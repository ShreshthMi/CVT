# Validation Results Analysis - VTF-265

**Date:** 2026-02-27
**Branch:** VTF-265
**Test Data:** 29 ADC files from frontend payload (ValidatePayloadJSON.txt)

---

## Pass Rate Comparison

| Metric | Before (VTF-265) | After (VTF-265) | Improvement |
|--------|------------------|-----------------|-------------|
| Total Results | 1,076 | 846 | Reduced noise |
| PASS | 418 (38.8%) | 734 (86.8%) | +48.0% |
| FAIL | 658 (61.2%) | 112 (13.2%) | -48.0% |

---

## Results by Rule Type

| Rule Type | PASS | FAIL | Total |
|-----------|------|------|-------|
| DuplicateCheck | 29 | 0 | 29 |
| RangeCheck | 29 | 0 | 29 |
| ProjectBlockCheck | 28 | 0 | 28 |
| MultipleBlockMultipleInputMatch | 28 | 0 | 28 |
| InputMatch | 400 | 52 | 452 |
| InputMatchOrBlockNotFound | 220 | 60 | 280 |
| **Total** | **734** | **112** | **846** |

---

## Remaining Failures Breakdown

### Category 1: Legitimate Value Mismatches (32 failures)

These are real validation findings where the user-provided value does not match the value in the ADC file.

| Block | Entry Key | Expected (userInput) | Actual (ADC file) | Files Affected | Count |
|-------|-----------|---------------------|--------------------|----------------|-------|
| CFG_SECTION | BEHAV_GE | 0 | 1 | 16 track section files | 16 |
| CFG_SECTION | RESET_OUT | 2 | 1 | 16 track section files | 16 |

**Interpretation:** The frontend is sending `BEHAV_GE=0` and `RESET_OUT=2` but the ADC files have `BEHAV_GE=1` and `RESET_OUT=1`. Either the frontend form has incorrect default values, or the ADC files were configured with different parameters. These are valid mismatches that the validation engine correctly catches.

---

### Category 2: Block/Parameter Not Found Across All Files (56 failures)

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

---

### Category 3: Block/Parameter Not Found in Specific IOEXB Files (24 failures)

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

---

## Summary of Actionable Items

| # | Issue | Action | Owner |
|---|-------|--------|-------|
| 1 | CFG_SECTION::BEHAV_GE mismatch (0 vs 1) | Verify correct default value in frontend form | Frontend |
| 2 | CFG_SECTION::RESET_OUT mismatch (2 vs 1) | Verify correct default value in frontend form | Frontend |
| 3 | CFG_PROJECT_AEB::PROJECT_NUMBER always fails | Default rule validates even when block doesn't exist; needs conditional logic tied to BLOCK_EXISTS | Backend |
| 4 | CFG_ZP::SUPERVIS_COUNT_LMT always fails | Investigate if InputMatchOrBlockNotFound handles missing entries within existing blocks | Backend |
| 5 | IOEXB files missing TYPE_IN/TYPE_IOEXB/TYPE_AUX entries | Determine if entries are optional for certain IOEXB configs | Backend/Domain |

---

## Test Configuration

- **ValidationConfiguration.json:** 42 rules (10 rules for CFG_DATA_OUT, CFG_DATA_SAFETY_LEVEL, CFG_MY_IP_NW1 deferred to later phase)
- **Postman payload modifications:** Fixed BLOCK_EXISTS key/type, TIMEOUT_VALUE indexing, CFG_SECTION_OUT key names, added TYPE_IOEXB
- **Endpoint:** POST /api/config/validate
- **29 ADC files:** Mix of track section (16), IOEXB (4), COM (1), and base config (8) files
