# FCVT — Frontend / Backend Contract Changes

This document collects two contract notes about the JSON between the FCVT frontend and backend:

1. **VTF-297: `ioexbDetails` flag split** — backend changed the API contract; the frontend must adapt.
2. **Frontend payload contract bugs (VTF-265, Feb 2026)** — five issues in the frontend's `/api/config/validate` payload that block validation.

Originally maintained as separate notes (`split-ioexbdetails-flag.md`, `frontend-payload-issues.md`).

---

## 1. VTF-297 — Split `ioexbDetails` into `acoIoexbDetails` and `dtIoexbDetails`

### Problem

The `ParsedConfigFile` model had a single boolean flag `ioexbDetails` that was set to `true` when a parsed ADC file contained **any** of these config blocks: `CFG_AXCNT`, `CFG_SECTION_OUT`, `CFG_DATA_SAFETY_LEVEL`, or `CFG_DATA_OUT`.

This conflated two distinct hardware scenarios:

- **ACO IOEXB boards** produce `CFG_AXCNT` and `CFG_SECTION_OUT` blocks
- **DT IOEXB boards** produce `CFG_DATA_SAFETY_LEVEL` and `CFG_DATA_OUT` blocks

An AEB board can have only ACO boards, only DT boards, or both (on PWR-2+ backplanes). With the single flag, all three extractors (IOEXB Behaviour, IOEXB ACO, Data Transmission) ran on every file marked `ioexbDetails=true`, even when the relevant blocks were absent. Similarly, validation rules for `CFG_AXCNT`/`CFG_SECTION_OUT` would attempt to run on files that only had DT blocks.

### Solution

Replace the single `ioexbDetails` flag with two independent flags:

| Flag | Set when file contains | Consumers |
|---|---|---|
| `acoIoexbDetails` | `CFG_AXCNT` or `CFG_SECTION_OUT` | IOEXBBehaviourExtractorService, IOEXBAcoExtractorService, 14 validation rules |
| `dtIoexbDetails` | `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` | DataTransmissionExtractorService |

Both flags can be `true` simultaneously on the same file (mixed IOEXB board configuration).

### Files Changed

#### Model

**`ParsedConfigFile.java`** — Replaced `boolean ioexbDetails` with `boolean acoIoexbDetails` and `boolean dtIoexbDetails`. Lombok `@AllArgsConstructor` generates the constructor with the new field order: `(fileName, blocks, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails, id)`.

#### Parser

**`CfgParserUtil.java`** — Split the single block-detection `if` into two:
- `acoIoexbDetails = true` when `CFG_AXCNT` or `CFG_SECTION_OUT` is found
- `dtIoexbDetails = true` when `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` is found

Updated the `ParsedConfigFile` constructor call to pass both flags.

#### Validation Engine

**`ConfigFileMarker.java`** — Replaced enum value `IOEXBDETAILS` with `ACOIOEXBDETAILS` and `DTIOEXBDETAILS`. These are validated at startup when loading `ValidationConfiguration.json`.

**`FileApplicability.java`** — Replaced `IOEXBDETAILS` enum value (which called `file.isIoexbDetails()`) with:
- `ACOIOEXBDETAILS` → calls `file.isAcoIoexbDetails()`
- `DTIOEXBDETAILS` → calls `file.isDtIoexbDetails()`

**`RuleExecutionEngine.java`** — Updated the `isFileEligible()` switch statement to match on `"ACOIOEXBDETAILS"` and `"DTIOEXBDETAILS"` instead of `"IOEXBDETAILS"`.

#### Validation Rules

**`ValidationConfiguration.json`** — All 14 rules that had `"ValidateOnlyInFilesWith": "IOEXBDETAILS"` changed to `"ValidateOnlyInFilesWith": "ACOIOEXBDETAILS"`. These rules all target `CFG_AXCNT` or `CFG_SECTION_OUT` blocks. No DT-specific validation rules exist yet (phase 2).

#### Extractors

**`IOEXBBehaviourExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isAcoIoexbDetails()`. This extractor reads from `CFG_AXCNT` blocks.

**`IOEXBAcoExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isAcoIoexbDetails()`. This extractor reads from `CFG_SECTION_OUT` blocks.

