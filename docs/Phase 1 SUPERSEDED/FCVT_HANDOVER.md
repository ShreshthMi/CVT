# FCVT — Codebase Handover Document

Frauscher Configuration Validator Tool — architectural reference for AI-assisted development support.

---

## 1. Repository Overview

### Directory Tree

```
configuration-validation-service/
├── src/
│   ├── main/
│   │   ├── java/com/frauscher/ConfigurationValidationService/
│   │   │   ├── ConfigurationValidationServiceApplication.java  — Spring Boot entry point
│   │   │   ├── constants/                — Shared string constants
│   │   │   ├── controller/               — REST endpoints (5 classes)
│   │   │   ├── dto/                      — API request/response wrappers (4 classes)
│   │   │   ├── exception/                — Exception hierarchy + global handler (7 classes)
│   │   │   ├── model/                    — Domain entities and DTOs (17 classes)
│   │   │   ├── service/                  — Business logic (4 services)
│   │   │   │   └── extractors/           — Detail extraction services (9 classes)
│   │   │   ├── startup/                  — Boot-time validation loading (2 classes)
│   │   │   ├── util/                     — Parsing, extraction, XML transform (4 classes)
│   │   │   │   └── excel/                — Excel report generation (6 classes)
│   │   │   └── validation/               — Validation engine core
│   │   │       ├── config/               — Jackson config, rule config validator (3 classes)
│   │   │       ├── context/              — Execution contexts and registries (5 classes)
│   │   │       ├── engine/               — Rule execution orchestrator (2 classes)
│   │   │       ├── payload/              — User input resolution (3 classes)
│   │   │       ├── rules/                — Rule implementations (8 classes)
│   │   │       └── spec/                 — Decision engine and file applicability (4 classes)
│   │   └── resources/
│   │       ├── application.properties          — Server, logging, CORS, upload config
│   │       ├── openapi.yaml                    — OpenAPI 3 specification
│   │       ├── ValidationConfiguration.json    — All validation rule definitions
│   │       └── value-mappings.properties       — UI dropdown value labels
│   └── test/
│       ├── java/.../cucumber/
│       │   ├── CucumberSpringConfiguration.java
│       │   ├── CucumberSpringTestRunner.java
│       │   └── stepdefs/                 — Step definitions (4 classes)
│       │       └── common/               — Test helpers (3 classes)
│       └── resources/cucumber/features/  — BDD feature files (24 files)
│           ├── engine/                   — Engine decision and execution tests
│           ├── extractors/               — Extractor service tests
│           └── rules/                    — Rule type tests
├── docs/                                 — Project documentation
├── build.gradle                          — Gradle build definition
├── Dockerfile                            — Container build
├── .gitlab-ci.yml                        — CI/CD pipeline
└── gradle/wrapper/                       — Gradle 8.14 wrapper
```

**Total: 91 main Java source files, 10 test Java files, 24 feature files.**

### Technology Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.2 |
| Spring Dependency Management | 1.1.7 |
| Jackson (databind, XML, JSR310) | 2.15.4 |
| Apache POI (Excel) | 5.2.5 |
| SpringDoc OpenAPI | 2.8.6 |
| Lombok | (managed by Spring BOM) |
| Cucumber (java, spring, junit-platform-engine) | 7.15.0 |
| JUnit Platform Suite | 1.10.2 |
| Gradle | 8.14 |

### Build & Run

- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Run**: `./gradlew bootRun` (port 7443)
- **Docker**: `docker build -t fcvt .` then run with port mapping to 7443

### Entry Point

`ConfigurationValidationServiceApplication` — standard `@SpringBootApplication` with `SpringApplication.run()`.

---

## 2. Architecture Diagram

```
                          ┌─────────────────────────────┐
                          │        Frontend (SPA)        │
                          │   at-cvt01.frauscher.host    │
                          └──────┬──────────┬────────────┘
                                 │          │
                    Upload .ADC  │          │  Validate / Report
                    (multipart)  │          │  (JSON)
                                 ▼          ▼
                     ┌───────────────────────────────┐
                     │       nginx reverse proxy      │
                     │       port 443 → 7443          │
                     └───────────────┬───────────────┘
                                     │
                     ┌───────────────▼───────────────┐
                     │     Spring Boot Application    │
                     │         port 7443              │
                     │                                │
                     │  ┌──────────┐  ┌────────────┐  │
                     │  │ Upload   │  │ Validation │  │
                     │  │Controller│  │ Controller │  │
                     │  └────┬─────┘  └─────┬──────┘  │
                     │       │              │         │
                     │       ▼              ▼         │
                     │  ┌──────────┐  ┌────────────┐  │
                     │  │ CfgParser│  │ Validation │  │
                     │  │  Util    │  │  Service   │  │
                     │  └──────────┘  └─────┬──────┘  │
                     │                      │         │
                     │         ┌────────────┼────┐    │
                     │         ▼            ▼    ▼    │
                     │  ┌──────────┐ ┌─────────┐ ┌─┐  │
                     │  │ Payload  │ │  Rule   │ │S│  │
                     │  │Validator │ │Execution│ │u│  │
                     │  └──────────┘ │ Engine  │ │m│  │
                     │               └────┬────┘ │m│  │
                     │                    │      │a│  │
                     │            ┌───────┼───┐  │r│  │
                     │            ▼       ▼   ▼  │y│  │
                     │  ┌───────────────────────┐│ │  │
                     │  │   8 Rule Impls        ││S│  │
                     │  │  (Strategy Pattern)   ││e│  │
                     │  └───────────────────────┘│r│  │
                     │                           │v│  │
                     │  ┌───────────────────────┐│i│  │
                     │  │  9 Extractor Services ││c│  │
                     │  │  (Detail Extraction)  ││e│  │
                     │  └───────────────────────┘└─┘  │
                     │                                │
                     │  ┌───────────────────────────┐  │
                     │  │  Excel Report Generator   │  │
                     │  │  (Apache POI)             │  │
                     │  └───────────────────────────┘  │
                     └────────────────────────────────┘
```

