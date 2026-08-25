# FCVT — Validation Test Results History

This document tracks the validation engine's test results across two snapshots, showing the impact of VTF-265 and surrounding fixes:

- **Pre-fix baseline (Feb 2026)** — 13 categorised issues, **38.8% pass rate** (418 / 1076 results).
- **Post-fix snapshot (VTF-265)** — **86.8% pass rate** (734 / 846 results); remaining failures triaged.

Originally maintained as separate notes (`validation-engine-issue-list.md`, `validation-results-analysis.md`).

---

## 1. Pre-fix Baseline — Issue List (Feb 2026)

**Config Block Validation | Preliminary Bug Hunt | February 2026**

**Overall: Pass 418 / Fail 658 (38.8% pass rate)**

### Summary Table

| Issue ID | Config Block                    | Rule Type   | Status  | Severity        | Root Cause Category              |
|----------|---------------------------------|-------------|---------|-----------------|----------------------------------|
| ISS-001  | CFG_AXCNT / CFG_SECTION_OUT     | InputMatch  | FAIL    | High            | All-file check on IOEXB-only block |
| ISS-002  | CFG_BEHAV_TGGL                  | InputMatch  | PASS    | None            | -                                |
| ISS-003  | CFG_OCC                         | InputMatch  | FAIL    | High            | Block not in default config files |
| ISS-004  | CFG_RESET                       | InputMatch  | FAIL    | High            | Block not in default config files |
| ISS-005  | CFG_TPF                         | InputMatch  | PASS    | None            | -                                |
| ISS-006  | CFG_PROJECT_AEB                 | InputMatch  | FAIL    | High            | Needs retest with correct input  |
| ISS-007  | CFG_RSR_TYPE                    | InputMatch  | PASS    | None            | -                                |
| ISS-008  | CFG_SECTION                     | InputMatch  | PARTIAL | Medium          | All-file check on partial block  |
| ISS-009  | CFG_TIMEOUT                     | InputMatch  | FAIL    | Critical        | Parser/lookup bug — block exists |
| ISS-010  | CFG_TROLLEY_SUPP                | InputMatch  | PASS    | Low (Observation) | Category misclassification     |
| ISS-011  | CFG_TYPE_PROTECT_CODE           | InputMatch  | PASS    | Low (Observation) | Category misclassification     |
| ISS-012  | CFG_ZP                          | InputMatch  | PARTIAL | Medium          | Params only in GS06/07+ configs  |
| ISS-013  | ID                              | RangeCheck  | PASS    | None            | -                                |

### Severity Breakdown

| Severity   | Count | Issue IDs                          |
|------------|-------|------------------------------------|
| Critical   | 1     | ISS-009                            |
| High       | 4     | ISS-001, ISS-003, ISS-004, ISS-006 |
| Medium     | 2     | ISS-008, ISS-012                   |
| Low        | 2     | ISS-010, ISS-011                   |
| None       | 4     | ISS-002, ISS-005, ISS-007, ISS-013 |

### Detailed Issue Analysis

#### ISS-001 — CFG_AXCNT / CFG_SECTION_OUT

| Field       | Value                                                                 |
|-------------|-----------------------------------------------------------------------|
| Status      | FAIL                                                                  |
| Severity    | High                                                                  |
| Rule Type   | InputMatch                                                            |
| Observation | Failing for files that don't have this config block. These checks should only run against AEB files with an attached IOEXB card. InputMatch is forcing validation against all files. Same issue applies to CFG_SECTION_OUT. |

**Sample Failures:**

| File Name     | Block Name | Entry        | Expected | Actual                        | Status |
|---------------|------------|--------------|----------|-------------------------------|--------|
| C0711_00.ADC  | CFG_AXCNT  | BEHAV_INPUT1 | 6        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_AXCNT  | BEHAV_INPUT1 | 6        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_AXCNT  | BEHAV_INPUT1 | 6        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0714_00.ADC  | CFG_AXCNT  | BEHAV_INPUT1 | 6        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** InputMatch rule does not filter by file type. It validates against all uploaded files regardless of whether the file contains the config block. Should be scoped to IOEXB-attached AEB files only.

#### ISS-002 — CFG_BEHAV_TGGL

| Field       | Value                                                      |
|-------------|-------------------------------------------------------------|
| Status      | PASS                                                        |
| Severity    | None                                                        |
| Rule Type   | InputMatch                                                  |
| Observation | Passing and working as intended. Block is present across all files. |

**Sample Results:**

