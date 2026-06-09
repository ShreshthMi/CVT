# FCVT — Codebase Reference

Current-state snapshot of the **Frauscher Configuration Validation Tool** backend service. Reference style. The narrative of how it got here lives in `fcvt-phase1-history.md`; the in-flight Phase 2 work lives in `fcvt-phase2-design.md`.

**Snapshot point:** close of VTF-303 / opening of VTF-305 (late May 2026).
**Branch of record:** `develop`.

---

## 1. What it does

A web application that validates Frauscher FAdC (Frauscher Advanced Counter) `.ADC` configuration files against project-specific rules. A safety engineer uploads a batch of `.ADC` files (typically one per board — AEB, IOEXB-ACO, IOEXB-DT, COM), enters expected values for project-dependent parameters via UI dropdowns, and receives PASS/FAIL results per rule plus an Excel report with per-concern detail sheets.

Phase 1 (this document) is **single-file `.ADC` validation** driven by `ValidationConfiguration.json`. Phase 2 (separate document) extends with `.fct2` archive + PDQ workbook cross-correlation.

---

## 2. Deployment topology

```
Frontend SPA (at-cvt01.frauscher.host:6443)
        │   multipart .ADC upload  /  JSON validate / report
        ▼
nginx reverse proxy (443 → 7443, client_max_body_size 50m)
        ▼
Spring Boot (port 7443, embedded Tomcat)
```

- Host: `at-cvt01.frauscher.host`
- Spring Boot app on `7443`; nginx terminates TLS on `443` and forwards
- nginx must allow ≥50 MB bodies (`client_max_body_size 50m;`) — the validate endpoint can receive 60–100 MB of parsed JSON for large batches
- Single process; no clustering, no shared cache, no database
- Stateless across requests

### Tomcat / Spring tunables (`application.properties`)

| Property | Value |
|---|---|
| `server.port` | 7443 |
| `server.tomcat.max-part-count` | 4096 |
| `server.tomcat.max-http-post-size` | 52428800 (50 MB) |
| `cors.allowed-origins` | `http://at-cvt01.frauscher.host:6443` |
| `file.parser.extension` | `.adc` |
| `logging.file.name` | `logs/configuration-validation-service.log` |
| `logging.logback.rollingpolicy.max-file-size` | 10 MB |
| `logging.logback.rollingpolicy.max-history` | 14 days |
| `logging.logback.rollingpolicy.total-size-cap` | 200 MB |
| `management.endpoints.web.exposure.include` | health, info, metrics |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` |

### Sizing reference

- ~140 `.ADC` files ≈ 2–3 MB JSON
- ~4000 `.ADC` files ≈ 60–100 MB JSON
- Bumping past 50 MB requires lifting both `server.tomcat.max-http-post-size` and nginx `client_max_body_size`

---

## 3. Tech stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.2 |
| Spring Dependency Management | 1.1.7 |
| Jackson (databind, dataformat-xml, jsr310) | 2.15.4 |
| Apache POI (Excel) | 5.2.5 |
| SpringDoc OpenAPI | 2.8.6 |
| Lombok | managed by Spring BOM |
| Cucumber (java, spring, junit-platform-engine) | 7.15.0 |
| JUnit Platform Suite | 1.10.2 |
| Gradle | 8.14 |

Build / run:

| Command | Action |
|---|---|
| `./gradlew build` | Compile + tests + assemble jar |
| `./gradlew test` | Run Cucumber + unit tests |
| `./gradlew bootRun` | Start app on port 7443 |
| `docker build -t fcvt .` | Build container image |

---

## 4. Repository layout

```
configuration-validation-service/
├── src/
│   ├── main/
│   │   ├── java/com/frauscher/ConfigurationValidationService/
│   │   │   ├── ConfigurationValidationServiceApplication.java
│   │   │   ├── constants/         — ValidationConstants
│   │   │   ├── controller/        — 5 REST controllers
│   │   │   ├── dto/               — Request/response wrappers (4)
│   │   │   ├── exception/         — Hierarchy + global handler (7)
│   │   │   ├── model/             — Domain entities + detail models (16)
│   │   │   ├── service/
│   │   │   │   └── extractors/    — Per-concern detail extractors (9 dir, 8 active services)
│   │   │   ├── startup/           — Boot-time rule loading + validation (2)
│   │   │   ├── util/
│   │   │   │   └── excel/         — Excel helpers (6)
│   │   │   └── validation/
│   │   │       ├── config/        — Jackson, rule-config validator (3)
│   │   │       ├── context/       — Execution context bundles (5)
│   │   │       ├── engine/        — Rule execution orchestrator (2)
│   │   │       ├── payload/       — User input resolution (3)
│   │   │       ├── rules/         — 9 ValidationRule implementations
│   │   │       └── spec/          — Decision engine + file applicability (4)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── openapi.yaml
│   │       ├── ValidationConfiguration.json
│   │       └── value-mappings.properties
│   └── test/
│       ├── java/.../cucumber/stepdefs/   — Step defs (4 + 3 helpers)
│       └── resources/cucumber/features/  — 24 .feature files
├── docs/
├── build.gradle, settings.gradle
├── Dockerfile
├── .gitlab-ci.yml
└── gradle/wrapper/                       — Gradle 8.14 wrapper
```

Counts: **92 main Java source files**, **10 test Java files**, **24 Cucumber `.feature` files**.

> Scenario count: 157 scenarios across the 24 feature files. *To be verified — Shreshth will recount in a later pass and update this file. Source for current value: memory only (uploaded handover doesn't enumerate scenarios).*

---

## 5. REST API contracts

Five endpoints. All on port 7443, all under `/api`. Swagger at `/swagger-ui.html`. Actuator: `/actuator/{health,info,metrics}`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/upload/adcfiles` | Parse `.ADC` files → `List<ParsedConfigFile>` |
| POST | `/api/upload/translate` | One-shot XML → JSON helper (strips `AEB_` / `COM_` prefixes) |
| POST | `/api/config/validate` | Run validation engine; returns `ValidationSummary` |
| POST | `/api/report/download` | Generate Excel from a `ValidationSummary` |
| GET  | `/api/configoptions` | UI dropdown source built from `value-mappings.properties` |