### Data Flow

1. **Upload**: Frontend sends .ADC files as multipart → `CfgParserUtil` parses line-by-line into `ParsedConfigFile` objects with classification flags → returns JSON array to frontend
2. **Validate**: Frontend sends parsed files + user input criteria as JSON → payload validation → rule execution engine iterates files × rules → summary service extracts hardware details → returns `ValidationSummary`
3. **Report**: Frontend sends `ValidationSummary` as JSON → `ExcelSummaryUtil` generates multi-sheet .xlsx → returns byte array

**No shared state between requests.** Parsed files are round-tripped through the frontend (stateless architecture).

---

## 3. Component Breakdown

### Controllers

| Class | Path | Purpose |
|---|---|---|
| `HomeController` | `controller/` | Redirects `/` to Swagger UI |
| `UploadController` | `controller/` | Accepts multipart .ADC uploads, returns parsed files; XML→JSON translation |
| `ConfigValidationController` | `controller/` | Accepts parsed files + user input, returns validation summary |
| `ConfigOptionsController` | `controller/` | Returns UI dropdown option mappings |
| `ReportController` | `controller/` | Accepts validation summary, returns Excel download |

### Services

| Class | Path | Purpose |
|---|---|---|
| `ConfigParsingService` | `service/` | Filters uploaded files by extension, delegates to `CfgParserUtil`, sorts by filename |
| `ConfigValidationService` | `service/` | Orchestrates full validation pipeline: payload validation → rule execution → summary |
| `ConfigOptionsService` | `service/` | Loads `value-mappings.properties`, builds `ConfigOptions` list (immutable, final field) |
| `SummaryService` | `service/` | Stateless — accepts results list, calls all 9 extractors, assembles `ValidationSummary` |

### Extractor Services

Each extractor filters files by a classification flag, then extracts domain-specific details from relevant config blocks.

| Class | Flag Filter | Reads From |
|---|---|---|
| `DpDetailExtractorService` | none (all non-COM files) | ID, CFG_SECTION, CFG_BEHAV_TGGL, CFG_TIMEOUT |
| `TrackSectionExtractorService` | `trackSectionDetails` | CFG_ZP_FMA1, CFG_ZP_FMA2 |
| `CHCExtractorService` | `!comDetails` | CFG_CONTROL, CFG_ZP |
| `SupervisorExtractorService` | `!comDetails` | CFG_SUPERVIS_FMA1/FMA2, CFG_AUTORESET_FMA1/FMA2 |
| `IOEXBBehaviourExtractorService` | `acoIoexbDetails` | CFG_AXCNT, CFG_COOP_RESET |
| `IOEXBAcoExtractorService` | `acoIoexbDetails` | CFG_SECTION_OUT |
| `DataTransmissionExtractorService` | `dtIoexbDetails` | CFG_DATA_SAFETY_LEVEL, CFG_DATA_OUT |
| `EthernetDetailExtractorService` | `comDetails` | CFG_MY_IP_NW1/NW2, CFG_MY_MASK, CFG_INT_ID_DEST_NW1/NW2, CFG_FWRD_ACD |
| `ValueMappingService` | n/a | value-mappings.properties — provides `mapValue(fieldKey, rawValue)` lookups |

### Validation Engine

| Class | Path | Purpose |
|---|---|---|
| `RuleExecutionEngine` | `validation/engine/` | Main orchestrator; maintains `EnumMap<RuleType, ValidationRule>`; decides and dispatches |
| `DefaultRuleExecutor` | `validation/engine/` | Handles unconfigured keys: creates default InputMatch rule, executes via engine |
| `ValidationDecisionEngine` | `validation/spec/` | Static `decide()` — returns APPLY_RULE, APPLY_DEFAULT, or IGNORE |
| `FileApplicability` | `validation/spec/` | Enum mapping marker strings to `ParsedConfigFile` flag checks |
| `DefaultPayloadValidator` | `validation/payload/` | Resolves all user inputs; validates mandatory fields, format, ranges |
| `DefaultRuleConfigValidator` | `validation/config/` | Validates rule definitions at startup — mandatory fields, duplicates, type constraints |

### Utilities

| Class | Path | Purpose |
|---|---|---|
| `CfgParserUtil` | `util/` | Parses .ADC file content line-by-line into blocks/entries; sets classification flags; enriches block occurrence metadata |
| `ConfigExtractionUtil` | `util/` | Static helpers for extracting values/comments from blocks with optional value mapping |
| `XmlToJsonTransformer` | `util/` | Converts XML (from frontend tool) to JSON; strips AEB_/COM_ prefixes |
| `ExcelSummaryUtil` + 5 helpers | `util/excel/` | Generates multi-sheet .xlsx workbook from `ValidationSummary` using Apache POI |

---

## 4. Domain Model

### Core Entities

**ParsedConfigFile** — Represents one parsed .ADC configuration file
- `fileName: String` — original filename (e.g., "C0371_00.ADC")
- `blocks: List<ConfigBlock>` — ordered list of configuration blocks
- `trackSectionDetails: boolean` — true if CFG_ZP_FMA1 or CFG_ZP_FMA2 present
- `acoIoexbDetails: boolean` — true if CFG_AXCNT or CFG_SECTION_OUT present
- `dtIoexbDetails: boolean` — true if CFG_DATA_SAFETY_LEVEL or CFG_DATA_OUT present
- `comDetails: boolean` — true if CFG_MY_IP_NW1 present
- `id: int` — extracted from ID block's ID entry value