| File Name     | Block Name      | Entry       | Expected | Actual | Status |
|---------------|-----------------|-------------|----------|--------|--------|
| C0711_00.ADC  | CFG_BEHAV_TGGL  | BEHAV_RESET | 7        | 7      | PASS   |
| C0712_00.ADC  | CFG_BEHAV_TGGL  | BEHAV_RESET | 7        | 7      | PASS   |
| C0713_00.ADC  | CFG_BEHAV_TGGL  | BEHAV_RESET | 7        | 7      | PASS   |

#### ISS-003 — CFG_OCC

| Field       | Value                                                           |
|-------------|-----------------------------------------------------------------|
| Status      | FAIL                                                            |
| Severity    | High                                                            |
| Rule Type   | InputMatch                                                      |
| Observation | All fail as usually this block is not found in default config files. |

**Sample Failures:**

| File Name     | Block Name | Entry     | Expected | Actual                        | Status |
|---------------|------------|-----------|----------|-------------------------------|--------|
| C0711_00.ADC  | CFG_OCC    | OCC_DELAY | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_OCC    | OCC_DELAY | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_OCC    | OCC_DELAY | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** CFG_OCC block does not exist in default configuration files. Rule should either be conditional or the test data should include files with this block.

#### ISS-004 — CFG_RESET

| Field       | Value                                                                    |
|-------------|--------------------------------------------------------------------------|
| Status      | FAIL                                                                     |
| Severity    | High                                                                     |
| Rule Type   | InputMatch                                                               |
| Observation | Same result as CFG_OCC. Block not present in default configuration files. |

**Sample Failures:**

| File Name     | Block Name | Entry         | Expected | Actual                        | Status |
|---------------|------------|---------------|----------|-------------------------------|--------|
| C0711_00.ADC  | CFG_RESET  | RESET_ID_TIME | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_RESET  | RESET_ID_TIME | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_RESET  | RESET_ID_TIME | 1        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** Same as ISS-003. CFG_RESET block absent from default config files.

#### ISS-005 — CFG_TPF

| Field       | Value                          |
|-------------|--------------------------------|
| Status      | PASS                           |
| Severity    | None                           |
| Rule Type   | InputMatch                     |
| Observation | Passing as intended.           |

**Sample Results:**

| File Name     | Block Name           | Entry          | Expected | Actual | Status |
|---------------|----------------------|----------------|----------|--------|--------|
| C0711_00.ADC  | CFG_PARAM_TROLLEY_SUPP | AXLE_DISTANCE | 5        | 5      | PASS   |
| C0712_00.ADC  | CFG_PARAM_TROLLEY_SUPP | AXLE_DISTANCE | 5        | 5      | PASS   |
| C0713_00.ADC  | CFG_PARAM_TROLLEY_SUPP | AXLE_DISTANCE | 5        | 5      | PASS   |

#### ISS-006 — CFG_PROJECT_AEB

| Field       | Value                                                              |
|-------------|--------------------------------------------------------------------|
| Status      | FAIL                                                               |
| Severity    | High                                                               |
| Rule Type   | InputMatch                                                         |
| Observation | Checks failing as of now. Maybe retest with appropriate input.     |

**Sample Failures:**

| File Name     | Block Name      | Entry       | Expected | Actual                        | Status |
|---------------|-----------------|-------------|----------|-------------------------------|--------|
| C0711_00.ADC  | CFG_PROJECT_AEB | BLOCK_EXIST | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_PROJECT_AEB | BLOCK_EXIST | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_PROJECT_AEB | BLOCK_EXIST | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** Needs retest with correct input data. Block may require specific project configuration to be present.

#### ISS-007 — CFG_RSR_TYPE

| Field       | Value                          |
|-------------|--------------------------------|
| Status      | PASS                           |
| Severity    | None                           |
| Rule Type   | InputMatch                     |
| Observation | Passing as intended.           |

**Sample Results:**

| File Name     | Block Name   | Entry    | Expected | Actual | Status |
|---------------|--------------|----------|----------|--------|--------|
| C0711_00.ADC  | CFG_RSR_TYPE | RSR_TYPE | 1        | 1      | PASS   |
| C0712_00.ADC  | CFG_RSR_TYPE | RSR_TYPE | 1        | 1      | PASS   |
| C0713_00.ADC  | CFG_RSR_TYPE | RSR_TYPE | 1        | 1      | PASS   |

#### ISS-008 — CFG_SECTION

