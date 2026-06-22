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

## 6. Open items still to resolve (per milestone)
1. **List-membership carrier** (blocks M5 CHC + FWRD_ACD): `MultipleBlockMultipleInputMatch`-style vs a dedicated `membershipSets` structure for set-valued instanced expectations.
2. **Full per-(block, entry) scalar-vs-instance enumeration** (sharpens M3/M5): sweep every validated ADC block to fix the scalar/instanced split.
3. **DT** (`CFG_DATA_*`): parked, AE input.
4. **`ApiErrorResponse` list shape** (M2): encode the track lists in `message` vs add a structured `details` field — decide when wiring `PHASE2_TRACK_RECONCILIATION_FAILED`.

## 7. Verification approach
Every milestone asserts the **emitted `Expectations`** for known FCT+PDQ fixtures (via `PdqFixtures`/`FctFixtures`). No end-to-end validate until BE-06. Keep the full suite green at each commit.