**ConfigBlock** — One named section within an .ADC file
- `name: String` — block identifier (e.g., "CFG_AXCNT", "ID")
- `entries: List<ConfigEntry>` — key-value pairs within the block
- `sequenceNumber: int` — position in file
- `blockIndex: int` — 0-based index among blocks with same name
- `totalOccurrence: int` — how many blocks share this name
- `startSequenceNumber: int` — sequence number of first occurrence
- `endSequenceNumber: int` — sequence number of last occurrence

**ConfigEntry** — One parameter within a block
- `key: String` — parameter name (e.g., "BEHAV_INPUT1")
- `bits: int` — bit width of the parameter
- `value: String` — parameter value
- `comment: String` — inline comment from .ADC file

**RuleConfig** — One validation rule definition (loaded from ValidationConfiguration.json)
- `ruleType: String` — external name (e.g., "InputMatch", "RangeCheck")
- `configBlockName: String` — target block name
- `configEntryKey: String` — target entry key
- `uiInputRequired: String` — "Yes" or "No"
- `validateOnlyInFilesWith: String` — optional file marker (e.g., "ACOIOEXBDETAILS")
- `min: Integer` — minimum (RangeCheck only)
- `max: Integer` — maximum (RangeCheck only)
- `skipComFile: Boolean` — skip rule on COM files
- `origin: RuleOrigin` — CONFIGURED or DEFAULT

**ValidationResult** — One check outcome
- `fileName: String`
- `ruleType: String`
- `blockName: String`
- `entryKey: String`
- `expectedValue: String`
- `actualValue: String`
- `status: String` — "PASS" or "FAIL"

**ValidationSummary** — Aggregated validation output
- `validation_results: List<ValidationResult>`
- `dp_details: List<DpDetail>`
- `track_section_details: List<TrackSectionDetail>`
- `chc_details: List<CHCDetail>`
- `supervisor_details: List<SupervisorDetail>`
- `ioexb_behaviour_details: List<IOEXBBehaviourDetail>`
- `ioexb_aco_details: List<IOEXBAcoDetail>`
- `data_transmission_details: List<DataTransmissionDetail>`
- `ethernet_details: List<EthernetDetail>`

### Detail Models

**DpDetail** — Distribution Point summary per AEB file
- `dpId, fileName, behavReset, behavSimul, commFail, behavGe, clrTrack, resetIn, resetOut, interval, timeout, supervisCount, supervisCountLmt, systemCount, partialCount, trolleySupp, suppTime, diameter, axleDistance, speedTolerance, rsrType, typePrtctCode, projectNumber`

**TrackSectionDetail** — Track section (counting head pair) per FMA
- `dpId, fma, section, chcDpIdA, chcDpIdB`

**CHCDetail** — Counting Head Controller config
- `dpId, fileName, chcType`

**SupervisorDetail** — Supervisor section config per FMA
- `dpId, fma, section, logicType, resetType, autoresetActive, autoresetSection`

**IOEXBBehaviourDetail** — IOEXB input behaviour
- `dpId, behavInput1, behavInput2, behavInput3, typeIn1, typeIn2, typeIn3, behavIoexb, typeIoexb, coopResetCtrlType`

**IOEXBAcoDetail** — IOEXB Axle Counting Output
- `dpId, acoFma1, section, clrOcc, typeAux1, typeAux2, aux1Out, aux2Out, aux1NoNc, aux2NoNc`

**DataTransmissionDetail** — Data Transmission IOEXB
- `dpId, safetyLevelIn, safetyLevelOut, safeOutFdbckQuad, sourceDpId, nmbrOut, position`

**EthernetDetail** — COM board network config
- `dpId, fileName, myIpNw1, myIpNw2, myMaskNw1, myMaskNw2, destNw1Entries, destNw2Entries, fwrdAcdEntries`

### Enums

**RuleType** — 8 validation strategies
- `INPUT_MATCH`, `INPUT_MATCH_OR_BLOCK_NOT_FOUND`, `OPTIONAL_INPUT_MATCH`, `RANGE_CHECK`, `DUPLICATE_CHECK`, `MULTIPLE_BLOCK_SINGLE_INPUT_MATCH`, `MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH`, `PROJECT_BLOCK_CHECK`

**ValidationStatus** — `PASS`, `FAIL`, `INVALID`

**ValidationDecision** — `APPLY_RULE`, `APPLY_DEFAULT`, `IGNORE`

**RuleOrigin** — `CONFIGURED` (from JSON), `DEFAULT` (auto-generated)

**ConfigFileMarker** — `ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `TRACKSECTIONDETAILS`, `COMDETAILS`

**FileApplicability** — `ALL`, `TRACKSECTIONDETAILS`, `ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `COMDETAILS`
- Each value implements `applies(ParsedConfigFile) -> boolean`

### Key Interfaces

**ValidationRule** — Strategy interface for rule implementations
- `supportedType() -> RuleType`
- `execute(RuleExecutionContext) -> List<ValidationResult>`

**PayloadValidator** — User input validation contract
- `validate(UserValidationInputCriteria, Map<ValidationKey, List<RuleConfig>>) -> ResolvedPayloadContext`

**RuleConfigValidator** — Startup rule definition validation
- `validate(List<RuleConfig>) -> void` (throws on invalid)

### DTOs

**ValidationRequestWrapper** — API request body for `/api/config/validate`
- `parsedConfigFiles: List<ParsedConfigFile>`
- `userInput: UserValidationInputCriteria`

**UserValidationInputCriteria** — Dynamic map of user-provided expected values
- Uses `@JsonAnySetter` to capture arbitrary block → entry → value mappings

**RuleExecutionResult** — Engine output wrapper
- `results: List<ValidationResult>`
- `ruleExecuted: boolean`

**ApiErrorResponse** — Error response body
- `errorCode: String`
- `message: String`
- `timestamp: LocalDateTime`

---

## 5. Validation Rule Catalogue

All rules loaded from `ValidationConfiguration.json` at startup. 41 rule definitions total.

### ID Validation (applies to ALL files)