| Field       | Value                                                                                                     |
|-------------|-----------------------------------------------------------------------------------------------------------|
| Status      | PARTIAL                                                                                                   |
| Severity    | Medium                                                                                                    |
| Rule Type   | InputMatch                                                                                                |
| Observation | Partial pass, partial fail. Passing as intended for files with this block. Failing for files where block does not exist (InputMatch forcing all-file check). |

**Sample Results (Mixed):**

| File Name     | Block Name  | Entry     | Expected | Actual                        | Status |
|---------------|-------------|-----------|----------|-------------------------------|--------|
| C0727_00.ADC  | CFG_SECTION | RESET_OUT | 2        | 2                             | PASS   |
| C0732_00.ADC  | CFG_SECTION | RESET_OUT | 2        | 2                             | PASS   |
| C0736_00.ADC  | CFG_SECTION | RESET_OUT | 2        | 2                             | PASS   |
| C0711_00.ADC  | CFG_SECTION | BEHAV_GE  | 0        | 1                             | FAIL   |
| C0712_00.ADC  | CFG_SECTION | BEHAV_GE  | 0        | 1                             | FAIL   |
| C0714_00.ADC  | CFG_SECTION | BEHAV_GE  | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** Same systemic issue as ISS-001. InputMatch forces validation against all files. Files without CFG_SECTION block return CONFIG_BLOCK_OR_PARAM_NOT_FOUND.

#### ISS-009 — CFG_TIMEOUT

| Field       | Value                                                                                           |
|-------------|-------------------------------------------------------------------------------------------------|
| Status      | FAIL                                                                                            |
| Severity    | **Critical**                                                                                    |
| Rule Type   | InputMatch                                                                                      |
| Observation | CONFIG BLOCK NOT found but doesn't make any sense as this block exists in all files. Potential parser/lookup bug. |

**Sample Failures:**

| File Name     | Block Name  | Entry         | Expected | Actual                        | Status |
|---------------|-------------|---------------|----------|-------------------------------|--------|
| C0711_00.ADC  | CFG_TIMEOUT | TIMEOUT_VALUED | 34      | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_TIMEOUT | TIMEOUT_VALUED | 34      | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_TIMEOUT | TIMEOUT_VALUED | 34      | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** Likely a parser or block name lookup bug. The block CFG_TIMEOUT is confirmed to exist in all files, yet the engine returns CONFIG_BLOCK_OR_PARAM_NOT_FOUND. This is the highest priority bug to investigate.

#### ISS-010 — CFG_TROLLEY_SUPP

| Field       | Value                                                                              |
|-------------|------------------------------------------------------------------------------------|
| Status      | PASS                                                                               |
| Severity    | Low (Observation)                                                                  |
| Rule Type   | InputMatch                                                                         |
| Observation | Checks are passing. However why is this not in the CFG_TPF check category?         |

**Sample Results:**

| File Name     | Block Name       | Entry        | Expected | Actual | Status |
|---------------|------------------|--------------|----------|--------|--------|
| C0711_00.ADC  | CFG_TROLLEY_SUPP | TROLLEY_SUPP | 1        | 1      | PASS   |
| C0712_00.ADC  | CFG_TROLLEY_SUPP | TROLLEY_SUPP | 1        | 1      | PASS   |
| C0713_00.ADC  | CFG_TROLLEY_SUPP | TROLLEY_SUPP | 1        | 1      | PASS   |

**Action Item:** Clarify with domain team whether CFG_TROLLEY_SUPP should be categorized under CFG_TPF.

#### ISS-011 — CFG_TYPE_PROTECT_CODE

| Field       | Value                                                                         |
|-------------|-------------------------------------------------------------------------------|
| Status      | PASS                                                                          |
| Severity    | Low (Observation)                                                             |
| Rule Type   | InputMatch                                                                    |
| Observation | Checks are passing. However this is also not in the CFG_TPF category.         |

**Sample Results:**

| File Name     | Block Name         | Entry           | Expected   | Actual     | Status |
|---------------|--------------------|-----------------|------------|------------|--------|
| C0711_00.ADC  | CFG_TYPE_PRTCT     | TYPE_PRTCT_CODE | 0xb192f6cd | 0xb192f6cd | PASS   |
| C0712_00.ADC  | CFG_TYPE_PRTCT     | TYPE_PRTCT_CODE | 0xb192f6cd | 0xb192f6cd | PASS   |
| C0713_00.ADC  | CFG_TYPE_PRTCT     | TYPE_PRTCT_CODE | 0xb192f6cd | 0xb192f6cd | PASS   |