**`DataTransmissionExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isDtIoexbDetails()`. This extractor reads from `CFG_DATA_SAFETY_LEVEL` and `CFG_DATA_OUT` blocks.

#### API Contract

**`openapi.yaml`** — `ParsedConfigFile` schema updated: `ioexbDetails` property replaced with `acoIoexbDetails` and `dtIoexbDetails`. All examples and descriptions updated. **This is a breaking change for the frontend.**

#### Test Step Definitions

**`ExtractorSteps.java`** — Step `"I have a parsed config file {string} with ioexbDetails={word}:"` replaced with two steps:
- `"I have a parsed config file {string} with acoIoexbDetails={word}:"`
- `"I have a parsed config file {string} with dtIoexbDetails={word}:"`

**`EngineSteps.java`** — Step `"the file has ioexbDetails marker set to {word}"` replaced with two steps:
- `"the file has acoIoexbDetails marker set to {word}"`
- `"the file has dtIoexbDetails marker set to {word}"`

**`DataHelper.java`** — `createParsedConfigFile` overload updated from 5 parameters `(fileName, id, trackSectionDetails, ioexbDetails, comDetails)` to 6 parameters `(fileName, id, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails)`.

#### Test Feature Files

**`file-eligibility.feature`** — `IOEXBDETAILS` scenarios renamed to `ACOIOEXBDETAILS`. All JSON test data updated: `"ioexbDetails": false` replaced with `"acoIoexbDetails": false, "dtIoexbDetails": false`. The `"ioexbDetails": true` scenario updated to `"acoIoexbDetails": true`.

**`engine-buisness-logic.feature`** — All references to `IOEXBDETAILS` changed to `ACOIOEXBDETAILS` (file type markers, rule config, scenario names).

**`ioexb-behaviour-extractor.feature`** — Step changed to `acoIoexbDetails=true`, JSON field updated.

**`ioexb-aco-extractor.feature`** — Step changed to `acoIoexbDetails=true`, JSON field updated.

**`data-transmission-extractor.feature`** — Step changed to `dtIoexbDetails=true`, JSON field updated.

**`default-rule-executor.feature`** — JSON test data updated with both new fields.

**`rule-execution-engine.feature`** — JSON test data updated with both new fields.

**`validation-decision-engine.feature`** — JSON test data updated with both new fields.

### Frontend Impact

The `/api/upload/adcfiles` response and `/api/config/validate` request body now use `acoIoexbDetails` and `dtIoexbDetails` instead of `ioexbDetails`. The frontend must be updated to:
1. Read the two new flags from the upload response
2. Send both flags back in the validation request body

---

## 2. Frontend Validation Payload — Issues for Backend Compatibility

**Date:** 2026-02-27
**Branch:** VTF-265
**Raised by:** Backend Team
**For:** Frontend Team

### Context

During integration testing of the `POST /api/config/validate` endpoint, several mismatches were found between the `userInput` payload sent by the frontend and the contract expected by the backend validation engine.

The backend performs **exact string matching** on all keys - there is no normalization, trimming, or index stripping. Every key in `userInput` must match the corresponding `ConfigEntryKey` defined in the backend's `ValidationConfiguration.json` and the entry keys present in the parsed ADC config files.

### Issue 1: CFG_PROJECT_AEB - Wrong Key Name and Type

**Severity:** Blocker (request rejected before validation runs)

| Field | Frontend Sends | Backend Expects |
|-------|---------------|-----------------|
| Key name | `BLOCK_EXIST` | `BLOCK_EXISTS` |
| Value type | `"0"` (string) | `false` (boolean) |

**Frontend payload (current):**
```json
"CFG_PROJECT_AEB": {
    "BLOCK_EXIST": "0",
    "PROJECT_NUMBER": "0"
}
```

**Expected payload:**
```json
"CFG_PROJECT_AEB": {
    "BLOCK_EXISTS": false,
    "PROJECT_NUMBER": "0"
}
```

**Root cause:** The backend `ProjectBlockCheckRule` hardcodes `BLOCK_EXISTS` (with S) and expects a Java `Boolean` type. The frontend sends the key without the S and as a string `"0"`.

### Issue 2: CFG_TIMEOUT - Indexed Keys Not Supported

**Severity:** Blocker (request rejected before validation runs)

