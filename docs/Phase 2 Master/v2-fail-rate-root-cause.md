# v2 Validation — Accepted-Run FAIL-Rate Root Cause

**Date:** 2026-07-01
**Backend:** VTF-338 (BE-08)
**Input under analysis:** an *accepted* `POST /api/config/v2/validate` response (271 KB), produced from a real project ADC set (`P0513_SR` / Arakkonam Jn, `COM-AdC(HUT1)`), with the PDQ + FCT baseline that **passed** the track-reconciliation gate.
**Observed:** `validation_results` = 571 scalar rows over 19 files, **273 PASS / 298 FAIL (~52%)**.

## TL;DR

The high FAIL rate is **not** a pipeline bug, **not** a parser bug, and **not** mismatched PDQ/FCT artifacts. It is an expectation **calibration/semantics gap**: the PDQ CQ-IR baseline specifies values for many *optional* parameter blocks that these ADCs deliberately leave **commented out** (running on firmware defaults), and the engine treats "absent optional block" inconsistently.

## 1. The pipeline executed correctly

The accepted run produced a fully-formed result: 571 scalar checks + all 8 detail sections (dp / track_section / chc / supervisor / ioexb_behaviour / ioexb_aco / data_transmission / ethernet) + instanced verdicts (`EXPECTED_OCCURRENCE_NOT_FOUND`×46, `UNEXPECTED_OCCURRENCE`×13) + Cluster-1 named verdicts (`ORPHANED`×10, `FILE_NOT_FOUND`×1) + 20/20 BE-07 `_mismatches` cell-annotations correctly joined to their `result_id` rows with zero sentinel leakage into display cells. Every v2 mechanism (BE-04 gate → BE-05 preprocessor → BE-06 engine → BE-07 annotator → BE-08 Cluster 1) fired as designed.

## 2. Root cause of the 298 FAILs (proven from the raw ADC)

- **246 / 298 FAILs are "not found" sentinels** (`CONFIG_BLOCK_OR_PARAM_NOT_FOUND` 163, `EXPECTED_OCCURRENCE_NOT_FOUND` 46, `CONFIG_BLOCK_NOT_FOUND` 18, `PROJECT_BLOCK_NOT_FOUND` 18). ~11 signatures fail **uniformly across all 18 AEB ADCs**.
- Direct inspection of `C0001_00.ADC` explains every one of them:

  | Block | State in ADC | JSON verdict |
  |---|---|---|
  | `CFG_OCC`, `CFG_SWITCH`, `CFG_RESET` | **commented out** (`// CFG_OCC …`) → parser skips | block-not-found |
  | `CFG_SUPERVIS_FMA1` | **active ×2** (`RESET_DELAY 8:3` each) | resolves → `"3,3"` |
  | `CFG_SECTION`, `CFG_TIMEOUT` | active | resolves (PASS, real value) |
  | `CFG_IP_SWITCH_TIME`, `CFG_PROJECT_AEB/COM` | **truly absent** | not-found |

- Therefore the not-found verdicts are **correct given the parsed config**. Note also that **many of the 273 PASSes are tolerated-absence passes** (`actualValue = CONFIG_BLOCK_NOT_FOUND`, PASS only because the PDQ value equals the rule's firmware default), not real reads.

## 3. Three actionable issues (ranked)

### (1) Inconsistent absence-tolerance — biggest lever (~90 FAILs) — = the deferred M4-registry gap
When an optional block is commented-out / absent, the engine handles it two different ways:
- **Registry** blocks (`CFG_OCC`, `CFG_RESET`) run through `InputMatchOrBlockNotFoundRule`, which compares the PDQ value against the firmware default → sensible PASS/FAIL. Example: `CFG_OCC/OCC_DELAY` PASSes (PDQ 0 == default 0) while `CFG_OCC/OCC_EXT` FAILs (PDQ 0 ≠ default 26) — same absent block, verdict split purely by the configured no-block default. This is *by design*, not a contradiction.
- **Registry-less** blocks (`CFG_SWITCH`, `CFG_IP_SWITCH_TIME` — absent from the 41-rule `ValidationConfiguration.json`) fall to a synthesized `INPUT_MATCH` default rule with **no absence-tolerance** (`InputMatchRule` emits `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` FAIL on empty actuals). ~90 uniform FAILs.

**Fix:** give registry-less optional blocks the same default-aware handling (i.e. add registry rules / a default value), so a commented-out block is compared to its firmware default rather than hard-failing. Ties directly to the deferred **M4 registry** work.

### (2) Supervisor `RESET_DELAY` / `RESET_TYPE` scalar-vs-instanced modeling bug (confirmed)
`CFG_SUPERVIS_FMA*` is a per-supervisor-instance, multi-occurrence block, but `RESET_DELAY`/`RESET_TYPE` are routed through the flat scalar CQ-IR bucket (`SupervisorExpectationsBuilder` javadoc). The scalar rule joins occurrences (`InputMatchRule` `String.join(",", actuals)`) → `"3,3"` / `"0,0"` and compares against a single expected `"10"` / `"3"` → **un-passable by construction**, even when each instance is individually correct.

**Fix:** move `RESET_DELAY`/`RESET_TYPE` into the instanced bucket keyed by supervisor identity, exactly as `LOGIC_TYPE` / `SLCT_TIMEOUT` already are.

### (3) Project-block over-broadcast (scope)
`CFG_PROJECT_AEB` / `CFG_PROJECT_COM` are project-level, but the scalar expectations are broadcast to **every** per-DP AEB ADC via the `(block, entry)` union in `ConfigValidationService.buildValidationUniverse()` → `PROJECT_BLOCK_NOT_FOUND` ×18. They should be validated against a single project-level artifact, not each DP config. `CFG_IP_SWITCH_TIME` is similar.

## 4. Genuine value findings (small, legitimate residue)

Only ~29 FAILs are real expected-vs-actual value diffs: `BEHAV_SIMUL` 0-vs-1 (all 18), `OCC_EXT` 0-vs-26, `DIR_INV`, `BEHAV_INPUT3` 7-vs-6. Worth a domain review — some may themselves reflect baseline defaults rather than config errors.

## 5. Recommended sequence

1. **(1)** absence-tolerance for registry-less optional blocks (M4 registry) — collapses the bulk of the FAILs.
2. **(2)** supervisor `RESET_*` → instanced bucket.
3. **(3)** project-block scope fix.

After these, the FAIL set should shrink from ~298 to the handful of genuine value discrepancies, which can then be adjudicated against design intent.

## Engine anchors (VTF-338)

- `ScalarExpectationsBuilder` — scalar bucket = verbatim copy of PDQ `cqIrParameters`.
- `ConfigValidationService.buildValidationUniverse()` — validation universe = 41-rule registry keys **UNION** every `(block, entry)` in the PDQ expectations (the broadcast).
- `InputMatchRule` — no absence-tolerance; comma-joins multi-occurrence actuals.
- `InputMatchOrBlockNotFoundRule` — default-aware tolerance for absent blocks.
- `ValidationConfiguration.json` — 41 rules; lacks `CFG_SWITCH`, `CFG_IP_SWITCH_TIME`, `CFG_SUPERVIS_FMA*`, `CFG_PROJECT_COM`.
- `SupervisorExpectationsBuilder` — routes `RESET_TYPE`/`RESET_DELAY` through the scalar bucket (source of issue 2).
