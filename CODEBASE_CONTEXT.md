# Configuration Validation Service (CVT): Codebase Context at `VTF-371`

> **Purpose.** This is planning context for the **UAT-feedback work package (AE team)**. It records what
> the service does at branch `VTF-371` (HEAD `eb769da`, 2026-08-25) and how it does it. It also lists
> where UAT feedback is likely to land. Read it before planning. Each claim names the file it comes
> from, so it can be re-verified.
>
> **Snapshot health (2026-10-08):** `./gradlew test` passes. That is **288 tests, 0 failures**: 131 Cucumber
> scenarios plus 157 JUnit tests across 34 suites. The jar boots, and the PDQ upload, FCT upload, v2
> validate and report download endpoints were exercised against the repo fixtures (see §14).
>
> Items marked **[verified]** were confirmed by running the service or by direct code reading. Items
> marked **[inferred]** are reasoned but not proven. **None of the design docs the code cites are in
> the repo:** `docs/` is in `.gitignore` (see §16).

---

## 0. TL;DR

- **What it is:** a Spring Boot 4 / Java 21 REST backend. It validates **Frauscher FAdC axle-counter
  configuration files (`.adc`)** against a design baseline and produces a validation log, eight
  "detail" tables and an Excel report. A separate frontend (not in this repo) calls it. Users are
  the **AE (Application Engineering)** team.
- **Two validation paths coexist:**
  - **Phase 1:** `POST /api/config/validate`. The user types expected values in the UI as a flat
    `block -> entry -> value` map. A config-driven rule engine runs 50 rules from
    `ValidationConfiguration.json`.
  - **Phase 2 ("v2"):** `POST /api/config/v2/validate`. Expected values are derived from two uploaded
    baseline artifacts: the **PDQ workbook** (`.xlsx`, parsed by `POST /api/upload/pdq`) and the **FCT
    archive** (`.fct2`, parsed by `POST /api/upload/fct`), plus optional **tpf** blocks. A
    preprocessor cross-correlates them into a *scalar* bucket (fed to the Phase 1 engine) and an
    *instanced* bucket (per-entity checks by a new evaluator). Failing results are then **annotated
    onto detail-table cells** (`_mismatches`).
- **Where UAT has been landing:** VTF-359 through VTF-362, then VTF-371. The issues were false-FAIL
  noise from broadcast or absent blocks, hard rejections on baseline inconsistencies, RESET_OUT
  never being checked, ACO rows collapsing, and failures invisible in the tables or the Excel export.
- **Biggest levers and risks for the next package** (§15): any baseline inconsistency
  **hard-rejects the whole run (HTTP 400)**. The RESET_OUT derivation (new in 371) **rejects the repo's
  own sample PDQ**. `DuplicateCheck` on DP IDs **never fails**. Many failures are
  **results-only** (no cell highlight). PDQ and FCT errors give the user **no reason**. There are
  several Excel cosmetic defects.

---

## 1. Domain primer (what the words mean)

| Term | Meaning in this code |
|---|---|
| **ADC file** (`*.adc`) | A configuration file for one board. It is either an **AEB** (Advanced Evaluation Board, a "DP" or detection point / counting head) or a **COM** (communication board, one per CAN segment). It is text made of `[BLOCK]` sections with `KEY bits:value // comment` lines. Its `[ID] ID` value is the board's numeric id: the **DP id** or the **COM id**. |
| **DP** | Detection point, which is an AEB. The display name is usually the comment on its `ID` entry (`ID 12:8 // DP1B`). |
| **Track section / FMA** | A track section is evaluated by one AEB as its **FMA1** or **FMA2** (`fmaId` 0/1, displayed 1-based as `fma_1_2`). Its counting heads are listed in `CFG_ZP_FMA1/2` blocks. |
| **Main vs combination (virtual/supervisor) track** | A main track is physical. A combination track is virtual: a supervisor made of main tracks. The PDQ's `Track Output` gives PHYSICAL->MAIN and VIRTUAL->COMBINATION, but the CHC derivation **does not trust it** and classifies by signed-set arithmetic (§7.4). |
| **CAN segment / chain** | A group of backplanes (`Bp`) linked by `CanConnections` in the FCT. Each has one COM. A redundant pair (MASTER/SLAVE or PRIMARY/SECONDARY) collapses to the lead COM with `redundantComPresent=true`. |
| **SLCT_TIMEOUT** | Index into `CFG_TIMEOUT`. In v2 it is a binary *physical/virtual* flag: `0` means the referenced DP is in the **same** CAN segment as the file's DP, `1` means a **different** segment (data arrives via COM forwarding). Detail tables display `CFG_TIMEOUT[idx].TIMEOUT_VALUE x 10`. |
| **DIR_INV** | Counting-head direction inversion (0/1). Track-section details split heads into `ch_*` (DIR_INV=0) and `i_ch_*` (DIR_INV=1). |
| **CHC / eChc** | Counting-head control. `CFG_CONTROL` blocks sit on a counting-head DP and reference the adjacent track(s). `eChc` (external CHC, YES/NO) comes from the PDQ DP table. |
| **ACO / IO-EXB** | Axle Counting Output on an IO extension board. In the ADC these are `CFG_SECTION_OUT` blocks, **two per card, by position**. DT-mode IO-EXBs do Data Transmission (`CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT`). |
| **Forwarding** (`CFG_FWRD_ACD`) | A COM forwards a DP's axle-count data to another COM over IP (socket, then dest IP, then COM). |
| **PDQ** | The project design questionnaire workbook (`.xlsx`, frozen **Ver14 / R14.1**). Sheets used: `PDQ`, `CQ-IR`, `Control table`, `Data transmission Inputs`. |
| **CQ-IR** | The PDQ sheet that maps each *Configuration Word* (ADC key) to a *Response*. It becomes `cqIrParameters`. |
| **FCT / `.fct2`** | The Frauscher Configuration Tool project archive, a ZIP whose `project.xml` describes the topology. |
| **tpf** | An optional file carrying `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE` and `CFG_TYPE_PRTCT`. There is **no tpf endpoint**: the FE sends its blocks inline in the v2 request (`@JsonAnySetter`). **[inferred]** The FE probably turns tpf XML into JSON through `/api/upload/translate`. |
| **AE** | Application Engineering, the users and the source of UAT feedback. They also own the mapping catalogs (e.g. `reset-out-mappings.properties`, "supplied by AE on 2026-08-24"). |

---

## 2. Tech stack, build, run, CI

- **Gradle 8.14**, **Java 21**, **Spring Boot 4.0.2** (web, validation, actuator), Jackson 2.15.4
  (+ xml, jsr310), **Apache POI 5.2.5** (xlsx), springdoc 2.8.6, Lombok. Tests use JUnit 5 and
  **Cucumber 7.15** (`cucumber-spring`, run through `junit-platform-suite`).
- Package root: `com.frauscher.ConfigurationValidationService` (capitalised, non-standard).
- Commands:
  - `./gradlew test`: everything, about 1.5 min cold. Reports go to `build/reports/tests/test/`;
    Cucumber HTML goes to `target/cucumber-reports.html`.
  - `./gradlew bootJar` then `java -jar build/libs/configuration-validation-service-1.0.0.jar`.
    It listens on **port 7443** (plain HTTP despite the number).
  - `/` redirects to `/swagger-ui.html`. Actuator serves `/actuator/health|info|metrics`.
- `application.properties`: logs go to `logs/configuration-validation-service.log` (rolling, 14 days).
  Multipart limits are 10 MB per file and 15 MB per request; Tomcat `max-part-count=4096` (many ADC files).
  `file.parser.extension=.adc`. `cors.allowed-origins=http://at-cvt01.frauscher.host:6443`. **This
  property is not read by any code; no CORS config class exists [verified].**