The frontend appends numeric indices to `TIMEOUT_VALUE` to represent multiple timeout block occurrences. The backend expects a single key `TIMEOUT_VALUE` with an array of values.

| Frontend Sends | Backend Expects |
|---------------|-----------------|
| `TIMEOUT_VALUE0`: `"34"` | `TIMEOUT_VALUE`: `["34", "61"]` |
| `TIMEOUT_VALUE1`: `"61"` | |
| `TIMEOUT_VALUE2`: `""` | |

**Frontend payload (current):**
```json
"CFG_TIMEOUT": {
    "TIMEOUT_VALUE0": "34",
    "TIMEOUT_VALUE1": "61",
    "TIMEOUT_VALUE2": ""
}
```

**Expected payload:**
```json
"CFG_TIMEOUT": {
    "TIMEOUT_VALUE": ["34", "61"]
}
```

**Note:** Empty string values (like `TIMEOUT_VALUE2: ""`) should be excluded.

**Rule type:** `MultipleBlockMultipleInputMatch` - designed for blocks with multiple occurrences sharing the same entry key.

### Issue 3: CFG_SECTION_OUT - Indexed/Prefixed Keys Not Supported

**Severity:** Blocker (request rejected before validation runs)

The frontend appends numeric suffixes/prefixes to several keys in `CFG_SECTION_OUT`. The backend expects the raw entry key names as they appear in the ADC config files.

| Frontend Sends | Backend Expects | Issue |
|---------------|-----------------|-------|
| `CLR_OCC1` | `CLR_OCC` | Appended `1` suffix |
| `TYPE1_AUX1` | `TYPE_AUX1` | Inserted `1` prefix |
| `TYPE1_AUX2` | `TYPE_AUX2` | Inserted `1` prefix |
| `AUX1_OUT1` | `AUX1_OUT` | Appended `1` suffix |
| `AUX2_OUT1` | `AUX2_OUT` | Appended `1` suffix |
| `AUX1_NO_NC` | `AUX1_NO_NC` | Correct |
| `AUX2_NO_NC` | `AUX2_NO_NC` | Correct |

**Frontend payload (current):**
```json
"CFG_SECTION_OUT": {
    "CLR_OCC1": "0",
    "TYPE1_AUX1": "0",
    "TYPE1_AUX2": "0",
    "AUX1_OUT1": "3",
    "AUX1_NO_NC": "0",
    "AUX2_OUT1": "3",
    "AUX2_NO_NC": "0"
}
```

**Expected payload:**
```json
"CFG_SECTION_OUT": {
    "CLR_OCC": "0",
    "TYPE_AUX1": "0",
    "TYPE_AUX2": "0",
    "AUX1_OUT": "3",
    "AUX1_NO_NC": "0",
    "AUX2_OUT": "3",
    "AUX2_NO_NC": "0"
}
```

### Issue 4: CFG_AXCNT - Missing TYPE_IOEXB Key

**Severity:** Blocker (request rejected before validation runs)

The frontend does not include `TYPE_IOEXB` in the `CFG_AXCNT` section, but the backend requires it (`UIInputRequired: "Yes"`).

**Frontend payload (current):**
```json
"CFG_AXCNT": {
    "BEHAV_INPUT1": "6",
    "TYPE_IN1": "0",
    "BEHAV_INPUT2": "6",
    "TYPE_IN2": "0",
    "BEHAV_INPUT3": "6",
    "TYPE_IN3": "0",
    "BEHAV_IOEXB": "7"
}
```

**Expected payload:**
```json
"CFG_AXCNT": {
    "BEHAV_INPUT1": "6",
    "TYPE_IN1": "0",
    "BEHAV_INPUT2": "6",
    "TYPE_IN2": "0",
    "BEHAV_INPUT3": "6",
    "TYPE_IN3": "0",
    "BEHAV_IOEXB": "7",
    "TYPE_IOEXB": "0"
}
```

**Valid values (from value-mappings.properties):**
- `0` - standard
- `1` - extended

### Issue 5: Missing Sections (Deferred to Later Phase)

The following `userInput` sections are not sent by the frontend at all. These have been **removed from the backend validation rules for now** and will be added in a later phase of development once the frontend UI supports them.

