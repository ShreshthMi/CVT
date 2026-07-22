# FCVT Phase 2 backend — end-to-end breakdown (VTF-331 → VTF-338)

One-stop summary of everything built on the Phase 2 backend line to date. Companion artifacts:
full diff [vtf-338.diff](vtf-338.diff) (59b5cb5 → b1b9edc: 43 commits, 118 files, +9,075/−36, test
suite 159 → 243 green), forward plan [vtf-359-series-alpha-fix-plan.md](vtf-359-series-alpha-fix-plan.md).

## What Phase 2 is

Phase 1 validated uploaded ADC configuration files against **expected values typed into the UI**.
Phase 2 replaces the typed input with an **upload-sourced baseline**: the customer's **PDQ workbook**
(planning questionnaire, frozen Ver14 layout) and the **FCT2 project file** (engineering-tool export)
become the source of truth. The backend derives every expectation from those two artifacts, validates
the ADCs against them, and answers with per-cell mismatch annotations and CAN-segment-aware verdicts —
while leaving the Phase 1 endpoint and its response byte-for-byte unchanged.

## The pipeline (as of VTF-338)

```
POST /api/upload/pdq          POST /api/upload/fct           POST /api/upload/adcfiles
  PDQ .xlsx ── parsers ──►      .fct2 ── zip caps ──►           .ADC files ── Phase 1
  PdqUploadResponse             project.xml ── ComAebMap        parser ──► ParsedConfigFile[]
  (header, CQ-IR, control       (CAN segments/chains, COMs,
   table, DT inputs)             AEBs, FMAs, ACO/DT IoExbs)
        └──────────────┬──────────────┘                                │
                       ▼                                               │
        POST /api/config/v2/validate  {parsedConfigFiles, userInput{pdqData, fctData}}
                       │  coupled-artifacts gate (both required → else PHASE2_INPUTS_INCOMPLETE)
                       ▼
        ExpectationsPreprocessor:  BaselineGate (control table present, PDQ↔FCT track
        reconciliation, RSR cross-check) → Expectations = scalar bucket + instanced bucket
        (7 builders: scalar/CQ-IR, counting-head, supervisor, ACO, control/CHC, IP-switch, forwarding)
                       ▼
        Consume:  scalar bucket → Phase 1 rule engine (lenient payload resolve, same dispatch)
                  instanced bucket → InstancedExpectationEvaluator (SINGLE / BY_IDENTITY
                  set-equality / POSITIONAL; extras → UNEXPECTED_OCCURRENCE)
                  CFG_FWRD_ACD → socket→COM resolution (ForwardingDestinationResolver)
                       ▼
        CanSegmentValidator (Cluster 1 named verdicts) → result ids → SummaryService
                       ▼
        MismatchAnnotator: verdict → detail-table cell join (_mismatches on 8 detail DTOs)
```

## Story-by-story