- CI is GitLab (`.gitlab-ci.yml` plus `.gitlab/jobs/gradle-test.yml`): `./gradlew test`, then docker build
  and push to `registry.gitlab.frauscher.app:5005`. The Dockerfile builds with `-x test` and runs on
  `eclipse-temurin:21-jre-alpine` with `SPRING_PROFILES_ACTIVE=prod` (no `application-prod.properties`
  exists). The upstream is GitLab `inv/pd/validation-tools/configuration-validation-service`; this
  GitHub repo (`ShreshthMi/CVT`) mirrors it. `README.md` is untouched GitLab boilerplate.
- Jackson: a custom `ObjectMapper` bean (`validation/config/JacksonConfig`) sets
  **`FAIL_ON_UNKNOWN_PROPERTIES=true`**. Any unknown JSON field in a request body is a 400
  (`INVALID_PAYLOAD`, "Invalid JSON in payload"). This matters for the FE: the report round-trip
  must send back exactly the shapes the service emits.

---

## 3. Repository map

```
controller/        REST endpoints (Phase 1: Upload, ConfigValidation, Report, ConfigOptions, Home;
                   Phase 2: PdqUpload, FctUpload, ConfigValidationV2)
dto/               request/response DTOs; dto/pdq (PdqUploadResponse, ControlTable, TrackSection, DpTableRow,
                   FadcAutoReset, DataTransmission...), dto/fct (ComAebMap, Chain, FctCom, FctAeb, EvaluatedFma, AcoIoExb)
model/             ParsedConfigFile/ConfigBlock/ConfigEntry (parsed ADC), RuleConfig, ValidationResult,
                   ValidationSummary, 8 detail-row models (all `Annotatable`), MismatchAnnotation
service/           ConfigParsingService (ADC upload), ConfigValidationService (Phase 1 engine driver),
                   ConfigValidationV2Service (v2 orchestrator), SummaryService (detail tables), ConfigOptionsService
service/extractors 8 detail-table extractors + ValueMappingService (raw -> display label)
service/pdq        PDQ workbook parsing (contract, header, CQ-IR, normalizer, control table, DT, project block)
service/fct        FCT archive + project.xml parsing
service/preprocessor  v2 expectation building: BaselineGate, BaselineIndex, BaselineInconsistencies,
                   Scalar/CountingHead/Supervisor/Aco/Control/IpSwitch/Forwarding/DataTransmission builders,
                   ResetOutMappingService, Expectations/InstancedExpectation/MatchMode
validation/        rule engine core (RuleType, ValidationRule, decision engine, payload validator, contexts)
validation/rules   9 Phase 1 rule implementations
validation/instanced  InstancedExpectationEvaluator, ForwardingDestinationResolver, InstancedFinding
validation/cluster    CanSegmentValidator (Cluster 1 / BE-08 verdict refinement + ORPHANED)
validation/annotation MismatchAnnotator (BE-07: result -> detail cell)
util/              CfgParserUtil (ADC text parser), ConfigExtractionUtil, XmlToJsonTransformer, ExcelSummaryUtil
util/excel         Excel workbook building (styles, headers, column names, freeze panes, data rows)
exception/         typed exceptions (each carries an external errorCode) + GlobalExceptionHandler
startup/           loads and validates ValidationConfiguration.json at boot (fail-fast)
resources/         ValidationConfiguration.json, value-mappings.properties, pdq-workbook.properties,
                   pdq-mappings.properties, reset-out-mappings.properties, openapi.yaml (stale)
```

Main code is about 13.6k Java lines plus about 1.6k lines of resources. The largest files are
`MismatchAnnotator` (772), `ExcelHeaderProcessor` (607), `InstancedExpectationEvaluator` (441) and
`ControlExpectationsBuilder` (325).

---

## 4. API surface and the expected FE flow

| Method & path | Body | Returns | Notes |
|---|---|---|---|
| `POST /api/upload/adcfiles` | multipart `files[]` | `List<ParsedConfigFile>` | Only `*.adc` (case-insensitive) kept; **others silently dropped**; sorted by filename. |
| `POST /api/upload/translate` | multipart `file` (`.xml`) | JSON string | XML to JSON, strips `AEB_`/`COM_` prefixes (Phase 1 helper; likely used for tpf). |
| `GET /api/configoptions` | n/a | `[{key, optionMappings:[{key,value}]}]` | From `value-mappings.properties`, for UI dropdowns. |
| `POST /api/config/validate` | `{parsedConfigFiles, userInput:{BLOCK:{ENTRY:value}}}` | `ValidationSummary` | **Phase 1.** UI-supplied expectations; strict payload checks. |
| `POST /api/upload/pdq` | multipart `file` (`.xlsx`) | `PdqUploadResponse` | Phase 2 baseline. |
| `POST /api/upload/fct` | multipart `file` (`.fct2`) | `ComAebMap` | Phase 2 baseline. |
| `POST /api/config/v2/validate` | `{parsedConfigFiles, userInput:{fctData, pdqData, <tpf blocks...>}}` | `ValidationSummary` (+ `id`, `_mismatches`) | **Phase 2.** Both `fctData` and `pdqData` are mandatory, else `PHASE2_INPUTS_INCOMPLETE`. |
| `POST /api/report/download` | the `ValidationSummary` JSON as returned | `.xlsx` bytes | The FE posts the summary back; the service renders Excel. |

**Expected v2 FE sequence [inferred from contracts]:** upload ADCs, upload PDQ, upload FCT, (tpf blocks),
v2 validate, render tables, download report. The service is **stateless**: nothing is stored
between calls, and the FE carries all intermediate JSON.

**`ValidationSummary` JSON** (`model/ValidationSummary`):
`validation_results[]`, `dp_details[]`, `track_section_details[]`, `chc_details[]`,
`supervisor_details[]`, `ioexb_behaviour_details[]`, `ioexb_aco_details[]`,
`data_transmission_details[]`, `ethernet_details[]`.
- A `ValidationResult` has `id` (v2 only, `"r0".."rN"`, omitted when null), `fileName`, `ruleType`,
  `blockName`, `entryKey`, `expectedValue`, `actualValue` and `status` (`PASS`/`FAIL`/`INVALID`).
- Each detail row may carry `_mismatches: [{field, index, kind: VALUE|UNEXPECTED|MISSING, expected,
  actual, result_id}]` (omitted when empty). See §9.

**Error contract** (`GlobalExceptionHandler`, body `{errorCode, message, timestamp}`):

| errorCode | HTTP | When |
|---|---|---|
| `INVALID_USER_INPUT` | 400 | Controller input checks; Phase 1 payload validation (`payload input required for X::Y` etc.) |
| `INVALID_PAYLOAD` | 400 | Malformed JSON / unknown field / missing multipart part |
| `PAYLOAD_TOO_LARGE` | 413 | Multipart limits |
| `FILE_PARSE_ERROR` | 400 | ADC read failure |
| `PDQ_INVALID` | 400 | **Any** PDQ parse/validation failure. The message is always "PDQ workbook is invalid"; the `PdqInvalidReason` goes **to logs only**. |
| `FCT_TAMPERED` / `FCT_INCOMPLETE_BASELINE` | 400 | FCT failures. The message is always "FCT archive is invalid"; the reason is logged only (`FctInvalidReason` maps to the code). |
| `PHASE2_INPUTS_INCOMPLETE` | 400 | v2 without fctData or pdqData |
| `PHASE2_CONTROL_TABLE_MISSING` | 400 | Gate: no track sections |
| `PHASE2_TRACK_RECONCILIATION_FAILED` | 400 | Gate: PDQ tracks vs FCT FMAs differ (message names NOT-FOUND/EXTRA) |
| `PHASE2_BASELINE_INCONSISTENT` | 400 | Gate (RSR_TYPE, duplicate track) **or** any accumulated builder/evaluator inconsistency (numbered list in `message`) |
| `RULE_CONFIGURATION_ERROR` / `ENGINE_ERROR` | 500 | Bad rules JSON / unexpected engine exception |
| `UNEXPECTED_ERROR` | 500 | Fallback (also catches `ReportGenerationException`, which has no dedicated handler) |