**Action Item:** Same as ISS-010. Clarify category classification with domain team.

#### ISS-012 — CFG_ZP

| Field       | Value                                                                                  |
|-------------|----------------------------------------------------------------------------------------|
| Status      | PARTIAL                                                                                |
| Severity    | Medium                                                                                 |
| Rule Type   | InputMatch                                                                             |
| Observation | Failing partially for parameters found only in config above GS06/07.                   |

**Sample Results (Mixed):**

| File Name     | Block Name | Entry              | Expected | Actual                        | Status |
|---------------|------------|--------------------|----------|-------------------------------|--------|
| C0735_00.ADC  | CFG_ZP     | SYSTEM_COUNT       | 2        | 2                             | PASS   |
| C0736_00.ADC  | CFG_ZP     | SYSTEM_COUNT       | 2        | 2                             | PASS   |
| C0737_00.ADC  | CFG_ZP     | SYSTEM_COUNT       | 2        | 2                             | PASS   |
| C0711_00.ADC  | CFG_ZP     | SUPERVIS_COUNT_LMT | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0712_00.ADC  | CFG_ZP     | SUPERVIS_COUNT_LMT | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |
| C0713_00.ADC  | CFG_ZP     | SUPERVIS_COUNT_LMT | 0        | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | FAIL   |

**Root Cause:** SUPERVIS_COUNT_LMT parameter only exists in GS06/07 and above configurations. Older config files do not have this parameter. InputMatch should handle version-specific parameters.

#### ISS-013 — ID

| Field       | Value                                                                                              |
|-------------|----------------------------------------------------------------------------------------------------|
| Status      | PASS                                                                                               |
| Severity    | None                                                                                               |
| Rule Type   | RangeCheck                                                                                         |
| Observation | All passing as intended. RangeCheck is the rule type being used. Only check besides InputMatch.     |

**Sample Results:**

| File Name     | Block Name | Entry | Expected  | Actual | Status |
|---------------|------------|-------|-----------|--------|--------|
| C0711_00.ADC  | ID         | ID    | 1 - 4095  | 711    | PASS   |
| C0712_00.ADC  | ID         | ID    | 1 - 4095  | 712    | PASS   |
| C0713_00.ADC  | ID         | ID    | 1 - 4095  | 713    | PASS   |
| C0714_00.ADC  | ID         | ID    | 1 - 4095  | 714    | PASS   |
| C0715_00.ADC  | ID         | ID    | 1 - 4095  | 715    | PASS   |
| C0716_00.ADC  | ID         | ID    | 1 - 4095  | 716    | PASS   |

### Recurring Patterns

#### Pattern 1: InputMatch All-File Check (ISS-001, ISS-008, ISS-012)

The InputMatch rule validates against **all uploaded files** regardless of whether the config block exists in the file. This causes false FAILs for files that legitimately do not contain the block.

**Affected Issues:** ISS-001, ISS-008, ISS-012

**Proposed Fix:** Introduce a `SkipComFile` or file-type filter mechanism in the InputMatch rule so that validation is only performed against files that are expected to contain the block.

#### Pattern 2: Block Not Found in Default Configs (ISS-003, ISS-004)

CFG_OCC and CFG_RESET blocks are not present in default configuration files. All checks fail with CONFIG_BLOCK_OR_PARAM_NOT_FOUND.

**Affected Issues:** ISS-003, ISS-004

**Proposed Fix:** Either make these rules conditional (only run when block exists) or ensure test data includes files with these blocks.

#### Pattern 3: Parser/Lookup Bug (ISS-009)

CFG_TIMEOUT block exists in all files but the engine reports CONFIG_BLOCK_OR_PARAM_NOT_FOUND. This indicates a bug in the parser or block name resolution logic.

**Affected Issues:** ISS-009

**Proposed Fix:** Debug the parser/lookup flow for CFG_TIMEOUT. Check for block name casing, whitespace, or aliasing issues.

#### Pattern 4: Category Classification (ISS-010, ISS-011)

CFG_TROLLEY_SUPP and CFG_TYPE_PROTECT_CODE are passing but may belong under the CFG_TPF category rather than being standalone checks.

**Affected Issues:** ISS-010, ISS-011

**Proposed Fix:** Review with domain team and reclassify if needed. Functional — no code change required.

### Priority Matrix