| Story | Branch | Delivered |
|---|---|---|
| BE-01 | VTF-331 | `POST /api/upload/pdq` (multipart limits, `PDQ_INVALID` error contract); PDQ header parse; **CQ-IR sheet** → block-grouped `cqIrParameters`; workbook/mapping `.properties` contracts with fail-fast loaders. |
| BE-02 | VTF-332 | **ConfigControlTable** sheet → `trackSections` (name, dpIn/dpOut, resetType, trackType, fadcAutoReset) + `dpTable` (rail position, eChc); **Data Transmission Inputs** sheet → safety-level + output sub-tables; assembled `PdqUploadResponse`; Cucumber coverage. |
| BE-03 | VTF-333 | `POST /api/upload/fct`: `.fct2` archive extraction under zip-security caps (ratio/entry/total), `project.xml` → **ComAebMap** — Bps grouped into CAN segments via `CanConnections`, redundancy collapse, per-AEB FMAs + ACO IoExbs (OutputFma cross-resolution) + DT-IoExb count; locked 2-code error contract (`FCT_TAMPERED` / `FCT_INCOMPLETE_BASELINE`, reason log-only). |
| — | VTF-350 + Ver14 | PDQ parser aligned to the **frozen Ver14 workbook**; IDENTIFICATION range → numeric `{min,max}`; `RSR_TYPE`→`CFG_RSR_TYPE` dual-sourcing; `BLOCK_EXISTS` from CQ-IR. |
| BE-04 | VTF-334 | `POST /api/config/v2/validate` + **coupled-artifacts gate** (`PHASE2_INPUTS_INCOMPLETE`); v2 request/response models; `ExpectationsPreprocessor` seam (stubbed) so the endpoint was shape-complete before derivation existed. |
| BE-05 | VTF-335 | The preprocessor behind the seam: **BaselineGate** (control-table presence; accumulate-then-report PDQ↔FCT track reconciliation; RSR cross-check) and the **two-bucket Expectations model** — scalar bucket (block→entry→value, Phase 1 engine reuse) + instanced bucket (`fileId`, block, `linkedId`/position, key → value; `MatchMode` SINGLE / BY_IDENTITY / POSITIONAL). Seven domain builders incl. the signed sensor-set CHC adjacency algorithm and CFG_FWRD_ACD virtual-forwarding derivation. Authoritative contract: `v2-expectations-contract.md`. |
| BE-06 | VTF-336 | The **consuming engine**: scalar bucket runs through the unchanged Phase 1 dispatch via lenient `PayloadValidator.resolve` (baseline-sourced values, no UI-input enforcement); `InstancedExpectationEvaluator` does per-entity occurrence selection on raw block entries — BY_IDENTITY is strict set-equality (missing member FAILs, stray occurrence → `UNEXPECTED_OCCURRENCE`), POSITIONAL catches re-sequenced configs; CFG_FWRD_ACD actuals resolved socket→COM across uploaded files. |
| BE-07 | VTF-337 | **MismatchAnnotator**: every failing verdict joined to its FE detail-table cell — `_mismatches` on 8 detail DTOs + response-scoped result `id`s (cell → log-entry navigation), serialized NON_NULL/NON_EMPTY so the Phase 1 response stays byte-identical. Instanced findings carry raw coordinates; scalar joins go through a per-block layout registry. |
| BE-08 | VTF-338 | **Cluster 1 (CAN segment)**: `CanSegmentValidator` refines findings into named verdicts — ORPHANED (uploaded AEB absent from FCT segments), FILE NOT FOUND (unknown referenced id), INVALID SCOPE / INVALID VALUE (SLCT_TIMEOUT, status `INVALID`) — carried as sentinels in expected/actual; plus CFG_DATA_OUT ID/SLCT_TIMEOUT expectations derived from the PDQ DT sub-tables (chain == CAN segment). |

## Design decisions that shaped it

1. **Two-bucket split** — reuse the Phase 1 engine verbatim for flat scalars; add a dedicated evaluator
   only for what that engine cannot express (per-entity occurrence selection). No fork, no rewrite.
2. **Phase 1 byte-compatibility** — every additive field is NON_NULL/NON_EMPTY; the Phase 1 path never
   sees Phase 2 machinery.
3. **Fail-fast admission control** — hard baseline defects are rejected up front by typed gates
   (coupled-artifacts, control table, track reconciliation, RSR) mapped to HTTP 400 with stable codes.
4. **Raw-entry validation** — the instanced evaluator reads raw `ID`/`SECTION` block entries, not the
   display-mapped (lossy, comment-deduped) extractor output.
5. **Locked external error vocabulary** — uploads answer with a small fixed code set; detailed reasons
   are log-only (a deliberate contract; revisit tracked as O5 in the VTF-359 plan).

## Where it stands (and what's next)

All 8 stories are pushed (`origin/VTF-331` … `origin/VTF-338`, linear chain, suite 243 green) and synced
to the external repo. A multi-agent code review of the full body plus an empirical alpha run of two real
customer bundles (Package W: Centralised + ABS topologies) confirmed the core machine — parsing, gating,
counting-head/ACO/CHC derivation, occurrence selection — works on real data, and produced a precise
defect/fix inventory: two P0 real-world blockers (redundant-COM vocabulary, junction-eChc handling),
one ~28 % spurious-FAIL noise complex (scoping + firmware-default absence tolerance), one coverage gap
(/02 channel CHC), and a hardening batch. That work is scoped as **VTF-359…375** — see
[vtf-359-series-alpha-fix-plan.md](vtf-359-series-alpha-fix-plan.md). Implementation has not started.