### Per-endpoint contracts

**`POST /api/upload/adcfiles`**
- Consumes `multipart/form-data`; field name `files` (array)
- Filters by case-insensitive `.adc` extension; sorts by filename for determinism
- 200: `List<ParsedConfigFile>`
- 400: `FILE_PARSE_ERROR`, `INVALID_USER_INPUT` (empty/null)

**`POST /api/upload/translate`**
- Consumes `multipart/form-data`; one XML file
- 200: JSON string (Jackson `XmlMapper`; strips `AEB_` / `COM_` prefixes; preserves element order; forces all values to String)
- 400: extension check, malformed XML, "No configuration blocks found"

**`POST /api/config/validate`**
- Body: `ValidationRequestWrapper { parsedConfigFiles, userInput }`
- 200: `ValidationSummary`
- 400: `INVALID_USER_INPUT`, `INVALID_PAYLOAD` (malformed JSON)
- 500: `ENGINE_ERROR`, `RULE_CONFIGURATION_ERROR`

**`POST /api/report/download`**
- Body: `ValidationSummary` (round-tripped unmodified from `/validate`)
- 200: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; `Content-Disposition: attachment; filename="validation-summary.xlsx"`
- 500: `REPORT_GENERATION_FAILED`

**`GET /api/configoptions`**
- 200: `List<ConfigOptions>` — built from `value-mappings.properties` once at startup; the in-memory list is `final`

### Exception → HTTP map

| Exception | HTTP | Error code |
|---|---|---|
| `InvalidUserValidationInputException` | 400 | `INVALID_USER_INPUT` |
| `FileParsingException` | 400 | `FILE_PARSE_ERROR` |
| `HttpMessageNotReadableException` | 400 | `INVALID_PAYLOAD` |
| `RuleConfigurationException` | 500 | `RULE_CONFIGURATION_ERROR` |
| `ValidationEngineException` | 500 | `ENGINE_ERROR` |
| `ReportGenerationException` | 500 | `REPORT_GENERATION_FAILED` |

All routed through `GlobalExceptionHandler` (`@RestControllerAdvice`) returning `ApiErrorResponse`.

---

## 6. Validation pipeline — stages at a glance

```
/upload/adcfiles  ──►  ConfigParsingService
                          └─ CfgParserUtil.parse(MultipartFile)
                             · line-by-line, block headers vs entry lines
                             · enrichBlockOccurrences()
                             · 4 classification flags
                             · ID from ID.ID
                          → List<ParsedConfigFile>

/config/validate  ──►  ConfigValidationController
                          ├─ DefaultPayloadValidator.validate
                          │     · resolves all inputs
                          │     · enforces UIInputRequired / shape constraints
                          ├─ DuplicateValueRegistry.clear()
                          ├─ for each file: for each (block, entry) key:
                          │     RuleExecutionEngine.execute
                          │        · isFileEligible (marker check)
                          │        · ValidationDecisionEngine.decide → APPLY_RULE | IGNORE
                          │        · rulesByType.get(type).execute
                          │     if !ruleExecuted && no marker rule on key:
                          │        DefaultRuleExecutor.executeDefault
                          │           · synth InputMatch rule (origin = DEFAULT)
                          ├─ sort results (ruleType, blockName, entryKey, ci)
                          └─ SummaryService.generateSummary
                                · 8 extractors dispatched in fixed order
                                · ValueMappingService.mapValue per cell

/report/download  ──►  ExcelSummaryUtil.generate (Apache POI 5.2.5)
                          · 1 "Validation Results" sheet + 1 per non-empty detail list
```

### Validation universe — when does a check run?

For a given file, the engine runs on `(block, entry)` when **any** of:

1. Pair has a configured rule in `ValidationConfiguration.json` **AND** file passes the marker check **AND** `SkipComFile` is not violated.
2. The user submitted a value for the pair, the pair has **no** configured rule, **and** no marker-bearing sibling rule exists on the pair (default-rule path).
3. The pair has a `RangeCheck` or `DuplicateCheck` rule (`UIInputRequired=No` → always runs).

### Decision engine (pure)

`ValidationDecisionEngine.decide(ValidationContext)`:

1. Rule present and not applicable to file (marker mismatch) → `IGNORE`.
2. `SkipComFile` (default `true` when unset) and `file.isComDetails()` → `IGNORE`.
3. No configured rule, payload present → `APPLY_DEFAULT`.
4. No configured rule, payload absent → `IGNORE`.
5. Otherwise → `APPLY_RULE`.

`UIInputRequired` is **not** consulted here — `DefaultPayloadValidator` enforces it upstream.

---

## 7. Rule engine internals

### Rule types — 9 implementations, 7 currently configured

| Rule type | # configured | Semantics |
|---|---:|---|
| `InputMatch` | 15 | File value equals user value (or `DefaultValue` if payload absent and `UIInputRequired=No`) |
| `InputMatchOrBlockNotFound` | 20 | Match if present; or block entirely absent **and** `DefaultValue` ∈ expected set (VTF-299) |
| `OptionalInputMatchOrBlockNotFound` | 2 | Payload absent → skip; block missing → PASS iff `DefaultValue` ∈ expected set; block present → delegate to `OptionalInputMatch` (VTF-300) |
| `OptionalInputMatch` | 0 (unconfigured) | Any value in user-provided list matches file value; skip if payload absent |
| `RangeCheck` | 1 | Integer in `[min, max]` |
| `DuplicateCheck` | 1 | Value not previously registered for `block::entry` in this run |
| `MultipleBlockSingleInputMatch` | 0 (unconfigured) | All occurrences of a repeating block share the same value |
| `MultipleBlockMultipleInputMatch` | 1 | Every entry value across occurrences is in the user-provided allowed set (`LinkedHashSet` preserves order — VTF-295) |
| `ProjectBlockCheck` | 1 | `BLOCK_EXISTS=true` → block must exist and `PROJECT_NUMBER` matches; `=false` → block must be absent |

**Total configured rules: 41.**

### Configured rules — full table

| # | Block.Entry | Type | UIInput | Marker | SkipCom | Default |
|---|---|---|---|---|---|---|
| 1 | `ID.ID` | RangeCheck | No | – | false | 1–4095 |
| 2 | `ID.ID` | DuplicateCheck | No | – | false | – |
| 3 | `CFG_BEHAV_TGGL.BEHAV_RESET` | InputMatchOrBlockNotFound | Yes | – | true | 4 |
| 4 | `CFG_BEHAV_TGGL.BEHAV_SIMUL` | InputMatchOrBlockNotFound | Yes | – | true | 0 |
| 5 | `CFG_SECTION.COMM_FAIL` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 0 |
| 6 | `CFG_SECTION.BEHAV_GE` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 7 | `CFG_SECTION.CLR_TRACK` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 0 |
| 8 | `CFG_SECTION.RESET_IN` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 5 |
| 9 | `CFG_SECTION.RESET_OUT` | OptionalInputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 10 | `CFG_AXCNT.BEHAV_INPUT1` | InputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 1 |
| 11 | `CFG_AXCNT.BEHAV_INPUT2` | InputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 4 |
| 12 | `CFG_AXCNT.BEHAV_INPUT3` | OptionalInputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 0 |
| 13 | `CFG_AXCNT.TYPE_IN1` | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 14 | `CFG_AXCNT.TYPE_IN2` | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 15 | `CFG_AXCNT.TYPE_IN3` | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 16 | `CFG_AXCNT.BEHAV_IOEXB` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 17 | `CFG_SECTION_OUT.CLR_OCC` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 18 | `CFG_SECTION_OUT.TYPE_AUX1` | InputMatch | No | ACOIOEXBDETAILS | true | – |
| 19 | `CFG_SECTION_OUT.TYPE_AUX2` | InputMatch | No | ACOIOEXBDETAILS | true | – |
| 20 | `CFG_SECTION_OUT.AUX1_OUT` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 21 | `CFG_SECTION_OUT.AUX2_OUT` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 22 | `CFG_SECTION_OUT.AUX1_NO_NC` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 23 | `CFG_SECTION_OUT.AUX2_NO_NC` | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 24 | `CFG_OCC.OCC_DELAY` | InputMatchOrBlockNotFound | Yes | – | true | 0 |
| 25 | `CFG_OCC.OCC_EXT` | InputMatchOrBlockNotFound | Yes | – | true | 26 |
| 26 | `CFG_RESET.RESET_LD_TIME` | InputMatchOrBlockNotFound | Yes | – | true | 1 |
| 27 | `CFG_RESET.RESET_OP_TIME` | InputMatchOrBlockNotFound | Yes | – | true | 50 |
| 28 | `CFG_PROJECT_AEB.PROJECT_NUMBER` | ProjectBlockCheck | Yes | – | true | – |
| 29 | `CFG_TIMEOUT.TIMEOUT_VALUE` | MultipleBlockMultipleInputMatch | Yes | – | true | – |
| 30 | `CFG_ZP.INTERVAL` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 3 |
| 31 | `CFG_ZP.SUPERVIS_COUNT` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 2 |
| 32 | `CFG_ZP.SYSTEM_COUNT` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 2 |
| 33 | `CFG_ZP.PARTIAL_COUNT` | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 34 | `CFG_ZP.SUPERVIS_COUNT_LMT` | InputMatchOrBlockNotFound | No | TRACKSECTIONDETAILS | true | 0 |
| 35 | `CFG_TROLLEY_SUPP.TROLLEY_SUPP` | InputMatch | No | – | true | – |
| 36 | `CFG_PARAM_TROLLEY_SUPP.AXLE_DISTANCE` | InputMatch | No | – | true | – |
| 37 | `CFG_PARAM_TROLLEY_SUPP.DIAMETER` | InputMatch | No | – | true | – |
| 38 | `CFG_PARAM_TROLLEY_SUPP.SPEED_TOLERANCE` | InputMatch | No | – | true | – |
| 39 | `CFG_PARAM_TROLLEY_SUPP.SUPP_TIME` | InputMatch | No | – | true | – |
| 40 | `CFG_RSR_TYPE.RSR_TYPE` | InputMatch | No | – | true | – |
| 41 | `CFG_TYPE_PRTCT.TYPE_PRTCT_CODE` | InputMatch | No | – | true | – |