`openapi.yaml` documents **only the four Phase 1 endpoints [verified]**. The v2, PDQ and FCT endpoints
are undocumented there (springdoc still auto-generates live docs from the controllers).

---

## 5. Input parsing

### 5.1 ADC files: `util/CfgParserUtil`
- Lines are trimmed. Blank lines and `//` lines are skipped. A line `[...]` starts a new block
  **without reading its header text**. **A block's name is the key of its first entry.** Example:
  `[CFG_ZP_FMA1]` followed by `CFG_ZP_FMA1 8:0 // 1AXT2` gives name `CFG_ZP_FMA1`, whose "header
  value" is `0` and whose header comment is `1AXT2`. Code relies on this "header entry" (e.g. the
  track-section name is the header comment; the forwarding resolver matches header values).
- An entry is `KEY bits:value [// comment]`. A line that does not fit is **silently skipped**.
- Per block it sets `sequenceNumber` (file order), `totalOccurrence`, `start/endSequenceNumber` and
  **`blockIndex`** (0-based ordinal among same-named blocks; 0 when unique). `CFG_TIMEOUT` lookups use
  `blockIndex` as the SLCT_TIMEOUT index, and positional ACO uses it as the slot.
- File flags (drive rule scoping and extractor selection):
  - `trackSectionDetails`: has `CFG_ZP_FMA1` or `CFG_ZP_FMA2`
  - `acoIoexbDetails`: has **`CFG_AXCNT` or `CFG_SECTION_OUT`**
  - `dtIoexbDetails`: has `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT`
  - `comDetails`: has `CFG_MY_IP_NW1` (this is how a COM file is recognised)
- `id` = the integer value of `ID.ID` (0 if missing or non-numeric). This is the join key for every v2 lookup.

### 5.2 PDQ workbook: `service/pdq/*` (`POST /api/upload/pdq`)
- **Where things are** comes from `pdq-workbook.properties` (sheet names, header labels, Control-table
  column letters, DT column labels). AE can retarget it without code changes. **How values transform**
  comes from `pdq-mappings.properties` (step divisors, INTERVAL ms to code, GS06+ defaults). Both load
  fail-fast at boot.
- `PdqSheetHeaderParser`: `projectCode` (the cell right of "Project Code", blank gives `"0"`) and the
  AEB equipment version (Sl. No. **1.09**, e.g. `GS07`). `gs06Plus` is set when the GS number is 6 or more.
- `CqIrSheetParser`: finds the header row with *Configuration Word* and *Response*. Each visible row
  with a non-empty Configuration Word is "parametric" and gets normalised (`CqIrValueNormalizer`):
  - `IDENTIFICATION` "a to b" becomes `{min,max}` ints (VTF-350).
  - `TIMEOUT_VALUE` "x & y & ..." is step-divided (10) and padded to **8** values with `"0"`.
  - Everything else: split on `" & "`, take the value left of the first `:`, then apply the INTERVAL
    enum (`10/40/80/160 ms` to `0..3`) or the step divisor (must divide exactly, else `PDQ_INVALID`).
  - Output follows a **fixed template** of blocks and keys: `CFG_SECTION`, `CFG_RESET`, `CFG_SECTION_OUT`,
    `CFG_AXCNT`, `CFG_OCC`, `CFG_ZP`, `CFG_BEHAV_TGGL`, `CFG_TIMEOUT`, `CFG_SWITCH`,
    `CFG_SUPERVIS_FMA1/2`, `CFG_IP_SWITCH_TIME` and `CFG_RSR_TYPE`. Keys not in the template are dropped.
    The GS06+-only keys (`TYPE_AUX1/2`, `TYPE_IN1..3`, `SUPERVIS_COUNT_LMT`) are **never read from the
    sheet**; they are filled from `versionDefault.*` (all `0`) when GS06+, and omitted otherwise.
  - The template has **no `RESET_OUT` and no `BEHAV_INPUT3`**. Both are derived at validate time from
    the Control table (§7.4).
- `ProjectBlockResolver`: the CQ-IR `PROJECT_NUMBER` row. Response YES gives `BLOCK_EXISTS="true"` and
  `PROJECT_NUMBER` = Remarks (blank gives `"0"`). NO gives `"false"`, and Remarks must be empty. The
  result is appended as **both** `CFG_PROJECT_AEB` and `CFG_PROJECT_COM`.
- `ControlTableParser` (Ver14 layout: Track Sections cols **A–I**, DP table cols **K–N**; the class
  javadoc still says A–H/J–M):
  - It locates the "Track Section Name" header, then reads each sub-table until the serial column stops
    being an integer.
  - A `TrackSection` has `serialNo`, `name`, `dpIn[]` and `dpOut[]` (comma split), `resetType` (verbatim;
    **blank on a named row means `PDQ_INVALID/MISSING_RESET_TYPE`**, added in VTF-371), and `trackType`
    (PHYSICAL to MAIN, VIRTUAL to COMBINATION, anything else rejected).
  - `fadcAutoReset` takes its operands (comma, at most 8; NA/blank means null) from col G and its
    operator (OR/AND, or blank for a single operand) from col H "Logic type". `autoResetByTimer` is YES/NO.
  - A `DpTableRow` has `serialNo`, `name`, `position` (ABOVE/BELOW THE RAIL, otherwise rejected) and
    `eChc` (YES/NO).
- `DataTransmissionParser`: two side-by-side sub-tables located by header labels.
  - `dataSafetyLevels[]` holds `{dpName (receiver), safetyLevelIn 0–3, safetyLevelOut 0–3, safeOutFdbckQuad 0–1}`.
  - `outputDataTransmission[]` holds `{sourceDpName, nmbrOut 0–15, position 0–31}`.
  - The result is `null` when the sheet is absent or has headers only. The two lists are **index-paired
    at validate time**.
- Sample output (repo fixture `pdq-phase2-sample.xlsx`, run 2026-10-08): `projectCode "0"`, `GS07`.
  The Control table has 7 tracks (e.g. `1AXT1 dpIn[DP1A] dpOut[DP2A] resetType "RESTRICTED RESET WITH LV"
  MAIN fadc{OR,[1AXT2,SUP1-AXT1]}`) and 8 DP rows. DT is `null`.

### 5.3 FCT archive: `service/fct/*` (`POST /api/upload/fct`)
- `FctParsingService` checks the ZIP magic bytes. It rejects nested archives, entries over 20 MB, a
  total over 50 MB, and compression ratios over 50:1. It extracts the root entry named exactly
  `project.xml` (case-sensitive; the real fixtures use lowercase). Other entries (`trackplan/*.xml`,
  `type_protected.xml`, ...) are read only to enforce the caps, then discarded.
- `FctProjectXmlParser` uses a secure DOM (no DTD/XXE):
  1. It indexes `Aeb internId -> <Id>` and `Fma internId -> (name, fmaId, aebInternId)`.
  2. It groups `Bp` elements into CAN segments by BFS over `Parameter/CanConnections@next|@previous`.
     A dangling reference gives `FCT_INCOMPLETE_BASELINE`.
  3. Per segment it takes the `Children`: `Com`, `Aeb` and `IoExb` (others ignored). A segment with
     neither COM nor AEB (e.g. a power-only backplane) is dropped.
  4. COM resolution: one COM gives `redundant=false`. Two COMs forming a MASTER/SLAVE or
     PRIMARY/SECONDARY pair (VTF-359) collapse to the lead with `redundant=true`. Anything else gives
     `MULTI_COM_NO_REDUNDANCY`, and no COM gives `CHAIN_NO_COM`.
  5. Each `Aeb` becomes an `FctAeb{dpId=<Id>, dpName=@cpName, evaluatedFmas=[Fmas/Fma{name, fmaId, dpId}],
     acoIoExbs, dtIoExbCount}`. A duplicate AEB Id gives `DUPLICATE_ENTITY_ID`.
  6. An `IoExb` with `AxleCountingOutput` is ACO. `OutputFma1/2` (an FMA internId) resolves to name,
     fmaId and owning-AEB `dpId`; an unresolved one gives `FCT_TAMPERED`. With `DataTransmission` it
     counts as DT. Anything else gives `UNSUPPORTED_IOEXB_MODE`.
