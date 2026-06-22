# VTF-335 (BE-05) — Expectations Preprocessor: implementation scope

> **Status:** scoping locked 2026-06-23. Detailed semantics are authoritative in [`v2-expectations-contract.md`](v2-expectations-contract.md); this doc is the *implementation plan* only.
> **Branch:** `VTF-335`, off the `VTF-350` tip (`bb05f29`) in the single `FCVT-VTF334` worktree. One branch, phased commits (M1→M5). Merges after VTF-350.

## 1. What this story is
Replace `StubExpectationsPreprocessor` (returns empty) with the real `ExpectationsPreprocessor` that turns the **FCT + PDQ (+ optional tpf)** baseline under `ValidationInputV2` into `Expectations`. **Emission-only:** the engine that *consumes* expectations against parsed ADC files is **BE-06 (VTF-336)**. VTF-335 is therefore verified by asserting the **emitted** expectations against the committed FCT/PDQ fixtures — not by an end-to-end validate.

## 2. Scope boundary
**In:** carrier reshape, widen seam, input gate (§3) + error codes, scalar bucket (§4), registry edits (§4/§7.5), instanced bucket (§5).
**Out:** engine consumption of expectations → BE-06. RangeCheck numeric + both-absent guard → **done in VTF-350**.
**Parked:** DT blocks (`CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT`) — awaiting AE input.

## 3. Current seam (what changes)
- `Expectation(String fileName, String block, int instance, String entryKey, String expectedValue)` — old flat shape; reshape required.
- `Expectations(List<Expectation>)` — must carry **two buckets**.
- `ExpectationsPreprocessor.preprocess(ComAebMap, PdqUploadResponse)` — widen to `preprocess(ValidationInputV2)` (needs `tpfSections`).
- `ConfigValidationV2Service.validate(...)` calls the seam; `StubExpectationsPreprocessor` to be replaced.

## 4. Milestones (one reviewable commit each; M5 splits into 6 sub-commits)

| # | Deliverable | Key files | Verification |
|---|---|---|---|
| **M1** | **Carrier reshape + widen seam.** `Expectation` gains a scalar variant (`block/entry/value`) and an instanced variant (`fileID` numeric + composite `linkedID = (ID, SECTION)` + `block/key/value`); `Expectations` holds `scalarExpectations: Map<String,Map<String,Object>>` + `instancedExpectations: List<…>`. Stub + `ConfigValidationV2Service` updated to compile and stay green. | `Expectation`, `Expectations`, `ExpectationsPreprocessor`, `ConfigValidationV2Service`, `StubExpectationsPreprocessor` | compile; existing v2 controller/BDD tests stay green |
| **M2** | **Input gate (§3)** — track reconciliation (Control Table ↔ FCT track universe, NOT-FOUND + EXTRA), RSR_TYPE dual-source cross-check, build-time gate checks. New codes `PHASE2_CONTROL_TABLE_MISSING`, `PHASE2_TRACK_RECONCILIATION_FAILED` (carries `notFoundTracks[]`+`extraTracks[]`), `PHASE2_BASELINE_INCONSISTENT`; each a concrete `ConfigValidationException` subclass **+ `@ExceptionHandler`** (else falls through to 500). `PHASE2_INPUTS_INCOMPLETE` kept for missing-artifact only. | new exception classes, `GlobalExceptionHandler`, `ApiErrorResponse` (encode lists in `message` or add `details`), preprocessor gate | unit test per gate path (400 + message) |
| **M3** | **Scalar bucket (§4)** — emit `scalarExpectations` from `cqIrParameters` + scalar cross-rules (RSR, project AEB/COM, dual-FMA RESET_TYPE/DELAY, CFG_SWITCH, CFG_IP_SWITCH_TIME). Name alignment `IDENTIFICATION`→`ID`. | preprocessor scalar assembly | assert emitted scalar map vs fixture |
| **M4** | **Registry edits (§4/§7.5)** — add `CFG_SWITCH` {SWITCH_GE, SWITCH_GSF, PRERESET_ACT_TIME} + `CFG_IP_SWITCH_TIME` {IP_SWITCH_TIME} InputMatch; add `CFG_PROJECT_COM` ProjectBlockCheck (COMDETAILS — first consumer of that marker); add 4 dual-FMA InputMatch rules; **supersede** `CFG_AXCNT.BEHAV_INPUT3`. Config-only; takes effect when BE-06 consumes. | `ValidationConfiguration.json` (+ COMDETAILS consumer if needed) | startup config-validator + registry-count test |
| **M5** | **Instanced bucket (§5)** — six derivations, one sub-commit each: counting-head `CFG_ZP_FMA1/2` (DIR_INV, SLCT_TIMEOUT); supervisor `CFG_SUPERVIS_FMA1/2` (op==null ⇒ no block); ACO `CFG_SECTION_OUT` (positional + filler, aco_fmaId=ID); CHC `CFG_CONTROL` (signed sensor-set adjacency, middle/boundary, E-CHC, derived BEHAV_INPUT3); `CFG_IP_SWITCH` (per-COM, redundantComPresent); `CFG_FWRD_ACD` (Check-B forwarding, one-hop cross-COM SLCT_TIMEOUT=1 set). | preprocessor instanced assembly | per-family unit test vs fixture |

M1→M2→M3 form the spine; M5 is the bulk.

## 5. Locked decisions (from the scoping pass)
- **Story size:** one `VTF-335` branch, phased commits M1–M5 (single PR).
- **Registry edits:** land **in VTF-335** (M4), dormant until BE-06 consumes.
- **RangeCheck:** out — shipped in VTF-350.
- **`UIInputRequired` flip:** NOT done (see `v2-expectations-contract.md` §4 fix 2) — the `ID` rule stays `No`.