| # | Block.Entry | Rule Type | UIInput | SkipCom | Pass Condition |
|---|---|---|---|---|---|
| 1 | ID.ID | RangeCheck | No | false | Value is integer in [1, 4095] |
| 2 | ID.ID | DuplicateCheck | No | false | Value is unique across all files in the batch |

### Behaviour Toggle (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 3 | CFG_BEHAV_TGGL.BEHAV_RESET | InputMatch | Yes | File value equals user-provided expected value |
| 4 | CFG_BEHAV_TGGL.BEHAV_SIMUL | InputMatch | Yes | File value equals user-provided expected value |

### Track Section Config (TRACKSECTIONDETAILS files only)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 5 | CFG_SECTION.COMM_FAIL | InputMatch | Yes | Exact match with user input |
| 6 | CFG_SECTION.BEHAV_GE | InputMatch | Yes | Exact match with user input |
| 7 | CFG_SECTION.CLR_TRACK | InputMatch | Yes | Exact match with user input |
| 8 | CFG_SECTION.RESET_IN | InputMatch | Yes | Exact match with user input |
| 9 | CFG_SECTION.RESET_OUT | OptionalInputMatch | Yes | Any value in user-provided list matches file value |

### IOEXB ACO — Axle Count Input (ACOIOEXBDETAILS files only)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 10 | CFG_AXCNT.BEHAV_INPUT1 | InputMatch | Yes | Exact match |
| 11 | CFG_AXCNT.BEHAV_INPUT2 | InputMatch | Yes | Exact match |
| 12 | CFG_AXCNT.BEHAV_INPUT3 | OptionalInputMatch | Yes | Any value in list matches |
| 13 | CFG_AXCNT.TYPE_IN1 | InputMatch | No | Auto-validated (no user input needed) |
| 14 | CFG_AXCNT.TYPE_IN2 | InputMatch | No | Auto-validated |
| 15 | CFG_AXCNT.TYPE_IN3 | InputMatch | No | Auto-validated |
| 16 | CFG_AXCNT.BEHAV_IOEXB | InputMatch | Yes | Exact match |

### IOEXB ACO — Section Output (ACOIOEXBDETAILS files only)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 17 | CFG_SECTION_OUT.CLR_OCC | InputMatch | Yes | Exact match |
| 18 | CFG_SECTION_OUT.TYPE_AUX1 | InputMatch | No | Auto-validated |
| 19 | CFG_SECTION_OUT.TYPE_AUX2 | InputMatch | No | Auto-validated |
| 20 | CFG_SECTION_OUT.AUX1_OUT | InputMatch | Yes | Exact match |
| 21 | CFG_SECTION_OUT.AUX2_OUT | InputMatch | Yes | Exact match |
| 22 | CFG_SECTION_OUT.AUX1_NO_NC | InputMatch | Yes | Exact match |
| 23 | CFG_SECTION_OUT.AUX2_NO_NC | InputMatch | Yes | Exact match |

### Occupancy Extension (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 24 | CFG_OCC.OCC_DELAY | InputMatchOrBlockNotFound | Yes | Value matches OR block absent (both are PASS) |
| 25 | CFG_OCC.OCC_EXT | InputMatchOrBlockNotFound | Yes | Value matches OR block absent |

### Reset Timing (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 26 | CFG_RESET.RESET_LD_TIME | InputMatchOrBlockNotFound | Yes | Value matches OR block absent |
| 27 | CFG_RESET.RESET_OP_TIME | InputMatchOrBlockNotFound | Yes | Value matches OR block absent |

### Project Block (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 28 | CFG_PROJECT_AEB.PROJECT_NUMBER | ProjectBlockCheck | Yes | If user says BLOCK_EXISTS=true: block must exist and PROJECT_NUMBER matches. If BLOCK_EXISTS=false: block must not exist. |

### Timeout (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 29 | CFG_TIMEOUT.TIMEOUT_VALUE | MultipleBlockMultipleInputMatch | Yes | All TIMEOUT_VALUE entries across all CFG_TIMEOUT block occurrences must be in the user-provided allowed value set |

### Counting Head / FMA Config (TRACKSECTIONDETAILS files only)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 30 | CFG_ZP.INTERVAL | InputMatch | Yes | Exact match |
| 31 | CFG_ZP.SUPERVIS_COUNT | InputMatch | Yes | Exact match |
| 32 | CFG_ZP.SYSTEM_COUNT | InputMatch | Yes | Exact match |
| 33 | CFG_ZP.PARTIAL_COUNT | InputMatch | Yes | Exact match |
| 34 | CFG_ZP.SUPERVIS_COUNT_LMT | InputMatchOrBlockNotFound | No | Value matches OR block/entry absent |

### Trolley Protection / Suppression (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 35 | CFG_TROLLEY_SUPP.TROLLEY_SUPP | InputMatch | No | Auto-validated |
| 36 | CFG_PARAM_TROLLEY_SUPP.AXLE_DISTANCE | InputMatch | No | Auto-validated |
| 37 | CFG_PARAM_TROLLEY_SUPP.DIAMETER | InputMatch | No | Auto-validated |
| 38 | CFG_PARAM_TROLLEY_SUPP.SPEED_TOLERANCE | InputMatch | No | Auto-validated |
| 39 | CFG_PARAM_TROLLEY_SUPP.SUPP_TIME | InputMatch | No | Auto-validated |

### RSR Type / Type Protection (AEB files, skips COM)

| # | Block.Entry | Rule Type | UIInput | Pass Condition |
|---|---|---|---|---|
| 40 | CFG_RSR_TYPE.RSR_TYPE | InputMatch | No | Auto-validated |
| 41 | CFG_TYPE_PRTCT.TYPE_PRTCT_CODE | InputMatch | No | Auto-validated |

### Rule Type Behaviour Summary