| Priority | Issue IDs         | Action Required                                    |
|----------|-------------------|----------------------------------------------------|
| P0       | ISS-009           | Investigate parser/lookup bug for CFG_TIMEOUT       |
| P1       | ISS-001, ISS-008  | Fix InputMatch all-file check logic                 |
| P1       | ISS-003, ISS-004  | Handle missing blocks gracefully or fix test data   |
| P2       | ISS-006           | Retest with appropriate input data                  |
| P2       | ISS-012           | Handle version-specific parameters (GS06/07+)       |
| P3       | ISS-010, ISS-011  | Clarify category classification with domain team    |
| OK       | ISS-002, ISS-005, ISS-007, ISS-013 | No action required                  |

---

## 2. Post-fix Analysis — VTF-265

**Date:** 2026-02-27
**Branch:** VTF-265
**Test Data:** 29 ADC files from frontend payload (ValidatePayloadJSON.txt)

### Pass Rate Comparison

| Metric | Before (VTF-265) | After (VTF-265) | Improvement |
|--------|------------------|-----------------|-------------|
| Total Results | 1,076 | 846 | Reduced noise |
| PASS | 418 (38.8%) | 734 (86.8%) | +48.0% |
| FAIL | 658 (61.2%) | 112 (13.2%) | -48.0% |

### Results by Rule Type

| Rule Type | PASS | FAIL | Total |
|-----------|------|------|-------|
| DuplicateCheck | 29 | 0 | 29 |
| RangeCheck | 29 | 0 | 29 |
| ProjectBlockCheck | 28 | 0 | 28 |
| MultipleBlockMultipleInputMatch | 28 | 0 | 28 |
| InputMatch | 400 | 52 | 452 |
| InputMatchOrBlockNotFound | 220 | 60 | 280 |
| **Total** | **734** | **112** | **846** |

### Remaining Failures Breakdown

#### Category 1: Legitimate Value Mismatches (32 failures)

These are real validation findings where the user-provided value does not match the value in the ADC file.

| Block | Entry Key | Expected (userInput) | Actual (ADC file) | Files Affected | Count |
|-------|-----------|---------------------|--------------------|----------------|-------|
| CFG_SECTION | BEHAV_GE | 0 | 1 | 16 track section files | 16 |
| CFG_SECTION | RESET_OUT | 2 | 1 | 16 track section files | 16 |

**Interpretation:** The frontend is sending `BEHAV_GE=0` and `RESET_OUT=2` but the ADC files have `BEHAV_GE=1` and `RESET_OUT=1`. Either the frontend form has incorrect default values, or the ADC files were configured with different parameters. These are valid mismatches that the validation engine correctly catches.

#### Category 2: Block/Parameter Not Found Across All Files (56 failures)

These failures occur because the block or entry key does not exist in the ADC config files.

| Block | Entry Key | Rule Type | Actual | Files Affected | Count |
|-------|-----------|-----------|--------|----------------|-------|
| CFG_PROJECT_AEB | PROJECT_NUMBER | InputMatch | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | All 28 non-COM files | 28 |
| CFG_ZP | SUPERVIS_COUNT_LMT | InputMatchOrBlockNotFound | CONFIG_BLOCK_OR_PARAM_NOT_FOUND | All 28 non-COM files | 28 |

**CFG_PROJECT_AEB::PROJECT_NUMBER:**
- The `CFG_PROJECT_AEB` block does not exist in any of the ADC files
- The `ProjectBlockCheck` rule (for `BLOCK_EXISTS`) correctly PASSes because the user sent `BLOCK_EXISTS: false`
- However, `PROJECT_NUMBER` is being validated via `InputMatch` (default rule from frontend key), which FAILs because the block doesn't exist
- **Action:** This is expected behavior - `PROJECT_NUMBER` validation should only apply when `BLOCK_EXISTS` is `true`. The default rule path creates this false failure. Consider adding explicit handling.