## 6. Resolved design decisions (2026-06-23)

**#1 — list-membership carrier → `matchMode` on flat rows (no separate bucket).** `instancedExpectations` stays one flat list; each `Expectation` carries a `matchMode` the BE-06 engine reads to select the ADC occurrence(s):

| `matchMode` | Blocks | Occurrence selection |
|---|---|---|
| `BY_FILE` | `CFG_AXCNT.BEHAV_INPUT3`, `CFG_IP_SWITCH.IP_SWITCH` | the one file picked by `fileID` |
| `BY_LINKED_ID` | `CFG_ZP_FMA*`, `CFG_SUPERVIS_FMA*` | occurrence whose identity == `linkedID` |
| `POSITIONAL` | `CFG_SECTION_OUT` (ACO) | i-th occurrence (card/slot order) |
| `SET_MEMBERSHIP` | `CFG_CONTROL`, `CFG_FWRD_ACD` | all occurrences vs the expected set (set-equality) |

The existing `MultipleBlockMultipleInputMatchRule` only checks `actual ⊆ expected`; the `SET_MEMBERSHIP` **set-equality** comparison (missing expected → fail, extra actual → flag) is a new BE-06 rule. The carrier just emits N rows under a shared `(fileID, block)` tagged `SET_MEMBERSHIP`.

**#3 — track-reconciliation error → verbose `message` (`ApiErrorResponse` stays flat).** `PHASE2_TRACK_RECONCILIATION_FAILED` must **name the offending tracks** in the message (which are NOT-FOUND in the FCT, which are EXTRA), accumulating all within the gate per §3.1. **Direction — no fail-fast:** a *later* story will add an **error array** to `ApiErrorResponse` listing every error found during preprocessing; until then VTF-335 keeps the flat shape (one verbose message per failing gate) and cross-gate accumulation lands with that later change.

**#2 — scalar/instanced split → enumerated in §6.1 (resolved).** Completeness (no unlisted entries in the instanced blocks) to be confirmed against real ADC block dumps during M5.

**DT** (`CFG_DATA_*`) — still **PARKED**, AE input.

### 6.1 Per-(block, entry) scalar vs instanced

**SCALAR** (expected from cqIR / tpf / project; every-occurrence `allMatch`):

| Block | Entries |
|---|---|
| `ID` | `ID` (RangeCheck ← IDENTIFICATION) |
| `CFG_BEHAV_TGGL` | `BEHAV_RESET`, `BEHAV_SIMUL` |
| `CFG_SECTION` | `COMM_FAIL`, `BEHAV_GE`, `CLR_TRACK`, `RESET_IN`, `RESET_OUT` |
| `CFG_AXCNT` | `BEHAV_INPUT1`, `BEHAV_INPUT2`, `TYPE_IN1/2/3`, `BEHAV_IOEXB` |
| `CFG_SECTION_OUT` | `CLR_OCC`, `TYPE_AUX1/2`, `AUX1_OUT`, `AUX2_OUT`, `AUX1_NO_NC`, `AUX2_NO_NC` (aux) |
| `CFG_OCC` | `OCC_DELAY`, `OCC_EXT` |
| `CFG_RESET` | `RESET_LD_TIME`, `RESET_OP_TIME` |
| `CFG_ZP` | `INTERVAL`, `SUPERVIS_COUNT`, `SYSTEM_COUNT`, `PARTIAL_COUNT`, `SUPERVIS_COUNT_LMT` |
| `CFG_TIMEOUT` | `TIMEOUT_VALUE` (array) |
| `CFG_PROJECT_AEB` / `CFG_PROJECT_COM` | `BLOCK_EXISTS` + `PROJECT_NUMBER` |
| `CFG_SUPERVIS_FMA1/2` | `RESET_TYPE`, `RESET_DELAY` (scalar half) |
| `CFG_SWITCH` | `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME` |
| `CFG_IP_SWITCH_TIME` | `IP_SWITCH_TIME` |
| `CFG_RSR_TYPE` | `RSR_TYPE` (also the §3.2 gate cross-check) |
| `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_TYPE_PRTCT` | tpf fields |

**INSTANCED** (per-entity from FCT / Control Table):

| Block | Entries | Identity | `matchMode` |
|---|---|---|---|
| `CFG_ZP_FMA1/2` | `DIR_INV`, `SLCT_TIMEOUT` | `linkedID` = head DP | BY_LINKED_ID |
| `CFG_SUPERVIS_FMA1/2` | `LOGIC_TYPE`, `SLCT_TIMEOUT` | `linkedID` = (ID, SECTION) | BY_LINKED_ID |
| `CFG_SECTION_OUT` | `ID` (=aco_fmaId), `SECTION`, `SLCT_TIMEOUT` | positional | POSITIONAL |
| `CFG_AXCNT` | `BEHAV_INPUT3` (derived 6/7) | `fileID` = DP | BY_FILE *(supersedes registry rule)* |
| `CFG_IP_SWITCH` | `IP_SWITCH` | `fileID` = COM | BY_FILE |
| `CFG_CONTROL` | `SLCT_TIMEOUT` | `linkedID` = (ID, SECTION) ×2 | SET_MEMBERSHIP |
| `CFG_FWRD_ACD` | `CAN_TX_ID`, `INT_ID_DEST` | per (source DP, dest COM) | SET_MEMBERSHIP |

**Structural (no expectation):** `ID.ID` DuplicateCheck — uniqueness, no baseline value (currently inert; confirm separately). **Parked:** `CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT` (DT).

## 7. Verification approach
Every milestone asserts the **emitted `Expectations`** for known FCT+PDQ fixtures (via `PdqFixtures`/`FctFixtures`). No end-to-end validate until BE-06. Keep the full suite green at each commit.