| Rule Type | Pass | Fail |
|---|---|---|
| **InputMatch** | All block occurrences have entry value matching expected | Any value differs or entry not found → `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` |
| **InputMatchOrBlockNotFound** | Value matches expected OR block is entirely absent | Value exists but doesn't match |
| **OptionalInputMatch** | Any value in the user-provided list matches the file value | No match found |
| **RangeCheck** | Integer value within [min, max] | Outside range or non-numeric |
| **DuplicateCheck** | Value not seen before in current validation run | Value already registered (shared `DuplicateValueRegistry`) |
| **MultipleBlockSingleInputMatch** | All occurrences of block have same entry value matching expected | Any occurrence differs |
| **MultipleBlockMultipleInputMatch** | All entry values across block occurrences are in allowed set | Any value outside allowed set |
| **ProjectBlockCheck** | If BLOCK_EXISTS=true: block present and value matches. If false: block absent | Mismatch in either direction |

---

## 6. Validation Pipeline

### Phase 1: File Upload

```
POST /api/upload/adcfiles (multipart files[])
  → UploadController.adcFiles()
  → ConfigParsingService.parseFiles()
    → filters files by .adc extension (case-insensitive)
    → sorts remaining files by filename
    → for each file: CfgParserUtil.parse(MultipartFile)
      → reads file line-by-line
      → identifies block headers and entries
      → builds List<ConfigBlock> with List<ConfigEntry> each
      → enriches block occurrence metadata (totalOccurrence, blockIndex, startSequenceNumber, endSequenceNumber)
      → scans block names to set classification flags:
        - CFG_ZP_FMA1 or CFG_ZP_FMA2 → trackSectionDetails = true
        - CFG_AXCNT or CFG_SECTION_OUT → acoIoexbDetails = true
        - CFG_DATA_SAFETY_LEVEL or CFG_DATA_OUT → dtIoexbDetails = true
        - CFG_MY_IP_NW1 → comDetails = true
      → extracts file ID from ID block's ID entry
      → returns ParsedConfigFile
  → returns List<ParsedConfigFile> as JSON
```

### Phase 2: Validation Request

```
POST /api/config/validate (JSON body: ValidationRequestWrapper)
  → ConfigValidationController.validate()
  → ConfigValidationService.validateParsedFiles(files, userInput)
```

#### Step 2a: Payload Validation

```
  → DefaultPayloadValidator.validate(userInput, rulesByKey)
    → for each configured rule:
      → if UIInputRequired="Yes": user input must be present
      → if RangeCheck: validates min/max format
      → if ProjectBlockCheck: validates BLOCK_EXISTS boolean present
      → if OptionalInputMatch or MultipleBlockMultipleInputMatch: validates array format
    → resolves all user inputs into Map<ValidationKey, ResolvedPayload>
    → returns ResolvedPayloadContext (immutable)
```

#### Step 2b: Rule Execution (per file)

```
  → for each ParsedConfigFile:
    → create FileContext (wraps file, provides hasBlock() and values() lookups)
    → build validation universe = union of configured rule keys + user input keys
    → for each ValidationKey in universe:
      → RuleExecutionEngine.execute(file, context, key, ruleConfigs, resolvedPayloadContext)
        → for each RuleConfig targeting this key:
          → isFileEligible(rule, file):
            - check ValidateOnlyInFilesWith marker against file flags
            - if marker set and file doesn't match → skip
          → build ValidationContext(payloadPresent, rule, file)
          → ValidationDecisionEngine.decide(context):
            - IGNORE if: file ineligible, or (SkipComFile=true AND file.isComDetails())
            - APPLY_RULE if: rule exists and conditions met
            - APPLY_DEFAULT if: payload present but no configured rule
          → if APPLY_RULE:
            - lookup ValidationRule impl by RuleType
            - build RuleExecutionContext (FileContext, ValidationKey, RuleConfig, ResolvedPayload, DuplicateValueRegistry)
            - execute rule → List<ValidationResult>
          → if APPLY_DEFAULT:
            - DefaultRuleExecutor creates default InputMatch rule (origin=DEFAULT)
            - executes via same engine path
          → if IGNORE: no results produced
    → sort all results by ruleType, blockName, entryKey (case-insensitive)
```

#### Step 2c: Summary Generation

```
  → SummaryService.generateSummary(files, results)
    → DpDetailExtractorService.extractDpDetails(files)
    → TrackSectionExtractorService.extractTrackSectionDetails(files)
    → CHCExtractorService.extractCHCDetails(files)
    → SupervisorExtractorService.extractSupervisorDetails(files)
    → IOEXBBehaviourExtractorService.extractIOEXBBehaviourDetails(files)
    → IOEXBAcoExtractorService.extractIOEXBAcoDetails(files)
    → DataTransmissionExtractorService.extractDataTransmissionDetails(files)
    → EthernetDetailExtractorService.extractEthernetDetails(files)
    → assembles ValidationSummary with all results + detail lists
  → returns ValidationSummary
```

### Phase 3: Report Download