- Sample (`fct-phase2-aco.fct2`): 2 chains (COM100: 5 AEBs, COM200: 3 AEBs). AEB 8 `DP1B` evaluates
  `1AXT2` (fma 0) and `SUP1-AXT2` (fma 1), and hosts an ACO card with outputs 1AXT2@8 and 2AXT2@6.

---

## 6. Phase 1 rule engine (also the scalar half of v2)

**Config:** `src/main/resources/ValidationConfiguration.json`, 50 rules. It is loaded and validated at boot
(`startup/ValidationConfigurationLoader` + `validation/config/DefaultRuleConfigValidator`). Unknown
fields, unknown `RuleType`, duplicates `(type, block, entry)`, a missing `DefaultValue` on `*OrBlockNotFound`,
or RangeCheck without min<max abort startup. The fields are `RuleType`, `ConfigBlockName`, `ConfigEntryKey`,
`UIInputRequired` (Yes/No), `ValidateOnlyInFilesWith` (`TRACKSECTIONDETAILS|ACOIOEXBDETAILS|DTIOEXBDETAILS|COMDETAILS`),
`SkipComFile` (default **true** if absent), `DefaultValue`, and `min`/`max`.

**Driver:** `ConfigValidationService.runRules`:
1. Build the key universe = all configured `(block, entry)` keys ∪ every `(block, entry)` in the payload
   (except `BLOCK_EXISTS`).
2. For each file and each key, run `RuleExecutionEngine.execute`. For each configured rule on the key:
   skip if the file lacks the marker flag; `ValidationDecisionEngine` returns IGNORE when the marker
   does not apply or when `SkipComFile` is set and the file is a COM file; otherwise run the
   `ValidationRule` impl.
3. If no configured rule executed **and** no rule on that key has a marker, apply the **default rule**
   (`InputMatch`, SkipComFile=true) when the payload has a value. So unknown payload keys are still
   checked on every non-COM file.
4. Results are sorted by ruleType, then block, then entry (case-insensitive).
- Phase 1 uses `DefaultPayloadValidator.validate`, which enforces `UIInputRequired=Yes` presence,
  `{min,max}` for RangeCheck, lists for Optional/MultipleBlockMultiple, and boolean `BLOCK_EXISTS` for
  ProjectBlockCheck. v2 uses `resolve()` instead (lenient: a missing baseline value leaves the rule dormant).

**Rule semantics** (`validation/rules/*`, "actual" = all values of block.entry across all occurrences):

| RuleType | Behaviour |
|---|---|
| `InputMatch` | No payload: no result. No actual: FAIL `CONFIG_BLOCK_OR_PARAM_NOT_FOUND`. Otherwise PASS iff **every** occurrence equals the expected value. |
| `InputMatchOrBlockNotFound` | Block absent: result `CONFIG_BLOCK_NOT_FOUND`, which is PASS iff `DefaultValue == expected` (or no payload). Otherwise behaves like InputMatch. |
| `OptionalInputMatch` | The expected value is a set. PASS iff **any** actual is in the set (an empty actual passes only if the set contains the default). |
| `OptionalInputMatchOrBlockNotFound` | Block absent: PASS iff the default is in the expected set. Otherwise behaves like OptionalInputMatch. The expected value is printed as a Java `Set.toString()` (`[0]`). |
| `RangeCheck` | min/max from the payload `{min,max}` or from the rule. `Integer.parseInt(actual)` throws on a non-numeric value, giving **500 `ENGINE_ERROR`**. |
| `DuplicateCheck` | Asks `DuplicateValueRegistry.isDuplicate`. **Nothing ever calls `record()`**, so it **always PASSes** [verified at runtime with two files of ID 8]. The registry is also a singleton holding mutable state shared across concurrent requests. |
| `MultipleBlockSingleInputMatch` | All occurrences must have the entry and all must equal the expected value. (No configured rule uses this.) |
| `MultipleBlockMultipleInputMatch` | Actual ⊆ expected list (`CFG_TIMEOUT.TIMEOUT_VALUE`). |
| `ProjectBlockCheck` | Uses payload `BLOCK_EXISTS`. Expected absent but present: FAIL `PROJECT_BLOCK_NOT_EXPECTED`. Absent as expected: PASS. Expected but absent: FAIL `PROJECT_BLOCK_NOT_FOUND`. Otherwise compares `PROJECT_NUMBER`. |

**The 50 configured rules, in short:**

| Group | Rules |
|---|---|
| **ID** | `ID.ID` RangeCheck 1–4095 and DuplicateCheck (both on COM files too). |
| **TRACKSECTIONDETAILS files** | `CFG_SECTION` COMM_FAIL/BEHAV_GE/CLR_TRACK/RESET_IN (InputMatchOrBNF); RESET_OUT (OptionalOrBNF, dormant in v2 because CQ-IR never supplies it); `CFG_OCC` (OptionalOrBNF); `CFG_IP_SWITCH_TIME`, `CFG_SWITCH` x3 (OptionalOrBNF); `CFG_SUPERVIS_FMA1/2` RESET_TYPE/RESET_DELAY (InputMatchOrBNF); `CFG_ZP` x5 (InputMatchOrBNF). |
| **ACOIOEXBDETAILS files** | `CFG_AXCNT` BEHAV_INPUT1/2, TYPE_IN1..3 (InputMatchOrBNF), BEHAV_INPUT3 (OptionalOrBNF, dormant in v2), BEHAV_IOEXB (InputMatch); `CFG_SECTION_OUT` CLR_OCC/TYPE_AUX1/2/AUX1/2_OUT/AUX1/2_NO_NC (InputMatch). |
| **No marker** (every non-COM file) | `CFG_BEHAV_TGGL` x2, `CFG_RESET` x2 (InputMatchOrBNF); `CFG_TIMEOUT.TIMEOUT_VALUE` (MultipleBlockMultiple); `CFG_PROJECT_AEB` (ProjectBlockCheck); tpf blocks `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP` x4, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT` (InputMatch). |
| **COMDETAILS files** | `CFG_PROJECT_COM` (ProjectBlockCheck, SkipComFile=false). |

VTF-362 changed some of these. `CFG_OCC` and `CFG_SWITCH` were softened to Optional, and all of the
above were scoped to `TRACKSECTIONDETAILS` to remove "absence/broadcast FAIL noise" on files that
legitimately lack those blocks.

---

## 7. Phase 2 (v2) pipeline: `ConfigValidationV2Service.validate`

```
ValidationRequestV2 {parsedConfigFiles, userInput{fctData, pdqData, tpf...}}
   │  controller gate: files non-empty; fctData && pdqData else PHASE2_INPUTS_INCOMPLETE
   ▼
DefaultExpectationsPreprocessor.preprocess
   1. BaselineGate.check ── hard 400s: CONTROL_TABLE_MISSING / duplicate track name /
   │                        TRACK_RECONCILIATION_FAILED / RSR_TYPE mismatch-or-absent
   2. ScalarExpectationsBuilder → scalar bucket (block→entry→value)
   3. BaselineIndex.build(fct, controlTable, problems)
   4. instanced builders (all record problems, skip at their grain):
        CountingHead, Supervisor, Aco, Control(+BEHAV_INPUT3 +RESET_OUT), IpSwitch, Forwarding, DataTransmission
   5. problems.throwIfAny() ── one PHASE2_BASELINE_INCONSISTENT listing every problem
   ▼
