# Split `ioexbDetails` into `acoIoexbDetails` and `dtIoexbDetails`

## Problem

The `ParsedConfigFile` model had a single boolean flag `ioexbDetails` that was set to `true` when a parsed ADC file contained **any** of these config blocks: `CFG_AXCNT`, `CFG_SECTION_OUT`, `CFG_DATA_SAFETY_LEVEL`, or `CFG_DATA_OUT`.

This conflated two distinct hardware scenarios:

- **ACO IOEXB boards** produce `CFG_AXCNT` and `CFG_SECTION_OUT` blocks
- **DT IOEXB boards** produce `CFG_DATA_SAFETY_LEVEL` and `CFG_DATA_OUT` blocks

An AEB board can have only ACO boards, only DT boards, or both (on PWR-2+ backplanes). With the single flag, all three extractors (IOEXB Behaviour, IOEXB ACO, Data Transmission) ran on every file marked `ioexbDetails=true`, even when the relevant blocks were absent. Similarly, validation rules for `CFG_AXCNT`/`CFG_SECTION_OUT` would attempt to run on files that only had DT blocks.

## Solution

Replace the single `ioexbDetails` flag with two independent flags:

| Flag | Set when file contains | Consumers |
|---|---|---|
| `acoIoexbDetails` | `CFG_AXCNT` or `CFG_SECTION_OUT` | IOEXBBehaviourExtractorService, IOEXBAcoExtractorService, 14 validation rules |
| `dtIoexbDetails` | `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` | DataTransmissionExtractorService |

Both flags can be `true` simultaneously on the same file (mixed IOEXB board configuration).

## Files Changed

### Model

**`ParsedConfigFile.java`** — Replaced `boolean ioexbDetails` with `boolean acoIoexbDetails` and `boolean dtIoexbDetails`. Lombok `@AllArgsConstructor` generates the constructor with the new field order: `(fileName, blocks, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails, id)`.

### Parser

**`CfgParserUtil.java`** — Split the single block-detection `if` into two:
- `acoIoexbDetails = true` when `CFG_AXCNT` or `CFG_SECTION_OUT` is found
- `dtIoexbDetails = true` when `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` is found

Updated the `ParsedConfigFile` constructor call to pass both flags.

### Validation Engine

**`ConfigFileMarker.java`** — Replaced enum value `IOEXBDETAILS` with `ACOIOEXBDETAILS` and `DTIOEXBDETAILS`. These are validated at startup when loading `ValidationConfiguration.json`.

**`FileApplicability.java`** — Replaced `IOEXBDETAILS` enum value (which called `file.isIoexbDetails()`) with:
- `ACOIOEXBDETAILS` → calls `file.isAcoIoexbDetails()`
- `DTIOEXBDETAILS` → calls `file.isDtIoexbDetails()`

**`RuleExecutionEngine.java`** — Updated the `isFileEligible()` switch statement to match on `"ACOIOEXBDETAILS"` and `"DTIOEXBDETAILS"` instead of `"IOEXBDETAILS"`.

### Validation Rules

**`ValidationConfiguration.json`** — All 14 rules that had `"ValidateOnlyInFilesWith": "IOEXBDETAILS"` changed to `"ValidateOnlyInFilesWith": "ACOIOEXBDETAILS"`. These rules all target `CFG_AXCNT` or `CFG_SECTION_OUT` blocks. No DT-specific validation rules exist yet (phase 2).

### Extractors

**`IOEXBBehaviourExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isAcoIoexbDetails()`. This extractor reads from `CFG_AXCNT` blocks.

**`IOEXBAcoExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isAcoIoexbDetails()`. This extractor reads from `CFG_SECTION_OUT` blocks.

**`DataTransmissionExtractorService.java`** — Filter changed from `file.isIoexbDetails()` to `file.isDtIoexbDetails()`. This extractor reads from `CFG_DATA_SAFETY_LEVEL` and `CFG_DATA_OUT` blocks.

### API Contract

**`openapi.yaml`** — `ParsedConfigFile` schema updated: `ioexbDetails` property replaced with `acoIoexbDetails` and `dtIoexbDetails`. All examples and descriptions updated. **This is a breaking change for the frontend.**

### Test Step Definitions

**`ExtractorSteps.java`** — Step `"I have a parsed config file {string} with ioexbDetails={word}:"` replaced with two steps:
- `"I have a parsed config file {string} with acoIoexbDetails={word}:"`
- `"I have a parsed config file {string} with dtIoexbDetails={word}:"`

**`EngineSteps.java`** — Step `"the file has ioexbDetails marker set to {word}"` replaced with two steps:
- `"the file has acoIoexbDetails marker set to {word}"`
- `"the file has dtIoexbDetails marker set to {word}"`

**`DataHelper.java`** — `createParsedConfigFile` overload updated from 5 parameters `(fileName, id, trackSectionDetails, ioexbDetails, comDetails)` to 6 parameters `(fileName, id, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails)`.

### Test Feature Files

**`file-eligibility.feature`** — `IOEXBDETAILS` scenarios renamed to `ACOIOEXBDETAILS`. All JSON test data updated: `"ioexbDetails": false` replaced with `"acoIoexbDetails": false, "dtIoexbDetails": false`. The `"ioexbDetails": true` scenario updated to `"acoIoexbDetails": true`.

**`engine-buisness-logic.feature`** — All references to `IOEXBDETAILS` changed to `ACOIOEXBDETAILS` (file type markers, rule config, scenario names).

**`ioexb-behaviour-extractor.feature`** — Step changed to `acoIoexbDetails=true`, JSON field updated.

**`ioexb-aco-extractor.feature`** — Step changed to `acoIoexbDetails=true`, JSON field updated.

**`data-transmission-extractor.feature`** — Step changed to `dtIoexbDetails=true`, JSON field updated.

**`default-rule-executor.feature`** — JSON test data updated with both new fields.

**`rule-execution-engine.feature`** — JSON test data updated with both new fields.

**`validation-decision-engine.feature`** — JSON test data updated with both new fields.

## Frontend Impact

The `/api/upload/adcfiles` response and `/api/config/validate` request body now use `acoIoexbDetails` and `dtIoexbDetails` instead of `ioexbDetails`. The frontend must be updated to:
1. Read the two new flags from the upload response
2. Send both flags back in the validation request body