```
POST /api/report/download (JSON body: ValidationSummary)
  → ReportController.downloadReport()
  → ExcelSummaryUtil.generate(summary)
    → creates XSSFWorkbook
    → ExcelStyleManager: fonts, colors, borders
    → ExcelSheetBuilder: creates "Validation Results" sheet + detail sheets
    → ExcelHeaderProcessor: styled column headers
    → ExcelDataProcessor: populates cell values
    → ExcelFreezePaneManager: freezes header rows
    → returns byte[] (xlsx content)
  → returns ResponseEntity with Content-Type application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

### Execution Order

- Rules are **not** executed in a fixed sequence — they are grouped by `ValidationKey` (block + entry pair)
- Within a key, all configured rules execute (typically one rule per key)
- Results are **collected-all** (not fail-fast) — every rule runs regardless of prior failures
- `DuplicateValueRegistry` is **shared state** across all files in a single validation request — it tracks seen values for DuplicateCheck
- All other rules are stateless per-file

---

## 7. REST API Contracts

### POST /api/upload/adcfiles

**Purpose**: Parse uploaded .ADC configuration files

- **Request**: `multipart/form-data` with field `files` (array of .ADC files)
- **Response 200**: `List<ParsedConfigFile>` (JSON array)
- **Response 400**: `ApiErrorResponse` with `FILE_PARSE_ERROR` if parsing fails

### POST /api/config/validate

**Purpose**: Validate parsed configuration files against rules with user-provided expected values

- **Request**: JSON body `ValidationRequestWrapper`
  - `parsedConfigFiles: List<ParsedConfigFile>` — from upload response (pass-through)
  - `userInput: Map<blockName, Map<entryKey, value>>` — user-provided expected values
- **Response 200**: `ValidationSummary` (results + extracted details)
- **Response 400**: `ApiErrorResponse` with `INVALID_USER_INPUT` if payload validation fails
- **Response 500**: `ApiErrorResponse` with `ENGINE_ERROR` or `RULE_CONFIGURATION_ERROR`

### POST /api/upload/translate

**Purpose**: Convert XML configuration export to JSON format

- **Request**: `multipart/form-data` with XML file
- **Response 200**: JSON string (translated content)

### GET /api/configoptions

**Purpose**: Return UI dropdown options for all configurable parameters

- **Response 200**: `List<ConfigOptions>` — grouped parameter options with labels

### POST /api/report/download

**Purpose**: Generate Excel report from validation results

- **Request**: JSON body `ValidationSummary`
- **Response 200**: `byte[]` with Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- **Response 500**: `ApiErrorResponse` with `REPORT_GENERATION_FAILED`

---

## 8. Spring Boot Internal Architecture

### Package Responsibilities

| Package | Responsibility |
|---|---|
| `controller` | HTTP request handling, input binding, response shaping |
| `dto` | API-boundary data structures (request/response wrappers) |
| `model` | Domain entities shared across layers |
| `service` | Business logic orchestration |
| `service.extractors` | Domain-specific detail extraction from parsed files |
| `validation` | Core validation framework (interfaces, enums, factories) |
| `validation.engine` | Rule execution orchestration |
| `validation.rules` | Strategy pattern rule implementations (8 classes) |
| `validation.context` | Immutable context bundles passed through execution |
| `validation.payload` | User input resolution and validation |
| `validation.spec` | Decision logic (when to apply rules, defaults, or ignore) |
| `validation.config` | Jackson configuration, rule definition validation |
| `startup` | Application initialization (rule loading, fail-fast validation) |
| `util` | Parsing, extraction helpers, XML transformation |
| `util.excel` | Excel report generation infrastructure |
| `exception` | Exception hierarchy with centralized handler |
| `constants` | Shared string constants |

### Validator Organisation — Strategy Pattern

The validation engine uses the **Strategy pattern**:

1. `ValidationRule` interface defines the contract: `supportedType()` and `execute()`
2. Eight concrete implementations, one per `RuleType` enum value
3. `RuleExecutionEngine` maintains `EnumMap<RuleType, ValidationRule>` populated via Spring DI (constructor receives `List<ValidationRule>`, maps each by `supportedType()`)
4. At execution time, the engine resolves the implementation by `RuleType` and delegates

### Context Architecture

Validation execution passes immutable context bundles:

- `FileContext` — wraps `ParsedConfigFile`; provides `hasBlock(name)` and `values(block, entry)` lookups
- `ValidationKey` — `(blockName, entryKey)` tuple, used as map key throughout
- `ResolvedPayload` — wraps resolved user input value; provides `isPresent()`, `asString()`, `asStringList()`, `raw()`
- `ResolvedPayloadContext` — immutable `Map<ValidationKey, ResolvedPayload>` for entire request
- `RuleExecutionContext` — bundles FileContext + ValidationKey + RuleConfig + ResolvedPayload + ResolvedPayloadContext + DuplicateValueRegistry
- `ValidationContext` — decision-making context: `payloadPresent`, `rule`, `file`

### Decision Logic (ValidationDecisionEngine)

Static `decide(ValidationContext) -> ValidationDecision`:

1. **IGNORE** if file not eligible (marker mismatch)
2. **IGNORE** if SkipComFile=true and file is COM
3. **APPLY_RULE** if configured rule exists and conditions met
4. **APPLY_DEFAULT** if payload present but no configured rule (creates default InputMatch)
5. **IGNORE** otherwise

### Exception Handling

Abstract base `ConfigValidationException` with errorCode.

| Exception | HTTP Status | Error Code | Trigger |
|---|---|---|---|
| `InvalidUserValidationInputException` | 400 | INVALID_USER_INPUT | Missing/invalid user input |
| `FileParsingException` | 400 | FILE_PARSE_ERROR | .ADC file parsing failure |
| `RuleConfigurationException` | 500 | RULE_CONFIGURATION_ERROR | Invalid ValidationConfiguration.json |
| `ValidationEngineException` | 500 | ENGINE_ERROR | Runtime engine failure |
| `ReportGenerationException` | 500 | REPORT_GENERATION_FAILED | Excel generation failure |
| `HttpMessageNotReadableException` | 400 | INVALID_PAYLOAD | Malformed JSON body |

All handled by `GlobalExceptionHandler` (@RestControllerAdvice) returning `ApiErrorResponse`.

### Spring Configuration

- `JacksonConfig` (@Configuration) — ObjectMapper with `FAIL_ON_UNKNOWN_PROPERTIES=true`, `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`
- `ValidationConfigurationStartupValidator` — @PostConstruct calls `ValidationConfigurationLoader.loadConfiguredRules()` which reads `ValidationConfiguration.json`, validates via `DefaultRuleConfigValidator`, and fails startup on error (fail-fast)
- CORS configured via `cors.allowed-origins` property
- Swagger UI at `/swagger-ui.html`, API docs at `/api-docs`

---

## 9. .ADC File Format

The .ADC (ADC Configuration) file is a proprietary binary-derived text format used by Frauscher FAdC (Frauscher Advanced Counter) systems. Structure inferred from the parser:

### Overall Structure

An .ADC file consists of sequential **blocks**. Each block has:
- A **header line** containing the block name (e.g., `CFG_AXCNT`, `ID`, `CFG_SECTION`)
- One or more **entry lines**, each with a key, bit width, value, and optional comment

### Entry Format

Each entry contains:
- `key` — parameter identifier (e.g., `BEHAV_INPUT1`, `TIMEOUT_VALUE`)
- `bits` — bit width of the parameter (e.g., 8, 12, 32)
- `value` — string representation of the value
- `comment` — optional inline annotation

### Block Types by Board

**AEB (Advanced Evaluation Board) files** may contain:
- `ID` — Board identifier (DP ID)
- `CFG_BEHAV_TGGL` — Behaviour toggle (reset, simulation)
- `CFG_SECTION` — Track section config (comm fail, behaviour, clearing, reset)
- `CFG_ZP` — Counting head / FMA zone parameters
- `CFG_ZP_FMA1`, `CFG_ZP_FMA2` — Track section detail indicators
- `CFG_TIMEOUT` — Timeout values (can repeat)
- `CFG_OCC` — Occupancy extension/delay
- `CFG_RESET` — Reset timing
- `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP` — Trolley suppression
- `CFG_RSR_TYPE` — Rail sensor type
- `CFG_TYPE_PRTCT` — Type protection code
- `CFG_PROJECT_AEB` — Project number
- `CFG_CONTROL` — Controller config
- `CFG_SUPERVIS_FMA1/FMA2` — Supervisor sections
- `CFG_AUTORESET_FMA1/FMA2` — Auto-reset config
- `COMPONENT` — Component metadata (version, CRC)

**IOEXB ACO files** additionally contain:
- `CFG_AXCNT` — Axle counting input behaviour
- `CFG_SECTION_OUT` — Section output configuration
- `CFG_COOP_RESET` — Cooperative reset

**IOEXB DT files** additionally contain:
- `CFG_DATA_SAFETY_LEVEL` — Safety level input/output
- `CFG_DATA_OUT` — Data output configuration

**COM (Communication Board) files** contain:
- `ID` — Board identifier
- `CFG_INTERVAL` — Communication interval
- `CFG_PROJECT_COM` — COM project number
- `CFG_MY_IP_NW1`, `CFG_MY_IP_NW2` — Network IP addresses
- `CFG_MY_MASK` — Subnet masks
- `CFG_INT_ID_DEST_NW1/NW2` — Destination IDs
- `CFG_FWRD_ACD` — Forwarding config
- `COMPONENT` — Component metadata

### Block Repetition

Some blocks can appear multiple times in a single file:
- `CFG_TIMEOUT` — indexed by `blockIndex`, referenced by `SLCT_TIMEOUT` in other blocks
- `CFG_SECTION_OUT` — one per track section on an IOEXB ACO board
- `CFG_DATA_OUT` — one per data output on an IOEXB DT board
- `CFG_SUPERVIS_FMA1/FMA2` — one per supervisor section

The parser tracks `totalOccurrence`, `blockIndex`, `startSequenceNumber`, and `endSequenceNumber` for multi-occurrence block handling.

---

## 10. Configuration & Environment

### application.properties

| Property | Value | Purpose |
|---|---|---|
| `server.port` | 7443 | Application port |
| `logging.level.root` | INFO | Log level |
| `logging.file.name` | logs/configuration-validation-service.log | Log file path |
| `logging.logback.rollingpolicy.max-file-size` | 10MB | Log rotation size |
| `logging.logback.rollingpolicy.max-history` | 14 | Days of log retention |
| `logging.logback.rollingpolicy.total-size-cap` | 200MB | Total log storage |
| `management.endpoints.web.exposure.include` | health,info,metrics | Actuator endpoints |
| `springdoc.swagger-ui.path` | /swagger-ui.html | Swagger UI path |
| `cors.allowed-origins` | http://at-cvt01.frauscher.host:6443 | Frontend CORS origin |
| `file.parser.extension` | .adc | File extension filter |
| `server.tomcat.max-part-count` | 4096 | Max multipart parts |
| `server.tomcat.max-http-post-size` | 52428800 | 50MB POST body limit |

### ValidationConfiguration.json

JSON array of 41 rule definitions. Loaded at startup by `ValidationConfigurationLoader`, validated by `DefaultRuleConfigValidator`. Invalid configuration fails application startup. See Section 5 for full catalogue.

### value-mappings.properties

Key-value lookup table for UI display labels. Format: `FIELD_KEY.RAW_VALUE = display label`. Used by `ValueMappingService.mapValue(fieldKey, rawValue)` during detail extraction. 150+ mappings covering all configurable parameters.

### External Dependencies

- **nginx** — reverse proxy on port 443, forwards to 7443. Requires `client_max_body_size 50m;` for large payloads.
- **No database** — fully stateless, no persistence layer
- **No message queue** — synchronous request/response only
- **Deployed at**: `at-cvt01.frauscher.host`

---

## 11. Dependency & Call Graph

### Service Dependencies

```
ConfigValidationController
  └─→ ConfigValidationService
        ├─→ DefaultPayloadValidator
        ├─→ RuleExecutionEngine
        │     ├─→ 8 ValidationRule implementations
        │     ├─→ ValidationDecisionEngine (static)
        │     ├─→ FileApplicability (enum)
        │     └─→ DuplicateValueRegistry
        ├─→ DefaultRuleExecutor
        │     └─→ RuleExecutionEngine (re-entry)
        └─→ SummaryService
              ├─→ DpDetailExtractorService
              ├─→ TrackSectionExtractorService
              ├─→ CHCExtractorService
              ├─→ SupervisorExtractorService
              ├─→ IOEXBBehaviourExtractorService
              ├─→ IOEXBAcoExtractorService
              ├─→ DataTransmissionExtractorService
              ├─→ EthernetDetailExtractorService
              └─→ ValueMappingService