**CFG_ZP::SUPERVIS_COUNT_LMT:**
- The `SUPERVIS_COUNT_LMT` entry does not exist in the CFG_ZP block of any ADC file
- The rule type is `InputMatchOrBlockNotFound`, which should PASS when block/entry is not found
- **Possible Issue:** The rule may be checking for the entry within an existing block (CFG_ZP exists, but SUPERVIS_COUNT_LMT entry doesn't). The `InputMatchOrBlockNotFound` rule may only handle missing blocks, not missing entries within existing blocks.

#### Category 3: Block/Parameter Not Found in Specific IOEXB Files (24 failures)

These failures occur only in specific IOEXB-marked files where certain entries are missing.

| Block | Entry Key | Rule Type | Files Affected | Count |
|-------|-----------|-----------|----------------|-------|
| CFG_AXCNT | TYPE_IN1 | InputMatch | C0726, C0727 (x2), C0739 | 4 |
| CFG_AXCNT | TYPE_IN2 | InputMatch | C0726, C0727 (x2), C0739 | 4 |
| CFG_AXCNT | TYPE_IN3 | InputMatch | C0726, C0727 (x2), C0739 | 4 |
| CFG_AXCNT | TYPE_IOEXB | InputMatch | C0726, C0727 (x2), C0739 | 4 |
| CFG_SECTION_OUT | TYPE_AUX1 | InputMatch | C0726, C0727 (x2), C0739 | 4 |
| CFG_SECTION_OUT | TYPE_AUX2 | InputMatch | C0726, C0727 (x2), C0739 | 4 |

**Interpretation:** These 4 IOEXB files have `CFG_AXCNT` and `CFG_SECTION_OUT` blocks but are missing the `TYPE_IN1/2/3`, `TYPE_IOEXB`, `TYPE_AUX1`, and `TYPE_AUX2` entries. This could indicate:
- Older firmware versions that don't include these entries
- Different IOEXB board configurations that don't use these parameters
- **Action:** Consider whether these entries are optional for certain IOEXB configurations. If so, the rule type should be changed or an optional handling mechanism should be added.

### Summary of Actionable Items

| # | Issue | Action | Owner |
|---|-------|--------|-------|
| 1 | CFG_SECTION::BEHAV_GE mismatch (0 vs 1) | Verify correct default value in frontend form | Frontend |
| 2 | CFG_SECTION::RESET_OUT mismatch (2 vs 1) | Verify correct default value in frontend form | Frontend |
| 3 | CFG_PROJECT_AEB::PROJECT_NUMBER always fails | Default rule validates even when block doesn't exist; needs conditional logic tied to BLOCK_EXISTS | Backend |
| 4 | CFG_ZP::SUPERVIS_COUNT_LMT always fails | Investigate if InputMatchOrBlockNotFound handles missing entries within existing blocks | Backend |
| 5 | IOEXB files missing TYPE_IN/TYPE_IOEXB/TYPE_AUX entries | Determine if entries are optional for certain IOEXB configs | Backend/Domain |

### Test Configuration

- **ValidationConfiguration.json:** 42 rules (10 rules for CFG_DATA_OUT, CFG_DATA_SAFETY_LEVEL, CFG_MY_IP_NW1 deferred to later phase)
- **Postman payload modifications:** Fixed BLOCK_EXISTS key/type, TIMEOUT_VALUE indexing, CFG_SECTION_OUT key names, added TYPE_IOEXB
- **Endpoint:** POST /api/config/validate
- **29 ADC files:** Mix of track section (16), IOEXB (4), COM (1), and base config (8) files


---

## 3. Coverage decision — Phase-1 `/api/config/validate` left untested (VTF-371, 2026-08-25)

`POST /api/config/validate` has **no automated test of any kind**. There is no
`ConfigValidationControllerTest`; `src/test/.../controller/` holds only the v2, FCT-upload and
PDQ-upload tests. There are no golden or snapshot files anywhere under `src/test`, and `.gitlab-ci.yml`
runs plain `gradle test` with no response-comparison job. Every result in §1 and §2 above was produced
by hand through Postman against this endpoint.

That gap has already cost something. The ACO comment-dedup defect fixed in VTF-371 — which silently
dropped 5 of 118 `CFG_SECTION_OUT` rows in a real package — survived because the one extractor scenario
covering it used a single block with a unique header comment, so it passed identically with and without
the dedup. Nothing at the endpoint level was watching.

**Decision: not adding that coverage.** Phase 2 is live, and `/api/config/v2/validate` is the endpoint
that matters going forward; investing in a regression harness for the Phase-1 path is not worth it at
this point in the migration.

**What that means in practice.** VTF-371 changed this endpoint's payload — `ioexb_aco_details` gains a
row per collided ACO card plus a `slot` column — and nothing will catch fallout automatically. The
first real Phase-1 run after VTF-371 merges should be eyeballed. If Phase 1 is ever put back under
active change rather than maintenance, this decision should be revisited first.

Coverage of the shared machinery underneath it is unaffected: the extractors, the rule engine and the
summary builder are all exercised by the unit and Cucumber suites, which VTF-371 extended — including
a scenario asserting one ACO row per block with distinct slots, the assertion whose absence let the
dedup persist.