scalar:     payloadValidator.resolve(scalar) → ConfigValidationService.validateParsedFiles (Phase 1 engine)
instanced:  InstancedExpectationEvaluator.evaluateAnnotated(files, instanced, problems)
            problems.throwIfAny()   ── forwarding socket→COM resolution failures also 400 the run
cluster 1:  CanSegmentValidator.apply(findings, fct, files) → refine verdicts + ORPHANED results
results  =  scalar ++ instanced ++ orphaned ; ids r0..rN assigned in that order
summary  =  SummaryService.generateSummary(files, results)      (8 detail tables, §8)
            MismatchAnnotator.annotate(summary, scalar, findings, files)   (§9)
```

### 7.1 Baseline gate (`BaselineGate`)
- PDQ track names (trimmed, case-sensitive) are reconciled as a **multiset** against the FCT track
  universe (every `EvaluatedFma.fmaName`; ACO output names excluded). Both NOT-FOUND and EXTRA are
  reported. A duplicate PDQ track name raises an immediate `BASELINE_INCONSISTENT`.
- `RSR_TYPE` must be present in the PDQ or the tpf. If both are present they must be equal.

### 7.2 Scalar bucket (`ScalarExpectationsBuilder`)
A copy of `cqIrParameters`. `IDENTIFICATION{min,max}` moves under `ID.ID` (for RangeCheck). tpf blocks
are merged with `putIfAbsent` (PDQ wins). It contains the `CFG_PROJECT_AEB/COM` blocks. It is then
validated by the **unchanged Phase 1 engine**, so every Phase 1 semantic and scoping rule in §6 applies.

### 7.3 Shared index (`BaselineIndex`, VTF-360)
Trim-normalised lookups: `trackByName`, `fmaByName` (first wins), `idOfDp(name)`, `chainOfDpId`,
`positionOfDp`, `eChcOfDp`, `hasAcoIoExb(dpId)` and `comIdOfChain`. A malformed id is recorded once
and skipped. Dependent lookups then miss and record their own reference-level problem.

### 7.4 Instanced derivations (what v2 actually checks per entity)

`InstancedExpectation(fileId, block, matchMode, linkedId, position, key, expectedValue, defaultValue)`.
The `fileId` is the ADC `ID` of the file it applies to. Same-chain gives `SLCT_TIMEOUT=0`, cross-chain gives 1.

| Builder | Block / mode | Emitted per | Identity -> checked values |
|---|---|---|---|
| `CountingHeadExpectationsBuilder` | `CFG_ZP_FMA{fmaId+1}` / BY_IDENTITY | every FCT EvaluatedFma (incl. supervisor FMAs), on its evaluating DP | `{ID: headDpId}` for each head in PDQ `dpIn ∪ dpOut`. `DIR_INV = (head∈dpOut) == (head BELOW THE RAIL) ? 0 : 1`; `SLCT_TIMEOUT`. |
| `SupervisorExpectationsBuilder` | `CFG_SUPERVIS_FMA{host.fmaId+1}` / BY_IDENTITY | each PDQ track with `fadcAutoReset` **and** an operator | `{ID: operand.dpId, SECTION: operand.fmaId}` for each operand. `LOGIC_TYPE` (OR=0/AND=1); `SLCT_TIMEOUT`. A single operand with no operator emits nothing. |
| `AcoExpectationsBuilder` | `CFG_SECTION_OUT` / **POSITIONAL** | each AEB hosting ACO cards, 2 slots per card in FCT order | Slot 1 = OutputFma1. Slot 2 = OutputFma2, or a **filler duplicate of OutputFma1**. Per slot: `ID` (output DP), `SECTION` (output fmaId), `SLCT_TIMEOUT`. One bad slot skips the whole host. |
| `ControlExpectationsBuilder` (CHC) | `CFG_CONTROL` / BY_IDENTITY | per counting-head DP | Tracks get signed vectors (+dpIn, −dpOut). A track is a **combination** iff its vector is a {0,1}-sum of ≥2 other tracks (subset search, bounded at 8); the rest are **main**. Over mains, a DP that is + for one and − for another is **middle** and gets 2 `CFG_CONTROL` (the 2 adjacent mains). A single-sign DP is **boundary**: with `eChc=YES` it gets one per owning main (VTF-361 junction case); with `eChc=NO` it gets none. Identity `{ID: refTrack.evalDp, SECTION: refTrack.fmaId}`, value `SLCT_TIMEOUT`. |
| (same, derived) | `CFG_AXCNT.BEHAV_INPUT3` / SINGLE | counting-head DP **that hosts an ACO IO-EXB** (VTF-362 M5) | `"7"` iff boundary+eChc=YES, else `"6"`. |
| (same, derived, **VTF-371**) | `CFG_SECTION.RESET_OUT` / SINGLE | each evaluating AEB (owner of FCT FMAs) | Control-table **Reset Type**, normalised (trim, upper, whitespace to `_`) and mapped by `reset-out-mappings.properties`: `RESTRICTED_RESET_WITH_LV=1`, `RESTRICTED_PREPARATORY_RESET_WITHOUT_LV=2`, `RESTRICTED_PREPARATORY_RESET_WITH_LV=3`. **All tracks evaluated by one AEB must share one Reset Type**, else a baseline inconsistency. An unmapped type is also an inconsistency. |
| `IpSwitchExpectationsBuilder` | `CFG_IP_SWITCH.IP_SWITCH` / SINGLE on COM | each chain | Redundant chain: mandatory `1`. Otherwise optional `0` with default `0`. |
| `ForwardingExpectationsBuilder` | `CFG_FWRD_ACD` / BY_IDENTITY on home COM | each cross-chain (track, head) pair, deduped | `{CAN_TX_ID: headDpId, DEST_COM: consumingComId}`. A one-hop obligation: the head's home COM must forward to the consuming track's COM. |
| `DataTransmissionExpectationsBuilder` | `CFG_DATA_OUT` / BY_IDENTITY on receiver | each PDQ DT row pair (index-paired) | `{ID: sourceDpId}` gives `SLCT_TIMEOUT`. A list-length mismatch skips the whole slice. **The DT payload proper (safety levels, NMBR_OUT, POSITION) is not validated anywhere ("BE-14 / Cluster 6", not built).** |

### 7.5 Evaluation (`InstancedExpectationEvaluator`)
- Expectations are grouped by `(fileId, block)`. **A `fileId` with no uploaded ADC is silently skipped**
  (a partial upload gives partial results with no warning).
- **SINGLE** mirrors InputMatch, or OptionalInputMatchOrBlockNotFound when `defaultValue != null`.
- **BY_IDENTITY** uses strict set-equality. An expected member with no matching occurrence gives FAIL
  `EXPECTED_OCCURRENCE_NOT_FOUND` (kind MISSING). An occurrence matching no member gives FAIL
  `UNEXPECTED_OCCURRENCE` (kind UNEXPECTED). For each matched member, each key is compared and gives
  a VALUE result. The `entryKey` looks like `DIR_INV[ID=8]`. ruleType is `IdentitySetMatch`.
- **POSITIONAL** sorts occurrences by `blockIndex`. Slot i is compared key by key (`ID[pos=0]`, ...).
  Missing slots are MISSING and extra blocks UNEXPECTED. ruleType is `PositionalMatch`.
- **CFG_FWRD_ACD** goes through `ForwardingDestinationResolver`. Each actual block's `INT_ID_DEST`
  socket maps to `CFG_INT_ID_DEST_NW1` (header = socket+32), giving the dest IP, then the COM whose
  `CFG_MY_IP_NW1` equals it. **NW2 (socket+48) must resolve to the same COM** (strict, VTF-360 D4).
  Unresolvable members are recorded as baseline problems, which **reject the run** after evaluation.
  The result is set-equality on `CAN_TX_ID|DEST_COM`.

### 7.6 Cluster 1 verdicts (`CanSegmentValidator`, VTF-338)
- In-scope blocks are `CFG_ZP_FMA1/2`, `CFG_SECTION_OUT`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1/2` and
  `CFG_DATA_OUT`.