### Default rule mechanism

If a user submits an expected value for a `(block, entry)` with **no** configured rule, `DefaultRuleExecutor` synthesises an `InputMatch` rule on the fly:

- `ruleType = "InputMatch"`
- `uiInputRequired = "No"`
- `skipComFile = true`
- `origin = DEFAULT`

This is the mechanism behind the "user can validate any parameter, not just the 41 configured ones" property.

**Two guards that suppress default rules:**

1. **Marker-rule guard** (`ConfigValidationService`) — if a key has a configured rule with `ValidateOnlyInFilesWith` set, the engine intentionally skipped that file for that rule. A default rule **must not** be created for it, because the marker is the rule author's signal "this entry isn't meaningful on this file class."
2. **ProjectBlockCheck guard** (`RuleExecutionEngine`) — once a `ProjectBlockCheck` runs on a block, sibling `(block, entry)` defaults are silently suppressed because the block-level check already covers them.

### Startup invariants (`ValidationConfigurationStartupValidator`, `@PostConstruct`)

Any violation aborts boot — typos in the JSON fail boot, not requests:

1. `ValidationConfiguration.json` must exist on the classpath and be non-empty.
2. Every rule must have `RuleType`, `ConfigBlockName`, `ConfigEntryKey`, `UIInputRequired`.
3. `RuleType` must be one of `RuleType.supportedExternalNames()`.
4. `RangeCheck` rules must have ordered `min`/`max`.
5. Any `*OrBlockNotFound` rule must have `DefaultValue` (VTF-299).
6. `ValidateOnlyInFilesWith`, if present, must be a member of `ConfigFileMarker` (`ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `TRACKSECTIONDETAILS`, `COMDETAILS`).
7. No duplicate `(ruleType, block, entry, marker)` tuple.

Jackson is configured with `FAIL_ON_UNKNOWN_PROPERTIES = true` — unknown JSON fields produce `UnrecognizedPropertyException` → `RuleConfigurationException` at boot. This is intentional and applies at runtime too (see §13).

### Strategy pattern + dispatch

`ValidationRule` interface → 9 `@Component` implementations. `RuleExecutionEngine` constructor takes `List<ValidationRule>` (Spring DI) and indexes by `supportedType()` into `EnumMap<RuleType, ValidationRule>`. Dispatch is O(1).

### Result sort

After the outer file loop:

```
results.sorted by ruleType (ci), then blockName, then entryKey
```

This is the order shipped to the frontend and into the Excel "Validation Results" sheet.

---

## 8. Domain model

### `ParsedConfigFile` — one parsed `.ADC` file

- `fileName: String`
- `blocks: List<ConfigBlock>`
- `id: int` — extracted from the `ID` block's `ID` entry (`NumberFormatException` → 0)
- **4 independent classification booleans** (a single file may have multiple set — PWR-2+ backplane is the canonical multi-flag case):

| Flag | Set when file contains |
|---|---|
| `trackSectionDetails` | `CFG_ZP_FMA1` or `CFG_ZP_FMA2` |
| `acoIoexbDetails` | `CFG_AXCNT` or `CFG_SECTION_OUT` |
| `dtIoexbDetails` | `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` |
| `comDetails` | `CFG_MY_IP_NW1` — **known gap, see §13** |

Lombok `@AllArgsConstructor` is in use; field order matters for positional construction: `(fileName, blocks, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails, id)`.

### `ConfigBlock`

- `name: String` (e.g. `CFG_AXCNT`, `ID`)
- `entries: List<ConfigEntry>`
- `sequenceNumber: int`
- `blockIndex: int` — 0-based index among blocks sharing the same name
- `totalOccurrence: int`
- `startSequenceNumber: int`, `endSequenceNumber: int` — min/max sequence among same-name blocks

`enrichBlockOccurrences()` runs at parse time and populates the occurrence fields. Anything downstream relies on these being set; do not construct `ConfigBlock` instances outside the parser without populating them.

**Block-naming quirk:** the parser names a block from its **first entry's key**. In practice every block's first entry has a key matching the block name itself (a "header entry"). This means each block has one entry whose key matches the block name and whose value is not the validated payload.

### `ConfigEntry`

- `key: String` (e.g. `BEHAV_INPUT1`, `TIMEOUT_VALUE`)
- `bits: int` — bit width
- `value: String`
- `comment: String` — inline annotation from the `.ADC` file

### `RuleConfig`

- `ruleType: String` (external name e.g. `InputMatch`)
- `configBlockName: String`, `configEntryKey: String`
- `uiInputRequired: String` (`"Yes"` or `"No"`)
- `validateOnlyInFilesWith: String` — optional marker (`ConfigFileMarker`)
- `min`, `max: Integer` — RangeCheck only
- `defaultValue: String` — required for `*OrBlockNotFound` rule types
- `skipComFile: Boolean`
- `origin: RuleOrigin` — `CONFIGURED` or `DEFAULT`

JSON field casing matches Java: `RuleType`, `ConfigBlockName`, `ConfigEntryKey`, `UIInputRequired`, `SkipComFile`, `ValidateOnlyInFilesWith`, `DefaultValue`, `min`, `max`.

### `ValidationResult`

- `fileName`, `ruleType`, `blockName`, `entryKey`, `expectedValue`, `actualValue`, `status`
- `status` ∈ `"PASS"`, `"FAIL"`, `"INVALID"` (rare)
- `actualValue` sentinels:
  - `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` — entry not found anywhere in the file
  - `CONFIG_BLOCK_NOT_FOUND` — whole block absent (used by `*OrBlockNotFound` rules)

### `ValidationSummary`

- `validation_results: List<ValidationResult>`
- 8 detail lists: `dp_details`, `track_section_details`, `chc_details`, `supervisor_details`, `ioexb_behaviour_details`, `ioexb_aco_details`, `data_transmission_details`, `ethernet_details`

### Context bundles (all immutable)

- `FileContext` — wraps `ParsedConfigFile`; exposes `hasBlock(name)`, `values(block, entry)`
- `ValidationKey` — `(blockName, entryKey)` tuple; map key throughout
- `ResolvedPayload` — wraps user-supplied value(s); `isPresent()`, `asString()`, `asStringList()`, `raw()`
- `ResolvedPayloadContext` — full immutable request-wide payload map
- `RuleExecutionContext` — `FileContext` + `ValidationKey` + `RuleConfig` + `ResolvedPayload` + `ResolvedPayloadContext` + `DuplicateValueRegistry`

---

## 9. UserInput JSON shapes per rule type

`UserValidationInputCriteria` uses `@JsonAnySetter` to capture an arbitrary top-level map `{ <blockName>: { <entryKey>: value, … } }` into a private `sections: Map<String, Map<String, Object>>`. The backend does not pre-declare the schema; only configured rules with `UIInputRequired=Yes` are enforced.

| Rule type | userInput value form | Example |
|---|---|---|
| `InputMatch`, `InputMatchOrBlockNotFound` | Scalar string | `"BEHAV_RESET": "4"` |
| `OptionalInputMatch`, `OptionalInputMatchOrBlockNotFound`, `MultipleBlockMultipleInputMatch` | Array of strings | `"BEHAV_INPUT3": ["0","7"]` |
| `RangeCheck` (only if `UIInputRequired=Yes` — currently dormant since the only `RangeCheck` is on `ID.ID` with `UIInputRequired=No`) | `{ min, max }` object | `"ID": { "min": 1, "max": 4095 }` |
| `ProjectBlockCheck` | Block-level map with `BLOCK_EXISTS: boolean`; `PROJECT_NUMBER` present iff `BLOCK_EXISTS=true` | `"CFG_PROJECT_AEB": { "BLOCK_EXISTS": true, "PROJECT_NUMBER": "1234" }` |

**Reference: complete expected `userInput` structure currently shipped by the frontend** (from VTF-265 post-fix):

```json
{
  "ID": { "ID": { "min": 1, "max": 4095 } },
  "CFG_PROJECT_AEB": { "BLOCK_EXISTS": false, "PROJECT_NUMBER": "0" },
  "CFG_BEHAV_TGGL": { "BEHAV_RESET": "7", "BEHAV_SIMUL": "1" },
  "CFG_SECTION": { "COMM_FAIL": "0", "BEHAV_GE": "1", "CLR_TRACK": "0", "RESET_IN": "5", "RESET_OUT": "1" },
  "CFG_ZP": { "INTERVAL": "2", "SUPERVIS_COUNT": "2", "SUPERVIS_COUNT_LMT": "0", "SYSTEM_COUNT": "2", "PARTIAL_COUNT": "1" },
  "CFG_OCC": { "OCC_EXT": "26", "OCC_DELAY": "1" },
  "CFG_TIMEOUT": { "TIMEOUT_VALUE": ["34", "61"] },
  "CFG_AXCNT": {
    "BEHAV_INPUT1": "6", "TYPE_IN1": "0",
    "BEHAV_INPUT2": "6", "TYPE_IN2": "0",
    "BEHAV_INPUT3": "6", "TYPE_IN3": "0",
    "BEHAV_IOEXB": "7", "TYPE_IOEXB": "0"
  },
  "CFG_SECTION_OUT": {
    "CLR_OCC": "0", "TYPE_AUX1": "0", "TYPE_AUX2": "0",
    "AUX1_OUT": "3", "AUX1_NO_NC": "0",
    "AUX2_OUT": "3", "AUX2_NO_NC": "0"
  },
  "CFG_RESET": { "RESET_OP_TIME": "50", "RESET_LD_TIME": "1" },
  "CFG_TROLLEY_SUPP": { "TROLLEY_SUPP": "1" },
  "CFG_PARAM_TROLLEY_SUPP": { "SUPP_TIME": "60", "DIAMETER": "109", "AXLE_DISTANCE": "5", "SPEED_TOLERANCE": "12" },
  "CFG_RSR_TYPE": { "RSR_TYPE": "1" },
  "CFG_TYPE_PRTCT": { "TYPE_PRTCT_CODE": "0xb192f6cd" }
}
```

### Payload-validation failure modes (`DefaultPayloadValidator`)

All throw `InvalidUserValidationInputException` → 400:

| Condition | Message |
|---|---|
| `UIInputRequired=Yes` and no entry for the key in `resolvedPayloads` | `payload input required for <block>::<entry>` |
| `RangeCheck` with `UIInputRequired=Yes` but no `{ min, max }` | `RangeCheck requires {min, max} for <block>::<entry>` |
| `OptionalInputMatch` / `MultipleBlockMultipleInputMatch` value not a `List` | `Array value required for <block>::<entry>` |
| `ProjectBlockCheck` block map has no `BLOCK_EXISTS: Boolean` | `ProjectBlockCheck requires BLOCK_EXISTS boolean in block <block>` |

(`OptionalInputMatchOrBlockNotFound` is not explicitly array-validated in Pass 2 — falls through to default arm; the rule defensively uses `payload.asStringList()`.)

---

## 10. Summary stage — extractor cheat-sheet

`SummaryService.generateSummary(parsedConfigFiles, validationResults)` is **stateless** (`VTF-280`) and dispatches in this exact order:

| # | Extractor | File filter | Trigger blocks read |
|---|---|---|---|
| 1 | `DpDetailExtractorService` | `!file.isComDetails()` (applied in `SummaryService.extractDpDetails`) | `ID`, `CFG_SECTION`, `CFG_BEHAV_TGGL`, `CFG_TIMEOUT`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT`, `CFG_PROJECT_AEB` |
| 2 | `TrackSectionExtractorService` | `file.isTrackSectionDetails()` | `CFG_ZP_FMA1`, `CFG_ZP_FMA2` |
| 3 | `CHCExtractorService` | `!file.isComDetails()` | `CFG_CONTROL`, `CFG_ZP` |
| 4 | `SupervisorExtractorService` | `!file.isComDetails()` | `CFG_SUPERVIS_FMA1/FMA2`, `CFG_AUTORESET_FMA1/FMA2` |
| 5 | `IOEXBBehaviourExtractorService` | `file.isAcoIoexbDetails()` | `CFG_AXCNT`, `CFG_COOP_RESET` |
| 6 | `IOEXBAcoExtractorService` | `file.isAcoIoexbDetails()` | `CFG_SECTION_OUT` |
| 7 | `DataTransmissionExtractorService` | `file.isDtIoexbDetails()` | `CFG_DATA_SAFETY_LEVEL`, `CFG_DATA_OUT` |
| 8 | `EthernetDetailExtractorService` | `file.isComDetails()` | `CFG_MY_IP_NW1/NW2`, `CFG_MY_MASK`, `CFG_INT_ID_DEST_NW1/NW2`, `CFG_FWRD_ACD`, `CFG_INTERVAL` |

Each extractor reads its blocks, maps raw codes to UI labels via `ValueMappingService.mapValue(fieldKey, rawValue)` from `value-mappings.properties`, and builds a Lombok `@Builder` detail object.

**Multi-IP / forward-ACD handling:** `EthernetDetail.destIpNw1` and `destIpNw2` are `List<String>` (VTF-303); `EthernetDetailExtractorService.buildIpAddressList()` iterates all `CFG_INT_ID_DEST_NW1/NW2` blocks (same pattern as `CFG_FWRD_ACD`). Excel renders these by joining list entries with `"\n"` inside a single cell (`ExcelDataProcessor.setCellValue`).

---

## 11. DuplicateValueRegistry — only mutable shared state

A Spring-managed singleton holding a `synchronized Map<String, Map<String, Map<String, Integer>>>` keyed `block::entry::value → count`. Used only by `DuplicateCheckRule` — `register()` per encountered value; second occurrence (count reaches 2) emits FAIL for both files.

`duplicateRegistry.clear()` is called at the top of every `/validate` request so that duplicate counts operate within a single request's universe.

**Concurrency caveat:** two concurrent `/validate` requests will see each other's counts (the singleton is `.clear()`-ed at request start, but there is no per-request copy). Acceptable as of VTF-305 because the deployed environment is single-user, low concurrency. If concurrent validation becomes a requirement, the registry must become request-scoped.

---

## 12. `.ADC` file format (inferred — no spec in repo)

Frauscher proprietary text format. A file is a sequence of blocks; each block has a header line in `[BRACKETS]` and entry lines of the form `KEY  <bits>:<value>  // comment`. Blank lines and `//` line-comments are skipped silently.

### Block taxonomy

**AEB-typical:** `ID`, `CFG_BEHAV_TGGL`, `CFG_SECTION`, `CFG_ZP`, `CFG_ZP_FMA1/2`, `CFG_TIMEOUT` (repeats), `CFG_OCC`, `CFG_RESET`, `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT`, `CFG_PROJECT_AEB`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1/2`, `CFG_AUTORESET_FMA1/2`, `COMPONENT`.

**IOEXB-ACO additional:** `CFG_AXCNT`, `CFG_SECTION_OUT`, `CFG_COOP_RESET`.

**IOEXB-DT additional:** `CFG_DATA_SAFETY_LEVEL`, `CFG_DATA_OUT`.

**COM-typical:** `ID`, `CFG_INTERVAL`, `CFG_PROJECT_COM`, `CFG_MY_IP_NW1/2`, `CFG_MY_MASK`, `CFG_INT_ID_DEST_NW1/2` (repeats), `CFG_FWRD_ACD` (repeats), `COMPONENT`.

### Repeating blocks

`CFG_TIMEOUT`, `CFG_SECTION_OUT`, `CFG_DATA_OUT`, `CFG_SUPERVIS_FMA1/2`, `CFG_INT_ID_DEST_NW1/2`, `CFG_FWRD_ACD` may repeat. The parser tracks `totalOccurrence`, `blockIndex`, `startSequenceNumber`, `endSequenceNumber`. `CFG_TIMEOUT` is indexed and referenced by `SLCT_TIMEOUT` entries elsewhere.

### Component generations

The `COMPONENT` block carries a version field. AEB files have a value ≤ 3, COM files ≥ 4 (e.g. 105) — a potential alternative discriminator for `comDetails` (see §13). VTF-290 made several entries `UIInputRequired=No` because GS05 components don't produce them: `TYPE_IN1/2/3` in `CFG_AXCNT`, `TYPE_AUX1/2` in `CFG_SECTION_OUT`, `SUPERVIS_COUNT_LMT` in `CFG_ZP`.

---

## 13. Known bugs and gotchas (pending at VTF-305 boundary)

### 1. COM file classification gap — **KNOWN BUG**

`comDetails` is set only when `CFG_MY_IP_NW1` is present. COM files with minimal config (`CFG_INTERVAL` + `CFG_PROJECT_COM` only) are missed → AEB-only rules with `SkipComFile=true` then incorrectly run on them. Candidate fixes: also trigger on `CFG_PROJECT_COM`, or use `COMPONENT` version ≥ 4. Not yet fixed.

### 2. CFG_TIMEOUT parsing sequence — under investigation

First timeout value appears last in validation output. Suspected ordering bug in `MultipleBlockMultipleInputMatchRule`'s actual-value iteration. VTF-295 partly addressed expected-value ordering via `LinkedHashSet`.

### 3. CFG_ZP InputMatch anomaly — under investigation

Reported `InputMatch` anomaly on `CFG_ZP` block. May correlate with GS05/GS06 component version handling.

### 4. Multi-flag files (gotcha, not a bug)

A single `.ADC` can legitimately have `acoIoexbDetails=true` and `dtIoexbDetails=true` simultaneously (PWR-2+ backplane). All extractors and rule selectors must treat the flags as **independent** — never mutually exclusive.

### 5. `@JsonAnySetter` schema-laxness

`UserValidationInputCriteria` accepts any nested structure. Only rules with `UIInputRequired=Yes` are enforced; **typos in keys silently pass through**. Intentional (default-rule path consumes arbitrary user inputs) but worth knowing.

### 6. `FAIL_ON_UNKNOWN_PROPERTIES = true` (intentional, but a recurring break-source)

Any new field added to a DTO without a matching frontend update will 400 the request. Recent contract breaks of this shape: VTF-297 (`ioexbDetails` → `acoIoexbDetails` + `dtIoexbDetails`), VTF-303 (`destIpNw1` String → `List<String>`). When you change `ParsedConfigFile`, the frontend **must** match.

### 7. Block-occurrence enrichment lifetime

`enrichBlockOccurrences()` runs at parse time. Anything downstream (extractors, rules) relies on the indices being already set. Do not construct `ConfigBlock` instances outside the parser without populating these fields.

### 8. Timeout unit conversion

`ConfigExtractionUtil.extractTimeoutValue` multiplies raw `TIMEOUT_VALUE` by 10 to produce milliseconds. Anywhere else reading timeouts must use the same helper or replicate the conversion.

### 9. No sample `.ADC` files in repo

All Cucumber tests use embedded JSON `ParsedConfigFile` representations. Real-file parsing is only exercised through manual upload tests.

### 10. Input file hygiene — pending

No upload sanitisation of user `.ADC` files yet. Tracked as a Phase 2 item.

---

## 14. Pending feature work (not yet implemented)

### CFG_SWITCH validation

CFG_SWITCH analysis recommends adding three rules using `InputMatchOrBlockNotFound` for `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME`.

- `UIInputRequired: "Yes"`
- `SkipComFile: true`
- `TYPE_PRTCT` excluded

Config-only addition + frontend fields; no parser/backend code changes needed. **Not yet implemented at VTF-305 boundary.**

---

## 15. Configuration files

### `application.properties`

See §2 for the full table.

### `value-mappings.properties`

~150 entries. Format: `FIELD_KEY.RAW_VALUE = display label`. Consumed by `ValueMappingService.mapValue(fieldKey, rawValue)`. Used in extractor output, **not** in validation (validation matches raw values).

`ConfigOptionsService` loads this once at construction (the in-memory `configOptions` field is `final`, set in constructor — VTF-280). Property reload requires a restart by design.

Range-metadata keys (`min`, `max`, `step`, `description`) sometimes have values like `"min - 0"` — `ConfigOptionsService` strips everything up to `" - "` for those keys.

### `openapi.yaml`

Hand-maintained OpenAPI 3 spec. When adding/changing DTO fields, this file must be updated. `acoIoexbDetails` / `dtIoexbDetails` already replace the older `ioexbDetails`.

### `ValidationConfiguration.json`

The 41-rule definitions consumed by the startup validator. Field casing: `RuleType`, `ConfigBlockName`, `ConfigEntryKey`, `UIInputRequired`, `SkipComFile`, `ValidateOnlyInFilesWith`, `DefaultValue`, `min`, `max`.

---

## 16. Statelessness — what is and isn't persisted

- **Persisted:** nothing. No database, no file store, no cross-request cache.
- **Per-request:** `DuplicateValueRegistry` (cleared at request start).
- **Per-process:** rules loaded from `ValidationConfiguration.json` (read once at startup; immutable thereafter), config-options loaded from `value-mappings.properties` (same).
- **Per-file:** classification flags and block-occurrence metadata; computed once at parse time and never mutated.

---

## 17. Hardware terminology

| Term | Meaning |
|---|---|
| **DP** | Detection Point — counting head pair on the track (an axle counter section endpoint). |
| **AEB** | Advanced Evaluation Board — primary evaluation logic. |
| **IOEXB** | I/O Extension Board. Two variants: **ACO** (axle-counting I/O) and **DT** (data transmission). |
| **COM** | Communication board — Ethernet networking. |
| **FMA** | Track section (Field Manipulation Area). One AEB controls 1–2 FMAs. |
| **ZP** | Zone / counting head parameters. |
| **CHC** | Counting Head Controller. |
| **GS05 / GS06 / GS07** | Component generations. GS05 differs in available parameters (VTF-290 added version-aware skipping). |
| **PWR-2+** | Backplane variant capable of hosting both ACO and DT IOEXB on the same AEB. |

---

## 18. Test coverage (Cucumber)

24 feature files under `src/test/resources/cucumber/features/{engine, extractors, rules}/`. Step definitions under `src/test/java/.../cucumber/stepdefs/{ValidationSteps, EngineSteps, ExtractorSteps, ConfigOptionsSteps}`. Helpers: `CucumberHooks`, `TestContext` (ThreadLocal), `DataHelper`.

| Area | Coverage |
|---|---|
| Rule implementations (9 types) | Excellent — boundaries, missing-block, multi-value, defaults |
| Engine decisions | Good — applicability, COM skip, default rule paths |
| Payload/config startup validation | Excellent |
| Extractor services (8) | One integration scenario per extractor |
| ConfigOptions API | Good |
| `CfgParserUtil` (raw `.ADC` parsing) | **Gap** — no real-file tests; all tests use pre-parsed JSON |
| Controller / HTTP layer | **Gap** — no `MockMvc` or `@WebMvcTest` tests |
| Excel generation | **Gap** — not tested |
| Error response formatting | Partial — validation-layer scenarios only |

Runner: `CucumberSpringTestRunner` with `@SelectClasspathResource` pointing to `cucumber/features`. `./gradlew test` runs the full suite.

---

*End of codebase reference. Updates required whenever the running codebase changes on `develop`.*