UploadController
  └─→ ConfigParsingService
        └─→ CfgParserUtil (static)

ReportController
  └─→ ExcelSummaryUtil (static)
        └─→ ExcelSheetBuilder, ExcelStyleManager, etc.
```

### External Library Usage

| Library | Used By | Purpose |
|---|---|---|
| Spring Boot Web | Controllers, exception handler | REST framework |
| Spring Boot Validation | Startup validators | Bean validation |
| Spring Boot Actuator | (auto-configured) | Health/metrics endpoints |
| Jackson | JacksonConfig, all JSON handling | JSON serialization |
| Jackson XML | XmlToJsonTransformer | XML-to-JSON conversion |
| Apache POI | ExcelSummaryUtil, excel/ package | Excel generation |
| SpringDoc OpenAPI | (auto-configured) | Swagger UI |
| Lombok | All model/DTO classes | Boilerplate reduction (@Data, @Builder, @AllArgsConstructor) |

### No Circular Dependencies

The architecture follows a clean top-down flow: controller → service → validation → rules. No circular references detected.

---

## 12. Known Complexity & Non-Obvious Areas

### DuplicateValueRegistry — Shared Mutable State

`DuplicateValueRegistry` maintains a `synchronized` map of `block::entry::value → count` across all files in a single validation request. It is the **only shared mutable state** in the validation pipeline. All other components are stateless per-file.

### Block Occurrence Enrichment

`CfgParserUtil.enrichBlockOccurrences()` is a post-parse step that adds metadata to blocks sharing the same name. This enables `MultipleBlockSingleInputMatch` and `MultipleBlockMultipleInputMatch` rules to iterate across all occurrences. The `blockIndex` field (0-based) distinguishes occurrences.

### Timeout Calculation

`ConfigExtractionUtil.extractTimeoutValue()` multiplies the raw `TIMEOUT_VALUE` by 10 to convert from internal units to milliseconds. The timeout block is referenced by index from `SLCT_TIMEOUT` entries in other blocks.

### Default Rule Execution

When a user provides input for a block/entry that has no configured rule, the engine creates a **default InputMatch rule** (`origin=DEFAULT`) on the fly. This means the validation can check any parameter the user provides expected values for, not just the 41 pre-configured rules.

### COM File Detection Gap

`comDetails` is currently triggered only by `CFG_MY_IP_NW1`. COM files without this block (e.g., smaller COM configurations with only `CFG_INTERVAL` and `CFG_PROJECT_COM`) are not flagged, causing AEB-only rules with `SkipComFile=true` to incorrectly run on them. [KNOWN BUG — under investigation]

### File Flag Independence

The four classification flags (`trackSectionDetails`, `acoIoexbDetails`, `dtIoexbDetails`, `comDetails`) are independent booleans, not mutually exclusive. A single file can have multiple flags set to `true` (e.g., mixed ACO+DT IOEXB boards on PWR-2+ backplanes).

### ValidationKey as Universe

The "validation universe" for each file is the union of all configured rule keys AND all user input keys. This means user input for unconfigured parameters still triggers validation via default rules.

### @JsonAnySetter on UserValidationInputCriteria

The user input DTO uses `@JsonAnySetter` to capture an arbitrary nested map structure (block → entry → value). This allows the frontend to send expected values for any parameter without the backend needing to predefine the schema.

---

## 13. Test Coverage Map

### Test Framework

- **Cucumber 7.15.0** with Spring integration for BDD
- **JUnit Platform Suite 1.10.2** as test runner
- **Spring Boot Test** for application context
- Feature files at `src/test/resources/cucumber/features/`
- Step definitions at `src/test/java/.../cucumber/stepdefs/`

### Coverage by Area

| Area | Feature Files | Coverage Level |
|---|---|---|
| **Rule implementations** (8 types) | 7 files, 75+ scenarios | Excellent — all rule types tested with edge cases, boundaries, error conditions |
| **Engine decisions** (apply/default/ignore) | 4 files, 20+ scenarios | Good — file eligibility, COM skipping, default rules |
| **Payload/config validation** | 2 files, 18+ scenarios | Excellent — missing fields, format errors, exception messages |
| **Extractor services** (8 extractors) | 8 files | Good — one integration scenario per extractor |
| **Config options** | 1 file | Good — parameter group loading, HTTP endpoint |
| **File parsing (CfgParserUtil)** | 0 files | Gap — all tests use pre-parsed JSON, no .ADC file tests |
| **Controller layer** | 0 files | Gap — no HTTP-level integration tests |
| **Excel report generation** | 0 files | Gap — not tested |
| **Error response formatting** | Partial | Exception scenarios tested via validation layer, not HTTP layer |

### Test Infrastructure

| Class | Purpose |
|---|---|
| `CucumberSpringTestRunner` | Suite runner with feature path and plugin config |
| `CucumberSpringConfiguration` | @SpringBootTest context for Cucumber |
| `CucumberHooks` | @Before/@After lifecycle: TestContext.reset()/clear() |
| `TestContext` | ThreadLocal shared state across step definitions |
| `DataHelper` | JSON parsing, reflection-based field access, factory methods |
| `ValidationSteps` | ~700 lines — all rule validation scenarios |
| `EngineSteps` | ~200 lines — engine decision and execution scenarios |
| `ExtractorSteps` | ~280 lines — extractor service integration scenarios |
| `ConfigOptionsSteps` | ~90 lines — config options lookup scenarios |

### No .ADC Sample Files

The repository contains no actual .ADC files. All tests use JSON representations of `ParsedConfigFile` objects embedded in feature files. Real-file parsing is only testable via the upload endpoint with actual .ADC files.