- A failing `SLCT_TIMEOUT` VALUE becomes status **`INVALID`**, with actual = `INVALID_SCOPE` (raw 2–7)
  or `INVALID_VALUE`.
- An UNEXPECTED occurrence whose `linkedId.ID` is not an FCT AEB gets actual = `FILE_NOT_FOUND`.
- Each uploaded non-COM ADC whose ID is not an FCT AEB produces a new result: ruleType `IdentityLookup`,
  `IDENTIFICATION.ID`, actual `ORPHANED`, FAIL. It has no cell.

---

## 8. Detail tables (`SummaryService` + `service/extractors/*`)

They are built identically for Phase 1 and v2 from the uploaded ADCs only (never the baseline). Values
are mostly **display-mapped** through `value-mappings.properties` (`"1" -> "1 - reset"`). Timeouts are
displayed as `CFG_TIMEOUT[SLCT_TIMEOUT].TIMEOUT_VALUE x 10`. FMA is displayed as `SECTION+1`
(`ConfigExtractionUtil.fmaFromSection`, made null-safe in 371).

| Table (JSON key / Excel sheet) | Files | Row grain | Columns (source) |
|---|---|---|---|
| `dp_details` / DP Details | non-COM | 1 per file | dp_can_id, dp_name (ID comment), time_out[] (all CFG_TIMEOUT x10), comm_fail/behav_ge/clr_track/reset_in/reset_out (CFG_SECTION), behav_reset/behav_simul (CFG_BEHAV_TGGL) |
| `track_section_details` | trackSectionDetails | 1 per (file, FMA1/FMA2 present) | ts_name (first block's header comment), e_dp_id/name, fma_1_2, ch_* / i_ch_* arrays split by DIR_INV (dp_id, dp_name from ID comment, slct_timeout) |
| `chc_details` | non-COM **with CFG_ZP and CFG_CONTROL** | 1 per file, **2 fixed slots** | ts_name_n (ID comment), timeout_n, dp_id_n, dp_name_n (lookup by id), fma_dtl_n; interval (mapped), supervis/system/partial_count (raw) |
| `supervisor_details` | non-COM | 1 per (file, SUPERVIS_FMA1/2 present), sorted by dp_id | sup_name (header comment), sup_by_ts[] / dp_id[] / dp_name[] / fma[] / time_out[] / logic_type[] (per block); reset_type, reset_delay (first block); auto_reset_type, reset_timer (CFG_AUTORESET_FMA*) |
| `ioexb_behaviour_details` | acoIoexbDetails **with CFG_AXCNT** | 1 per file | behav_input1..3, type_in1..3, behav_ioexb, type_ioexb; coop reset (CFG_COOP_RESET) |
| `ioexb_aco_details` | acoIoexbDetails | **1 per CFG_SECTION_OUT block, with `slot`** (VTF-371; header-comment dedup removed) | slot, aco_fma1 (header comment), clr_occ, type_aux1/2, aux1/2_out, aux1/2_no_nc, fma_1_2, time_out |
| `data_transmission_details` | dtIoexbDetails | 1 per index-paired (SAFETY_LEVEL[i], DATA_OUT[i]) | safety levels (mapped), source_dp_id/name, timeout, nmbr_out, position |
| `ethernet_details` | COM | 1 per file | com, id, ip_nw_1/2, subnet_mask_1/2, dest_ip_nw_1/2[], fwrd_acd_to_dp_ids[] / dtls[] (CAN_TX_ID value/comment), interval |

---

## 9. Cell annotation (`MismatchAnnotator`, BE-07, heavily extended in 371)

The annotator runs after the summary is built. It mutates rows in place by appending `MismatchAnnotation`s.
The FE (and, since 371, the Excel export) highlights the named cell. `result_id` links the cell to its log entry.

**Instanced findings to cells:**

| Block | Table.column | What is annotated |
|---|---|---|
| `CFG_ZP_FMA1/2` | track_section_details | SLCT_TIMEOUT VALUE on `ch_slct_timeout[i]` / `i_ch_slct_timeout[j]`. UNEXPECTED on `ch_dp_name` / `i_ch_dp_name`. MISSING **appends an empty element** to all three parallel arrays (side chosen by expected DIR_INV). **DIR_INV VALUE is results-only.** No row for that FMA means results-only. |
| `CFG_SUPERVIS_FMA1/2` | supervisor_details | LOGIC_TYPE on `logic_type[i]`, SLCT_TIMEOUT on `time_out[i]`, UNEXPECTED on `sup_by_ts_dp_name[i]`. MISSING appends to all 6 arrays (first row of the file). |
| `CFG_CONTROL` | chc_details | `timeout_1/2`, `dp_name_1/2`. MISSING fills an empty slot; when both slots are full it is results-only. |
| `CFG_SECTION_OUT` | ioexb_aco_details | Row = the file's rows at index `position`. SECTION on `fma_1_2`, SLCT_TIMEOUT on `time_out`, UNEXPECTED on `aco_fma1`. **ID is results-only.** |
| `CFG_AXCNT.BEHAV_INPUT3` | ioexb_behaviour_details.behav_input3 | VALUE only |
| `CFG_SECTION.RESET_OUT` (371) | dp_details.reset_out | VALUE only |
| `CFG_DATA_OUT` | data_transmission_details | `timeout`, `source_dp_name`. MISSING is results-only. |
| `CFG_FWRD_ACD` | ethernet_details | UNEXPECTED on `fwrd_acd_to_dp_dtls[i]` (re-resolves actual forwards for the index). MISSING appends an empty element to both arrays. |
| `CFG_IP_SWITCH` | none | results-only |

**Scalar results to cells (371, routed by the result's `fileName`, then the row's `dp_id`):**
- The `CFG_SECTION_OUT` aux keys go to `ioexb_aco_details` (every row of the file whose display value differs).
- `CFG_SUPERVIS_FMA*` RESET_TYPE/RESET_DELAY go to `supervisor_details` (row chosen positionally,
  FMA1 before FMA2).
- `CFG_ZP` INTERVAL/SUPERVIS_COUNT/SYSTEM_COUNT/PARTIAL_COUNT go to `chc_details`.
- **Every other scalar FAIL is results-only.** That includes all `CFG_SECTION` checks except RESET_OUT,
  `CFG_BEHAV_TGGL`, `CFG_RESET`, `CFG_OCC`, `CFG_SWITCH`, `CFG_TIMEOUT`, RSR/tpf, project blocks,
  `CFG_AXCNT` BEHAV_INPUT1/2/IOEXB/TYPE_*, and ID.
- Display rules: `DISPLAY_MAPPED_ENTRIES` (CLR_OCC, TYPE_AUX*, AUX*, RESET_TYPE, INTERVAL) map
  raw values to labels so a tooltip matches its cell. A `[a, b]` set payload is split and mapped per
  token. The engine's verdict is reused, never re-compared.

---

## 10. Excel report (`util/ExcelSummaryUtil` + `util/excel/*`)

- The sheets are "Validation Results" (with a summary row `PASS: n, FAIL: m`) followed by one sheet per
  **non-empty** detail list, in the order of §8. Columns come from **reflection over the model's
  declared fields** (not JSON names). The order follows `@JsonPropertyOrder` when present, else
  declaration order. `mismatches` is excluded (371).
- Headers come from `ExcelColumnMapper` (display names keyed by **Java field name**). Grouped two-row
  headers come from `ExcelHeaderProcessor`. Lists are newline-joined in one cell.
- Styling:
  - Validation Results rows with `FAIL` are dark grey.
  - Detail cells named by a `_mismatches` entry get kind-specific fills (371): VALUE red, MISSING
    amber, UNEXPECTED blue. An array cell is highlighted whole.
- **Defects visible in the exported report:**
  - **[verified]** The `id` field is a column with the raw header "id". Its header mapping key is
    missing, so it falls back to the field name.
  - **[verified]** The block column header reads "block Name": the mapping key is `configBlock` but
    the field is `blockName`.
  - Ethernet mapping keys `comId` don't match the fields `com`/`id`.
  - `INVALID` results are neither styled as failures nor counted in the summary row (only PASS/FAIL).
  - Results are ordered scalar (sorted), then instanced (emission order), then ORPHANED.

---

## 11. Tunable resources (AE-editable without a rebuild of code logic, but they ship in the jar)

| File | Controls |
|---|---|
| `ValidationConfiguration.json` | Which scalar rules run, where, and how lenient they are (§6). Overridable path: `validation.configuration.path` (`file:` supported). |
| `value-mappings.properties` | Raw-to-display labels for detail tables, plus `/api/configoptions` dropdowns (also `min`/`max`/`step`/`description` metadata). |
| `pdq-workbook.properties` | PDQ sheet names, header labels, Control-table column letters, DT labels. |
| `pdq-mappings.properties` | CQ-IR step divisors, INTERVAL enum, GS06+ defaults. |
| `reset-out-mappings.properties` (371) | Control-table Reset Type to RESET_OUT code (3 entries today; AE may extend). |

---

## 12. Ticket timeline (from git; the first commit squashes earlier history)

| Ticket | Date | What landed |
|---|---|---|
| VTF-331 (BE-01) | pre-history | PDQ CQ-IR parsing (`cqIrParameters`, project code, AEB version) |
| VTF-332 (BE-02) | Jun 5–16 | Control table + DT parsing; adapted to **frozen PDQ Ver14** |
| VTF-333 | Jun 5 | FCT `.fct2` upload, archive caps, `project.xml` to `ComAebMap` |
| VTF-334 | Jun 8 | `POST /api/config/v2/validate`, coupled-artifacts gate, preprocessor seam |
| VTF-350 | Jun 23 | PDQ IDENTIFICATION as numeric `{min,max}` |
| VTF-335 (BE-05) | Jun 23–24 | Expectations carrier, baseline gate, scalar bucket, 6 instanced derivations (M5 #1–#6) |
| VTF-336 (BE-06) | Jun 24 | Instanced evaluator (SINGLE / BY_IDENTITY / POSITIONAL), forwarding socket-to-COM resolution |
| VTF-337 (BE-07) | Jun 29 | Result ids + `_mismatches` + `MismatchAnnotator` |
| VTF-338 (BE-08) | Jul 1 | Cluster 1 named verdicts (ORPHANED, FILE_NOT_FOUND, INVALID_SCOPE/VALUE) + `CFG_DATA_OUT` ID/SLCT_TIMEOUT |
| **VTF-359** (UAT) | Jul 22 | Accept PRIMARY/SECONDARY redundant COM pair |
| **VTF-360** (UAT) | Jul 22 | Accumulate *all* baseline inconsistencies into one 400; shared `BaselineIndex` (trim fix, per-entry tolerance); per-member forwarding resolution; strict NW2 mirror |
| **VTF-361** (UAT) | Jul 22 | Junction head with eChc=YES gets one `CFG_CONTROL` per owning main track (ABS AD01A/AU09A layout, user-confirmed 2026-07-06) |
| **VTF-362** (UAT) | Jul 22 | Scope and soften scalar rules (§6); BEHAV_INPUT3 only on ACO-IO-EXB DPs; `CFG_PROJECT_COM` only on COM files |
| **VTF-371** (UAT) | Aug 24–25 | Report round-trip fix (`@JsonCreator` on ValidationResult/MismatchAnnotation, which had silently dropped 4 columns); `_mismatches` removed from Excel columns; **per-cell mismatch styling in Excel**; scalar SUPERVIS/CFG_ZP results routed to cells **keyed by file** (stopped cross-DP broadcast); null-safe SECTION to FMA; **MISSING_RESET_TYPE** PDQ rejection; **RESET_OUT derived from Control-table Reset Type** (was never checked); ACO: **one row per CFG_SECTION_OUT block with `slot`** (old dedup lost 5/118 blocks in a production package) and positional finding to slot-row join |

The pattern across the UAT tickets is that AE ran real project packages. The feedback fell into
**(a)** false FAILs from rules broadcast to files that don't carry a block, **(b)** whole-run 400s on
baseline quirks, **(c)** expected checks silently missing, and **(d)** failures not visible where users
look (cells, Excel).

---

## 13. Tests

- **JUnit** (157 tests) covers PDQ/FCT parsers against real fixture files, every preprocessor builder,
  the gate, the index, the evaluator, forwarding, the annotator (19), CanSegmentValidator, the Excel
  util and the report round-trip, plus MockMvc controller tests.
- **Cucumber** (131 scenarios under `src/test/resources/cucumber/features`): rules, engine, extractors,
  PDQ/FCT upload, and one v2 happy path.
  - **Caveat [verified]:** the Phase 1 *rule* features (`rules/*.feature`, `ValidationSteps.executeValidation`)
    run a **test-local re-implementation of the rule logic**, not the production `validation/rules/*`
    classes. That is why DuplicateCheck "passes" its duplicate scenario while production never detects
    duplicates. `EngineSteps` and `ExtractorSteps` do autowire real beans.
- **Fixtures:**
  - `fixtures/pdq-phase2-sample.xlsx` (+ `-dtio.xlsx`).
  - `fct-phase2-{aco,redundant,dt,duplicate-id}.fct2`.
  - **There are no real ADC packages in the repo.** v2 tests use synthetic JSON or `.adc` snippets.
  - `PdqFixtures.withConsistentResetTypes` **rewrites the sample PDQ's Reset Types** so it passes
    the 371 RESET_OUT gate (see §15 R1).
- `src/test/resources/config-and-payload-validation.feature` sits outside `cucumber/features`, so it
  **does not run**.

---

## 14. What was run on 2026-10-08 (reproducible)

1. `./gradlew test`: BUILD SUCCESSFUL, 288/288.
2. `bootJar`, then the jar on :7443. The PDQ and FCT fixtures were uploaded via curl; the shapes in
   §5.2/§5.3 come from this run.
3. v2 validate with the raw sample PDQ, `fct-phase2-aco.fct2` and two hand-written ADCs gave **HTTP 400
   `PHASE2_BASELINE_INCONSISTENT`**: "AEB 5 evaluates track sections with differing Reset Types:
   '1AXT1' = RESTRICTED RESET WITH LV, 'SUP1-AXT1' = RESTRICTED PREPARATORY RESET WITH LV ...", and
   the same for AEB 8.
4. The same call with all Reset Types forced equal gave 51 results (27 PASS / 24 FAIL), correctly shaped:
   - The track_section row was built.
   - DIR_INV FAILs carried **no** cell annotation (results-only as designed).
   - `CFG_RSR_TYPE.RSR_TYPE` FAILed on the AEB file (`InputMatch`, no marker, block absent).
   - `CFG_ZP.INTERVAL` FAILed with `CONFIG_BLOCK_NOT_FOUND` because DefaultValue 3 ≠ PDQ 2.
5. Two files sharing `ID 8` both gave DuplicateCheck PASS.
6. `/api/report/download` with that summary returned a valid xlsx (sheets: Validation Results, DP Details,
   Track Section Details, Ethernet Details) with headers `id`, `FILE NAME`, `RULE TYPE`, **`block Name`**, ...

---

## 15. Risks, gaps, likely UAT hot-spots (prioritised input for planning)

**R1. RESET_OUT derivation can block entire runs [verified].** `ControlExpectationsBuilder.addResetOut`
groups **all** tracks an AEB evaluates, including supervisor or combination FMAs such as `SUP1-AXT2`,
and requires a single Reset Type. The repo's own sample PDQ violates this, and real projects likely do
too. Open domain question for AE: should combination/supervisor tracks be excluded? Should RESET_OUT be
checked per FMA? Or should a conflict be a per-file FAIL rather than a whole-run 400? Also, only 3
Reset Types are mapped.

**R2. Whole-run hard rejection model.** The gate, every builder problem (VTF-360) and every forwarding
resolution failure turn the response into a 400 with a long numbered `message` and **zero results**.
UAT users with one bad reference lose the whole report. A "degrade to per-file FAIL/INVALID + continue"
mode is a likely ask. Forwarding problems come from the *ADC* side (COM files), not the baseline, yet
they still reject.

**R3. Opaque PDQ/FCT errors.** `PDQ_INVALID` and `FCT_*` messages are generic. `PdqInvalidReason` and
`FctInvalidReason` (with row/cell detail) only reach server logs, so AE cannot see *which* cell broke
the upload.

**R4. DuplicateCheck is dead [verified].** No `record()` call exists, so duplicate DP/COM IDs are never
flagged. Fixing it means populating per request, which also requires removing the singleton mutable
registry (a thread-safety issue).

**R5. Failures with no cell (results-only).** These are DIR_INV, ACO `ID`, `CFG_IP_SWITCH`, MISSING
without a slot (CHC both slots full, DT, FMA row absent), ORPHANED, and every scalar FAIL except the
routed ones (§9). Most DP Details columns (COMM_FAIL, BEHAV_GE, CLR_TRACK, RESET_IN, BEHAV_RESET/SIMUL,
TIMEOUTS) and IOEXB behaviour columns other than BEHAV_INPUT3 **are never highlighted**, even though a
column exists for each.

**R6. Remaining scalar noise.**
- `CFG_SECTION_OUT` aux rules run on any `acoIoexbDetails` file, and that flag is also set by `CFG_AXCNT`
  alone. A CFG_AXCNT-only file therefore gets 7 FAILs (acknowledged in `MismatchAnnotator.noCell`).
- `CFG_RSR_TYPE`, the tpf InputMatch rules, `CFG_BEHAV_TGGL`, `CFG_RESET` and `CFG_TIMEOUT` have no
  marker, so they run on every non-COM file.
- `InputMatchOrBlockNotFound` FAILs whenever the PDQ value ≠ the rule's `DefaultValue` and the block is
  absent (e.g. CFG_ZP INTERVAL).
- These are possibly correct, but they are exactly the VTF-362 class of complaint.

**R7. Silent partial coverage.**
- Instanced expectations for DP/COM ids with no uploaded ADC are skipped without any result. A missing
  file is not reported, unlike ORPHANED, which covers the reverse case.
- Non-`.adc` uploads are silently dropped.
- Unparseable ADC lines are silently skipped.

**R8. Data Transmission is barely validated.** Only `CFG_DATA_OUT` ID/SLCT_TIMEOUT is checked; safety
levels, NMBR_OUT and POSITION are not ("BE-14", unbuilt). On the display side, `DataTransmissionExtractorService`
resolves `timeout` and `source_dp_name` by scanning **all** files (the first `CFG_TIMEOUT` with that
blockIndex in *any* file), not the current file. The cell can therefore show another DP's timeout,
while the annotator's tooltip uses the file-scoped value.

**R9. Extractor fragility.**
- In `TrackSectionExtractor`, `ts_name` is the first block's header comment. `ch_dp_id` and `ch_dp_name`
  filter out empty values but `ch_slct_timeout` does not, so the parallel arrays can misalign. The DP
  id/name fall back to the first ID block.
- CHC shows only 2 slots and requires `CFG_ZP`.
- Ethernet forwarding arrays skip blocks with an empty `CAN_TX_ID`, while the annotator indexes by
  resolver order **[inferred]**, so the two can drift.

**R10. Excel/report cosmetics [verified].** The raw `id` column, the "block Name" header, unmapped
Ethernet headers, INVALID not styled or counted, mixed result ordering, and a `System.err` in
`configureWorkbook`.

**R11. Robustness.** RangeCheck on a non-numeric `ID` gives 500 `ENGINE_ERROR`. `ReportGenerationException`
has no handler, so it surfaces as `UNEXPECTED_ERROR`. The exception constructors log at ERROR on
every throw (noisy logs; `log.error(message, errorCode)` misuses the varargs).

**R12. Docs/dev-ex.**
- `openapi.yaml` is Phase 1 only and `README.md` is boilerplate.
- The design docs referenced throughout (§16) are not in git.
- The CORS property is unused.
- The ControlTableParser javadoc is stale.
- The Phase 1 rule BDD tests the wrong code (§13).
- No real-world ADC fixtures exist, so there is no regression net for UAT packages.

**Open questions to settle with AE before planning:**
1. What is the actual UAT feedback list? It is not in the repo. Map each item to R1–R12 or new work.
2. RESET_OUT scope (R1): which tracks count, and is a conflict a FAIL or a rejection?
3. Rejection vs. degraded results (R2): which inconsistencies must still block?
4. Is Phase 1 (`/api/config/validate`) still used by the FE, or can its constraints be relaxed for v2?
5. Should "expected but not uploaded" files be reported (R7)?
6. Should the DT payload validation (BE-14) be in this package?
7. Can AE share an anonymised real package (ADC set + PDQ + FCT) to commit as a regression fixture?

---

## 16. Referenced design documents (not in the repo; `docs/` is gitignored)

`v2-expectations-contract.md` (§1–§6: gate, scalar/instanced buckets, derivations),
`fcvt-phase2-design.md` (§2 coupled gate, §5 FCT, §6 PDQ, §8.1 Cluster 1),
`vtf-360-design.md` (accumulate-and-reject model), `FCVT-v2-Validation-Response-Contract.md` (§2/§4/§5:
result ids, `_mismatches`), `vtf-335-scope.md`, `pdq-upload-contract-v1.4.wiki`,
`input-artefacts-pdq-workbook.wiki`, `pdq-sample-data-gaps.md` (open item A3 = Reset Type catalog).
A future session should ask for these if a decision depends on contract wording.

---

## 17. Quick "where do I change X" index

| Change | Start here |
|---|---|
| A scalar rule's scope or leniency | `ValidationConfiguration.json` (+ `DefaultRuleConfigValidator` if adding fields) |
| A new per-entity check from PDQ/FCT | new `*ExpectationsBuilder` + wire into `DefaultExpectationsPreprocessor`; evaluator handles SINGLE/BY_IDENTITY/POSITIONAL generically |
| Reject vs continue on baseline problems | `BaselineInconsistencies.throwIfAny` call sites (`DefaultExpectationsPreprocessor`, `ConfigValidationV2Service`), `BaselineGate` |
| Error detail to the user | `PdqInvalidException` / `FctInvalidException` constructors + `GlobalExceptionHandler` |
| Highlight a new column | `MismatchAnnotator.dispatchInstanced` / `dispatchScalar` (+ display transform) |
| Detail-table content | `service/extractors/*` (+ `ExcelColumnMapper`, `ExcelHeaderProcessor` for Excel) |
| PDQ layout drift | `pdq-workbook.properties` first, then `service/pdq/*` |
| Reset Type catalog | `reset-out-mappings.properties` |
| Display labels / dropdowns | `value-mappings.properties` |
