# VTF-338 (BE-08) — Cluster 1: CAN Segment — scope & as-built

> **Status:** DONE & pushed (`origin/VTF-338`, off the BE-07 tip `35472d6`), full suite **243 green**.
> Cluster 1 Check-A named verdicts + the CFG_DATA_OUT ID/SLCT_TIMEOUT slice. Check B (virtual
> forwarding) was already realized by BE-06. Spec: `fcvt-phase2-design.md` §8.1; story: VTF-338.

## 1. Two decisions (user, 2026-07-01)

- **Named verdicts → sentinels in `expected`/`actual`** (not a new field, not a ValidationStatus enum
  change). Reuses the §6 marker pattern; `status` carries FAIL / INVALID.
- **CFG_DATA_OUT ID/SLCT_TIMEOUT is in BE-08**, not deferred to BE-14. (DT was parked for the
  source→receiver join; that join is derivable — see §3.)

## 2. Check A — named verdicts (`CanSegmentValidator`)

A post-pass over the instanced findings + uploaded ADCs (design §8.1). The physical/virtual **expected**
SLCT_TIMEOUT (0 same-segment / 1 different-segment) is *already* segment-based in the BE-05 builders
(`chainByDpId`, and **chain == CAN segment** per §5.5 / `ComAebMap`) — this pass only refines the
**verdict**, plus adds the two lookups:

| Verdict | Trigger | Carried as |
|---|---|---|
| **ORPHANED** | an uploaded non-COM (AEB) ADC whose `[IDENTIFICATION] ID` matches no AEB in the FCT segments (these files were silently skipped before) | new result, `actual = ORPHANED`, status FAIL |
| **FILE NOT FOUND** | an unexpected occurrence whose referenced AEB `ID` is absent from the baseline | refines `UNEXPECTED_OCCURRENCE` → `actual = FILE_NOT_FOUND` |
| **INVALID SCOPE** | a SLCT_TIMEOUT value mismatch with actual `2–7` (out of Phase 2's binary scope) | `actual = INVALID_SCOPE`, status **INVALID** |
| **INVALID VALUE** | a SLCT_TIMEOUT value mismatch otherwise (in-scope but wrong, or non-numeric) | `actual = INVALID_VALUE`, status **INVALID** |

Finding results are mutated in place; the detail-cell annotation (BE-07) still reads the finding's raw
actual, so the **cell keeps the value while the log entry carries the verdict** (`isAnnotatable` now
includes INVALID). This is the first real use of the `INVALID` status.

**Resolves reconciliation finding #1** (no named-verdict vocabulary) and **#5** (no ORPHANED / referenced-id
resolution). The "same/different-**chain** proxy, not segment membership" concern (#3) is a non-issue:
`ComAebMap` collapses each CAN segment to one chain, so chain identity *is* segment membership.

## 3. CFG_DATA_OUT ID/SLCT_TIMEOUT (`DataTransmissionExpectationsBuilder`)

Cluster 1 owns ID + SLCT_TIMEOUT on CFG_DATA_OUT; the DT payload proper (safety levels, `NMBR_OUT`,
`POSITION`) stays with Cluster 6 / BE-14.

- **Source→receiver join (was the reason DT was parked):** the PDQ Data Transmission sheet is a *single*
  table split into two index-aligned sub-tables (design §6.6). `dataSafetyLevels[i]` carries the
  **receiving** DP (`dpName`), `outputDataTransmission[i]` the **source** DP (`sourceDpName`) of the same
  row. So per paired row: `fileId = idOf(receivingDp)`, `linkedId = {ID: idOf(sourceDp)}`,
  `SLCT_TIMEOUT = chain(source) == chain(receiver) ? 0 : 1`, `matchMode = BY_IDENTITY`.
- **Assumption + guard:** the two sub-tables can only be paired by row index (the parsed DTOs carry no
  shared key). A length mismatch between them → `PHASE2_BASELINE_INCONSISTENT` (400); an unresolvable DP
  name → same. Verified against `fcvt-phase2-design.md` §6.6 and the sample (`UploadPDQResponse.json`).
  *If a future PDQ has asymmetric blank rows in the two column groups this pairing would need the parser to
  emit linked rows — flag if the guard ever fires spuriously.*
- Consumed by the existing BY_IDENTITY evaluator; annotated onto `data_transmission_details`
  (`SLCT_TIMEOUT` VALUE → `timeout` ×10; UNEXPECTED → `source_dp_name`). MISSING is results-only (the
  scalar-row DT table has no array slot to append to — as with CHC/ACO MISSING).

## 4. Results-only (no faithful cell — by design)
- FILE NOT FOUND / ORPHANED land on the `validation_results` log entry, not a cell tooltip (as agreed).
- FILE NOT FOUND is applied only to BY_IDENTITY blocks whose identity has an `ID` (CFG_ZP_FMA,
  CFG_SUPERVIS_FMA, CFG_CONTROL, CFG_DATA_OUT). ACO's referenced `aco_fmaId` (positional, no ID identity)
  and forwarding's `CAN_TX_ID` are not relabeled — documented gap.
- DT MISSING (an expected source absent) — results-only (no scalar-row slot).

## 5. Tests
- `CanSegmentValidatorTest` (5): INVALID SCOPE (2–7) / INVALID VALUE, FILE NOT FOUND (unknown ref) vs
  UNEXPECTED (known-but-unexpected ref), ORPHANED (AEB file absent from the FCT; COM files exempt).
- `DataTransmissionExpectationsBuilderTest` (4): same/different-segment SLCT_TIMEOUT, misaligned sub-tables
  → 400, unresolvable DP → 400, null/empty DT → nothing.
- `MismatchAnnotatorTest` gains the CFG_DATA_OUT `timeout` VALUE case.

## 6. Carried / not in this story
- Registry finalisation / **M4** spurious-FAIL — still parked (chip `task_d9e07982`).
- ACO / forwarding FILE-NOT-FOUND relabeling; DT MISSING as a cell (currently results-only).
- Remaining clusters: BE-09 (ACO rack position), BE-13 (dual-FMA RESET consistency), BE-14 (DT payload).