| Block | Missing Keys | File-Type Marker |
|-------|-------------|------------------|
| `CFG_DATA_OUT` | `SOURCE_DP_ID`, `NMBR_OUT`, `POSITION` | IOEXBDETAILS |
| `CFG_DATA_SAFETY_LEVEL` | `SAFETY_LEVEL_IN`, `SAFETY_LEVEL_OUT`, `SAFE_OUT_FDBCK_QUAD` | IOEXBDETAILS |
| `CFG_MY_IP_NW1` | `MY_IP_NW1_B1`, `MY_IP_NW1_B2`, `MY_IP_NW1_B3`, `MY_IP_NW1_B4` | COMDETAILS |

**Action:** No frontend change needed now. Backend rules for these have been removed from `ValidationConfiguration.json`. They will be re-added when the frontend UI for these sections is ready.

### Summary Table

| # | Block | Issue | Severity | Action Required |
|---|-------|-------|----------|-----------------|
| 1 | CFG_PROJECT_AEB | Wrong key (`BLOCK_EXIST` -> `BLOCK_EXISTS`) and type (string -> boolean) | Blocker | Frontend fix |
| 2 | CFG_TIMEOUT | Indexed keys (`TIMEOUT_VALUE0/1/2` -> `TIMEOUT_VALUE` array) | Blocker | Frontend fix |
| 3 | CFG_SECTION_OUT | Indexed/prefixed keys (5 keys affected) | Blocker | Frontend fix |
| 4 | CFG_AXCNT | Missing `TYPE_IOEXB` key | Blocker | Frontend fix |
| 5 | CFG_DATA_OUT, CFG_DATA_SAFETY_LEVEL, CFG_MY_IP_NW1 | Sections not implemented in UI | Deferred | Backend rules removed for now |

### Reference: Complete Expected userInput Structure

Below is the complete `userInput` structure the backend expects. All keys must match exactly.

```json
{
    "ID": {
        "ID": { "min": 1, "max": 4095 }
    },
    "CFG_PROJECT_AEB": {
        "BLOCK_EXISTS": false,
        "PROJECT_NUMBER": "0"
    },
    "CFG_BEHAV_TGGL": {
        "BEHAV_RESET": "7",
        "BEHAV_SIMUL": "1"
    },
    "CFG_SECTION": {
        "COMM_FAIL": "0",
        "BEHAV_GE": "1",
        "CLR_TRACK": "0",
        "RESET_IN": "5",
        "RESET_OUT": "1"
    },
    "CFG_ZP": {
        "INTERVAL": "2",
        "SUPERVIS_COUNT": "2",
        "SUPERVIS_COUNT_LMT": "0",
        "SYSTEM_COUNT": "2",
        "PARTIAL_COUNT": "1"
    },
    "CFG_OCC": {
        "OCC_EXT": "26",
        "OCC_DELAY": "1"
    },
    "CFG_TIMEOUT": {
        "TIMEOUT_VALUE": ["34", "61"]
    },
    "CFG_AXCNT": {
        "BEHAV_INPUT1": "6",
        "TYPE_IN1": "0",
        "BEHAV_INPUT2": "6",
        "TYPE_IN2": "0",
        "BEHAV_INPUT3": "6",
        "TYPE_IN3": "0",
        "BEHAV_IOEXB": "7",
        "TYPE_IOEXB": "0"
    },
    "CFG_SECTION_OUT": {
        "CLR_OCC": "0",
        "TYPE_AUX1": "0",
        "TYPE_AUX2": "0",
        "AUX1_OUT": "3",
        "AUX1_NO_NC": "0",
        "AUX2_OUT": "3",
        "AUX2_NO_NC": "0"
    },
    "CFG_RESET": {
        "RESET_OP_TIME": "50",
        "RESET_LD_TIME": "1"
    },
    "CFG_TROLLEY_SUPP": {
        "TROLLEY_SUPP": "1"
    },
    "CFG_PARAM_TROLLEY_SUPP": {
        "SUPP_TIME": "60",
        "DIAMETER": "109",
        "AXLE_DISTANCE": "5",
        "SPEED_TOLERANCE": "12"
    },
    "CFG_RSR_TYPE": {
        "RSR_TYPE": "1"
    },
    "CFG_TYPE_PRTCT": {
        "TYPE_PRTCT_CODE": "0xb192f6cd"
    }
}
```
