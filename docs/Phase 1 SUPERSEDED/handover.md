# FCVT — Phase 1 Handover (as of VTF-305)

**Frauscher Configuration Validation Tool — backend service.**
This document is the single-source briefing for Claude (web) to author technical documentation for Phase 1.
It captures everything material about the codebase, domain, architecture, APIs, validation rules, completed tickets, known issues, and Phase 2 hooks, current to the develop branch at the close of VTF-303 / opening of VTF-305.

---

## 0. How to Use This Document

- **Audience**: Claude (web) writing user-facing technical docs, runbooks, architecture pages, or onboarding material.
- **Authority**: This file overrides any older handover snippets if they conflict.
- **Source of truth for code**: the develop branch. This document summarises but does not replace `git log` / actual source.
- **Companion docs** in [docs/](.):
  - [FCVT_HANDOVER.md](FCVT_HANDOVER.md) — earlier (longer) architectural reference; structure largely unchanged but pre-VTF-300.
  - [split-ioexbdetails-flag.md](split-ioexbdetails-flag.md) — VTF-297 design note for the ACO/DT flag split.
  - [validation-engine-issue-list.md](validation-engine-issue-list.md) — Feb 2026 bug-hunt classification (pre-fix).
  - [validation-results-analysis.md](validation-results-analysis.md) — analytical write-up of validation outcomes.
  - [frontend-payload-issues.md](frontend-payload-issues.md) — frontend/backend payload contract notes.

---

## 1. Product Context

### What the tool does

FCVT (Frauscher Configuration Validation Tool) is a web application that validates Frauscher FAdC (Frauscher Advanced Counter) `.ADC` configuration files against project-specific rules. A safety engineer:

1. Uploads a batch of `.ADC` files (typically one per board, e.g. AEB, IOEXB, COM).
2. Enters expected values for project-dependent parameters via UI dropdowns.
3. Receives PASS/FAIL results per rule and an Excel report containing summarised hardware detail sheets.

### Phase 1 scope (this document)

Phase 1 is **single-file `.ADC` validation** driven by `ValidationConfiguration.json`. It includes:

- Parsing `.ADC` files and classifying each as AEB / IOEXB-ACO / IOEXB-DT / COM via boolean flags.
- 41 declarative validation rules across 9 rule types.
- 8 detail-extractor services that build summary sheets per hardware concern.
- Excel report generation.
- Stateless round-trip architecture (no database).

### Phase 2 scope (out of scope here, but referenced)

- `.fct2` archive ingestion (project-level files containing multiple `.ADC`).
- Baseline FCT and PDQ (CQ-IR + Control Table) ingestion and cross-correlation.
- See [project_phase2_inputs.md](../memory/project_phase2_inputs.md) and [project_fct_parser_analysis.md](../memory/project_fct_parser_analysis.md) in the project-memory tree for the analysis already done.

### Deployed environment

- Host: `at-cvt01.frauscher.host`.
- Spring Boot app on port `7443`; nginx reverse proxy on port `443` (HTTPS) and `6443` (frontend origin).
- nginx requires `client_max_body_size 50m;` to match Spring's increased POST limit (VTF-292).

---

## 2. Repository Layout

```
configuration-validation-service/
├── src/
│   ├── main/
│   │   ├── java/com/frauscher/ConfigurationValidationService/
│   │   │   ├── ConfigurationValidationServiceApplication.java
│   │   │   ├── constants/                — ValidationConstants (shared strings)
│   │   │   ├── controller/               — 5 REST controllers
│   │   │   ├── dto/                      — API request/response wrappers (4)
│   │   │   ├── exception/                — Exception hierarchy + global handler (7)
│   │   │   ├── model/                    — Domain entities and detail models (16)
│   │   │   ├── service/                  — Orchestration services (4)
│   │   │   │   └── extractors/           — Per-concern detail extractors (9)
│   │   │   ├── startup/                  — Boot-time rule loading + validation (2)
│   │   │   ├── util/                     — Parsing, extraction, XML transform (4)
│   │   │   │   └── excel/                — Excel generation helpers (6)
│   │   │   └── validation/               — Validation engine
│   │   │       ├── config/               — Jackson, rule-config validator (3)
│   │   │       ├── context/              — Execution context bundles (5)
│   │   │       ├── engine/               — Rule execution orchestrator (2)
│   │   │       ├── payload/              — User input resolution (3)
│   │   │       ├── rules/                — 9 ValidationRule implementations
│   │   │       └── spec/                 — Decision engine + file applicability (4)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── openapi.yaml
│   │       ├── ValidationConfiguration.json
│   │       └── value-mappings.properties
│   └── test/
│       ├── java/.../cucumber/stepdefs/   — Cucumber step definitions (4 + 3 helpers)
│       └── resources/cucumber/features/  — 24 feature files (engine, extractors, rules)
├── docs/                                 — Project documentation (this folder)
├── build.gradle, settings.gradle
├── Dockerfile
├── .gitlab-ci.yml
└── gradle/wrapper/                       — Gradle 8.14 wrapper
```

- **92 main Java source files**, **10 test Java files**, **24 Cucumber `.feature` files** (as of VTF-303 branch + VTF-305 prep).

### Build / run

| Command | Action |
|---|---|
| `./gradlew build` | Compile + tests + assemble jar |
| `./gradlew test` | Run Cucumber + unit tests |
| `./gradlew bootRun` | Start app on port 7443 |
| `docker build -t fcvt .` | Build container image |

### Technology stack

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

---

## 3. Architecture — Detailed Account

This section is the authoritative deep walk-through of how a request moves through the system, what every class does, and what data flows between layers. Each subsection describes one path: deployment topology → startup → upload → validate → report → config-options → cross-cutting concerns.

### 3.1 Deployment topology

```
                          ┌─────────────────────────────┐
                          │       Frontend (SPA)        │
                          │  at-cvt01.frauscher.host    │
                          └─────────┬───────┬───────────┘
              Upload .ADC (multipart)│       │ Validate / Report (JSON)
                                     ▼       ▼
                        ┌────────────────────────────┐
                        │    nginx reverse proxy     │
                        │     443 → 7443             │
                        │  client_max_body_size 50m  │
                        └─────────────┬──────────────┘
                                      ▼
                        ┌────────────────────────────┐
                        │   Spring Boot (port 7443)  │
                        └────────────────────────────┘
```

- **Frontend**: SPA hosted at `http://at-cvt01.frauscher.host:6443` — added to the CORS allow-list in `application.properties` (`cors.allowed-origins`, set during VTF-262).
- **nginx**: terminates TLS on 443 and forwards to the Spring Boot process on 7443. nginx must allow request bodies up to 50 MB (`client_max_body_size 50m;`) because the validate endpoint can receive 60–100 MB of parsed JSON for large batches.
- **Spring Boot app**: single process, no clustering, no shared cache, no database. Tomcat is the embedded container.
- **Tomcat tunables (`application.properties`)**:
  - `server.tomcat.max-part-count = 4096` — caps multipart parts on upload.
  - `server.tomcat.max-http-post-size = 52428800` (50 MB) — caps JSON body on validate (VTF-292).

### 3.2 Application startup sequence

The application's bootstrap is a strict, fail-fast pipeline:

```
ConfigurationValidationServiceApplication.main()
  → SpringApplication.run()
      → Spring container creates @Configuration beans
          → JacksonConfig produces an ObjectMapper with:
              - FAIL_ON_UNKNOWN_PROPERTIES = true
              - JavaTimeModule registered
              - WRITE_DATES_AS_TIMESTAMPS = false
      → Spring container creates @Component / @Service beans
          → ConfigOptionsService constructor reads
            classpath:value-mappings.properties (eager, final field)
          → All 9 ValidationRule @Components are instantiated
          → RuleExecutionEngine constructor receives List<ValidationRule>,
            indexes them into EnumMap<RuleType, ValidationRule>
          → DefaultRuleExecutor constructor does the same
      → @PostConstruct phase
          → ValidationConfigurationStartupValidator.validateAtStartup()
              → ValidationConfigurationLoader.loadConfiguredRules()
                  · resolves "classpath:ValidationConfiguration.json"
                  · jackson-parses into List<RuleConfig>
                  · stamps every rule with origin = CONFIGURED
                  · DefaultRuleConfigValidator.validate(rules)
              → ConfigValidationService constructor runs
                  · loadConfiguredRules() again (no caching layer between)
                  · groups by ValidationKey(block, entry) into rulesByKey
      → Tomcat starts on 7443, /swagger-ui.html available
```

Key invariants enforced at startup (any violation aborts boot):

1. `ValidationConfiguration.json` must exist on the classpath.
2. The file must be non-empty.
3. Every rule must have `RuleType`, `ConfigBlockName`, `ConfigEntryKey`, `UIInputRequired`.
4. `RuleType` must be one of `RuleType.supportedExternalNames()`.
5. `RangeCheck` rules must have ordered `min`/`max`.
6. Any `*OrBlockNotFound` rule must have `DefaultValue` (VTF-299).
7. `ValidateOnlyInFilesWith`, if present, must be a member of `ConfigFileMarker`.
8. The same `(ruleType, block, entry, marker)` tuple must not appear twice.

If `jackson` encounters an unknown field, `UnrecognizedPropertyException` is caught and rethrown as `RuleConfigurationException("Invalid rule configuration: unrecognized field '<X>' at rule index <N>")` — i.e. typos in the JSON fail boot, not validation requests.

Note that `loadConfiguredRules()` is called **twice** at startup: once by `ValidationConfigurationStartupValidator` (fail-fast) and once by `ConfigValidationService`'s constructor. Both reads are idempotent.

### 3.3 Path A — `POST /api/upload/adcfiles` (file parsing)

**Endpoint**: `UploadController.adcFiles(@RequestPart("files") MultipartFile[] files)`
**Consumes**: `multipart/form-data`
**Returns**: `ResponseEntity<List<ParsedConfigFile>>`

```
Frontend ─► multipart POST /api/upload/adcfiles
        │
        │   field name: files (array)
        │
        ▼
UploadController.adcFiles(files)
  ├─ if files == null || files.length == 0
  │     → throw InvalidUserValidationInputException
  │       ("At least one config file is required")
  │       → handled by GlobalExceptionHandler → HTTP 400
  │
  └─ ConfigParsingService.parseFiles(files)
        │
        ├─ duplicate null/empty guard (defensive)
        │
        ├─ Arrays.stream(files)
        │     .filter(non-null filename)
        │     .filter(filename.toLowerCase().endsWith(.adc))     ← from file.parser.extension
        │     .sorted by filename                                 ← deterministic ordering
        │     .map(CfgParserUtil::parse)                          ← static call, per file
        │     .toList()
        │
        ├─ if list empty → InvalidUserValidationInputException
        │
        └─ log.debug + return List<ParsedConfigFile>
```

#### 3.3.1 `CfgParserUtil.parse(MultipartFile)` — the parsing kernel

```
parse(MultipartFile file):
  open BufferedReader(new InputStreamReader(file.getInputStream()))   ← UTF default
  sequence = 0
  currentBlock = null
  blocks = []

  for each line in reader:
    trim
    skip blank lines and lines starting with "//"

    if line starts with "[" and ends with "]":          ← block marker
        currentBlock = new ConfigBlock()
        currentBlock.sequenceNumber = ++sequence
        blocks.add(currentBlock)
        continue

    if currentBlock != null:
        entry = parseEntry(line)
        if entry == null: continue                       ← malformed line is silently skipped
        if currentBlock.name == null:
            currentBlock.name = entry.key                ← FIRST entry's key names the block
        currentBlock.entries.add(entry)

  catch Exception → throw new FileParsingException(filename, cause)
                    → handled by GlobalExceptionHandler → HTTP 400 FILE_PARSE_ERROR

  enrichBlockOccurrences(blocks)
  scan blocks → set 4 classification flags
  scan blocks → extract fileId from ID.ID
  return new ParsedConfigFile(filename, blocks, trackSectionDetails,
                              acoIoexbDetails, dtIoexbDetails, comDetails, fileId)
```

**`parseEntry(line)`** — extracts one entry:

- Splits off any `//…` inline comment.
- Tokenises on whitespace; first token = `key`.
- Second token must split on `:` into `[bits, value]`. `bits` parsed as int; non-numeric → returns `null` (line skipped).
- Builds `new ConfigEntry(key, bits, value, comment)`.

**`enrichBlockOccurrences(blocks)`** — post-parse pass adds multi-occurrence metadata used by `MultipleBlockSingleInputMatch` / `MultipleBlockMultipleInputMatch`:

```
group blocks by name (LinkedHashMap, insertion-ordered)
for each (name, sameNameBlocks):
    total = size
    startSeq = min(sequenceNumber across them)
    endSeq   = max(sequenceNumber across them)
    for i, block in enumerate(sameNameBlocks):
        block.totalOccurrence = total
        block.startSequenceNumber = startSeq
        block.endSequenceNumber   = endSeq
        block.blockIndex = (total > 1 ? i : 0)
```

**Classification-flag pass** — independent boolean OR for each:

| Trigger block (case-insensitive) | Flag set |
|---|---|
| `CFG_ZP_FMA1`, `CFG_ZP_FMA2` | `trackSectionDetails = true` |
| `CFG_AXCNT`, `CFG_SECTION_OUT` | `acoIoexbDetails = true` (VTF-297) |
| `CFG_DATA_SAFETY_LEVEL`, `CFG_DATA_OUT` | `dtIoexbDetails = true` (VTF-297) |
| `CFG_MY_IP_NW1` | `comDetails = true` *(see §10.1 — known gap)* |

**File ID extraction** — first block named `ID`, first entry keyed `ID`, parsed as int; on `NumberFormatException` defaults to `0`.

**Returned object** — `ParsedConfigFile(fileName, blocks, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails, id)` (positional constructor; field order matters because Lombok `@AllArgsConstructor` is in use).

#### 3.3.2 Response shape

The endpoint returns `200 OK` with `List<ParsedConfigFile>` serialised by the global `ObjectMapper`. Each element is a flat JSON object with:

- `fileName`, `id`, four classification booleans, and a nested `blocks` array of `{ name, sequenceNumber, blockIndex, totalOccurrence, startSequenceNumber, endSequenceNumber, entries: [{ key, bits, value, comment }] }`.

Because `FAIL_ON_UNKNOWN_PROPERTIES=true`, the **frontend must round-trip the entire object back** to the validate endpoint without dropping or renaming fields.

### 3.4 Path A′ — `POST /api/upload/translate` (XML → JSON helper)

**Endpoint**: `UploadController.transformXmlFile(@RequestPart("file") MultipartFile file)`
**Purpose**: a one-shot converter for legacy XML exports.

```
UploadController.transformXmlFile(file)
  ├─ if file null or empty → 400 InvalidUserValidationInputException
  ├─ if !filename.toLowerCase().endsWith(".xml") → 400
  ├─ xmlContent = new String(file.bytes, UTF-8)
  ├─ XmlToJsonTransformer.containsConfigBlocks(xmlContent)
  │     → if false → 400 ("No configuration blocks found")
  ├─ XmlToJsonTransformer.transformXmlToJson(xmlContent)
  │     · jackson XmlMapper deserialisation
  │     · strips "AEB_" / "COM_" prefixes from element names
  │     · forces all values to String
  │     · preserves element order
  └─ return JSON string
  catch IOException → 400 InvalidUserValidationInputException ("Failed to read XML file: …")
```

This path is independent of validation; it produces JSON intended to be hand-fed into the validate endpoint by tools that started life with XML.

### 3.5 Path B — `POST /api/config/validate` (the validation pipeline)

This is the heaviest path. Five distinct stages run in order; each is described with its actual class calls.

**Endpoint**: `ConfigValidationController.validate(@RequestBody ValidationRequestWrapper request)`
**Consumes**: `application/json`
**Returns**: `ResponseEntity<ValidationSummary>`

```
Frontend ─► JSON POST /api/config/validate
  Body: { parsedConfigFiles: [...], userInput: { sections: {...} } }
        │
        ▼
ConfigValidationController.validate(request)
  ├─ parsedConfigFiles = request.parsedConfigFiles
  ├─ userInput         = request.userInput (UserValidationInputCriteria)
  │
  ├─ guards:
  │    · parsedConfigFiles null/empty → 400 InvalidUserValidationInputException
  │    · userInput null               → 400
  │    · userInput.sections null/empty→ 400
  │
  ├─ results  = ConfigValidationService.validateParsedFiles(
  │                  parsedConfigFiles, userInput.sections)
  │
  └─ summary  = SummaryService.generateSummary(parsedConfigFiles, results)
  └─ return 200 OK with summary
```

#### 3.5.1 Stage 1 — request binding and payload reception

- `ValidationRequestWrapper` is a plain DTO: `{ List<ParsedConfigFile> parsedConfigFiles; UserValidationInputCriteria userInput; }`.
- `UserValidationInputCriteria` is unusual: it uses `@JsonAnySetter addSection(name, rules)` to capture an **arbitrary** top-level map `{ <blockName>: { <entryKey>: value, … } }` into its private `sections: Map<String, Map<String, Object>>` field. The frontend may send any block/entry names; the backend does not pre-declare the schema.
- Internally `sections` is what's passed onwards as `userInput`.
- Malformed JSON triggers `HttpMessageNotReadableException` → `GlobalExceptionHandler` returns `INVALID_PAYLOAD` 400.

Payload value forms accepted per `(block, entry)`:

| Form | Used by | Example |
|---|---|---|
| Scalar | `InputMatch`, `InputMatchOrBlockNotFound` | `"BEHAV_RESET": "4"` |
| Array | `OptionalInputMatch`, `OptionalInputMatchOrBlockNotFound`, `MultipleBlockMultipleInputMatch` | `"BEHAV_INPUT3": ["0","7"]` |
| `{ min, max }` | `RangeCheck` (only if `UIInputRequired=Yes`; currently the only `RangeCheck` on `ID.ID` is `UIInputRequired=No`, so this path is dormant) | `"ID": { "min": 1, "max": 4095 }` |
| `BLOCK_EXISTS` + scalar | `ProjectBlockCheck` (the block-level map carries `BLOCK_EXISTS: boolean` and, if `true`, the `PROJECT_NUMBER` value) | `"CFG_PROJECT_AEB": { "BLOCK_EXISTS": true, "PROJECT_NUMBER": "1234" }` |

#### 3.5.2 Stage 2 — payload validation (`DefaultPayloadValidator.validate`)

`ConfigValidationService.validateParsedFiles(files, userInput)` first calls `payloadValidator.validate(userInput, rulesByKey)`. Two passes happen inside `DefaultPayloadValidator`:

**Pass 1 — resolve all user inputs (rule-independent):**

```
for each (block, entries) in userInput:
    for each (entry, value) in entries:
        if value != null:
            resolvedPayloads.put(
                new ValidationKey(block, entry),
                ResolvedPayload.present(value))
```

This is unconditional — the user can put values for keys with no configured rule, and they survive into Pass 1 because §3.5.5's default-rule path will consume them.

**Pass 2 — enforce constraints, but ONLY against configured rules:**

```
for each (key, [ruleConfigs]) in rulesByKey:
    for each rule in [ruleConfigs]:
        validateForRule(rule, key, blockPayload, resolvedPayloads)
```

`validateForRule` throws `InvalidUserValidationInputException` (→ 400) on:

| Condition | Message |
|---|---|
| `UIInputRequired = "Yes"` and `resolvedPayloads` has no entry for the key | `"payload input required for <block>::<entry>"` |
| `RangeCheck` with `UIInputRequired = "Yes"` but no `{ min, max }` map | `"RangeCheck requires {min, max} for <block>::<entry>"` |
| `OptionalInputMatch` / `MultipleBlockMultipleInputMatch` value is not a `List` | `"Array value required for <block>::<entry>"` |
| `ProjectBlockCheck` block's map has no `BLOCK_EXISTS: Boolean` | `"ProjectBlockCheck requires BLOCK_EXISTS boolean in block <block>"` |

(Note: `OptionalInputMatchOrBlockNotFound` is not explicitly validated for array shape in Pass 2 — it goes through the `default` arm. The rule itself defensively uses `payload.asStringList()`.)

The result is an immutable `ResolvedPayloadContext(Map<ValidationKey, ResolvedPayload>)`. Missing keys resolve to `ResolvedPayload.missing()` via `payloadFor(key).getOrDefault(..., missing)`.

#### 3.5.3 Stage 3 — duplicate registry reset

```
duplicateRegistry.clear()
```

`DuplicateValueRegistry` is a Spring-managed singleton that holds a synchronised `Map<String, Map<String, Map<String, Integer>>>` keyed by `block::entry::value`. It is the ONLY mutable shared state during a validation run and must be reset before every request so that `DuplicateCheckRule` operates within a single request's universe.

#### 3.5.4 Stage 4 — rule execution loop (`ConfigValidationService` × `RuleExecutionEngine`)

This is the core. For each file, for each key, the engine decides what to do.

```
for each ParsedConfigFile file in parsedFiles:                       ← outer loop
    FileContext ctx = new FileContext(file)
    Set<ValidationKey> universe = buildValidationUniverse(
            rulesByKey.keySet(), userInput)
        · keys = configured rule keys (from rulesByKey)
        · plus  EVERY (block, entry) the user submitted
                EXCEPT entries whose key equals "BLOCK_EXISTS"
                (case-insensitive; BLOCK_EXISTS is a flag, not a parameter)

    for each ValidationKey key in universe:                          ← inner loop
        try:
            List<RuleConfig> ruleConfigs = rulesByKey.getOrDefault(key, [])
            RuleExecutionResult exec = ruleEngine.execute(
                    file, ctx, key, ruleConfigs, resolvedPayloadContext)

            if exec.ruleExecuted:
                results.addAll(exec.results)
            else:
                hasMarkerRule = any rule has ValidateOnlyInFilesWith != null
                if !hasMarkerRule:                                    ← important guard
                    results.addAll(
                        defaultRuleExecutor.executeDefault(
                            file, ctx, key, resolvedPayloadContext))
        catch Exception:
            throw new ValidationEngineException("Unexpected …", cause)
            → GlobalExceptionHandler → 500 ENGINE_ERROR

sort results by (ruleType, blockName, entryKey), case-insensitive
return List<ValidationResult>
```

**`buildValidationUniverse`** is what makes the engine open-ended: a user-supplied input for any unconfigured `(block, entry)` enters the universe and can trigger a default rule.

**`RuleExecutionEngine.execute(...)`** then walks every `RuleConfig` for the key:

```
RuleExecutionResult execute(file, fileContext, key, rules, inputContext):
    configuredRuleExecuted = false
    results = []
    hasProjectBlockCheckForBlock = any rule is PROJECT_BLOCK_CHECK
                                    on key.block

    for each rule in rules:
        if !isFileEligible(rule, file):  continue                    ← marker mismatch
            // marker enum: ACOIOEXBDETAILS, DTIOEXBDETAILS,
            //              TRACKSECTIONDETAILS, COMDETAILS
            // null marker → applies to all files

        type = RuleType.fromExternal(rule.ruleType)
        payloadPresent = inputContext.payloadFor(key).isPresent()

        decision = ValidationDecisionEngine.decide(
                       new ValidationContext(payloadPresent, rule, file))

        if decision == IGNORE: continue
        // (note: RuleExecutionEngine treats both IGNORE and the absent
        //  case identically; APPLY_DEFAULT is unreachable here because
        //  a non-null rule was passed — APPLY_DEFAULT is handled by the
        //  outer ConfigValidationService loop via DefaultRuleExecutor)

        impl = rulesByType.get(type)        ← EnumMap lookup
        if impl == null:
            log.warn("No ValidationRule for type [{}]", rule.ruleType)
            continue

        context = new RuleExecutionContext(
                       fileContext, key, rule,
                       inputContext.payloadFor(key),
                       inputContext, duplicateRegistry)
        results.addAll(impl.execute(context))
        configuredRuleExecuted = true

    if hasProjectBlockCheckForBlock:
        configuredRuleExecuted = true        ← suppresses default for sibling
                                              entries under the same block

    return new RuleExecutionResult(results, configuredRuleExecuted)
```

**`ValidationDecisionEngine.decide(ctx)`** — single pure function, three lines of logic:

```
1. rule != null && !ruleApplicableForFile  → IGNORE         (marker check)
2. shouldSkipForComFile                    → IGNORE         (SkipComFile + COM)
3. rule == null                            → payloadPresent
                                              ? APPLY_DEFAULT
                                              : IGNORE
4. otherwise                               → APPLY_RULE
```

The supporting `ValidationContext` methods:

- `isRuleApplicableForFile()` — null marker → true. Otherwise `FileApplicability.from(marker).applies(file)` (delegates to the enum's lambda, which checks one of the four `is*Details()` flags on `ParsedConfigFile`).
- `shouldSkipForComFile()` — `(SkipComFile == null || SkipComFile == true) && file.isComDetails()`. Defaults to **skipping** when `skipComFile` is omitted — the JSON usually sets it explicitly, but the default is "yes, skip on COM".

**`isFileEligible(rule, file)`** in `RuleExecutionEngine` re-implements the marker check inline using an uppercase switch — functionally equivalent to `FileApplicability`. (Two paths to the same check exist; this is intentional to keep `RuleExecutionEngine` independent of the `spec` package's enum.)

**`RuleExecutionContext`** carries everything a rule needs:

```
FileContext          ← file plus hasBlock(name), values(block, entry)
ValidationKey        ← target (block, entry)
RuleConfig           ← the rule itself
ResolvedPayload      ← user-supplied expected value, or .missing()
ResolvedPayloadContext  ← whole-request payload map (rarely used)
DuplicateValueRegistry  ← shared mutable map for DuplicateCheckRule
```

#### 3.5.5 Stage 4b — default-rule fallback (`DefaultRuleExecutor`)

When the engine returns `ruleExecuted == false` for a key AND no rule on that key carries a marker, `ConfigValidationService` invokes `DefaultRuleExecutor.executeDefault(...)`:

```
executeDefault(file, fileContext, key, inputContext):
    defaultRule = DefaultRuleFactory.inputMatch(key.block, key.entry)
                  · ruleType        = "InputMatch"
                  · uiInputRequired = "No"
                  · skipComFile     = true
                  · origin          = DEFAULT
    decision = ValidationDecisionEngine.decide(
                   new ValidationContext(
                       inputContext.payloadFor(key).isPresent(),
                       defaultRule, file))
    if decision == IGNORE: return []
    inputMatch = rulesByType.get(INPUT_MATCH)
    if inputMatch == null: throw IllegalStateException
    return inputMatch.execute(new RuleExecutionContext(...))
```

This is the mechanism behind the "user can validate any parameter, not just the 41 configured ones" property.

**Two important guards:**

1. The marker-rule guard in `ConfigValidationService` — if a key has a configured rule with `ValidateOnlyInFilesWith` set, the engine intentionally skipped that file. A default rule **must not** be created for it, because the marker is the rule author's signal "this entry isn't meaningful on this file class".
2. The ProjectBlockCheck suppression in `RuleExecutionEngine` — once a `ProjectBlockCheck` runs on a block, sibling `(block, entry)` defaults are silently suppressed because the block-level check already covers them.

#### 3.5.6 Stage 5 — result sort

After the outer file loop:

```
sortedResults = results.stream()
    .sorted by ruleType (case-insensitive),
             then blockName,
             then entryKey
    .toList()
```

This is the order shipped to the frontend and into the Excel "Validation Results" sheet.

#### 3.5.7 Stage 6 — summary generation (`SummaryService.generateSummary`)

The controller then calls `SummaryService.generateSummary(parsedConfigFiles, validationResults)`. The service is **stateless** (VTF-280): all inputs flow as parameters, never instance fields. It dispatches to extractors in this exact order:

```
1. extractDpDetails(parsedFiles)
   · INTERNAL helper that filters out COM files (file.isComDetails() == false)
   · then DpDetailExtractorService.extractDpDetails(filtered)

2. trackSectionExtractorService.extractTrackSectionDetails(parsedFiles)
3. chcExtractorService.extractCHCDetails(parsedFiles)
4. supervisorExtractorService.extractSupervisorDetails(parsedFiles)
5. ioexbBehaviourExtractorService.extractIOEXBBehaviourDetails(parsedFiles)
6. ioexbAcoExtractorService.extractIOEXBAcoDetails(parsedFiles)
7. dataTransmissionExtractorService.extractDataTransmissionDetails(parsedFiles)
8. ethernetDetailExtractorService.extractEthernetDetails(parsedFiles)
```

Each extractor:

- Filters `parsedFiles` by one classification flag (see §3.8.5 table).
- Iterates the relevant `ConfigBlock`s (using `block.getName()` matching).
- Pulls entry values via either `FileContext.values(...)` or its own helper.
- Maps raw codes to UI labels through `ValueMappingService.mapValue(fieldKey, rawValue)` which reads `value-mappings.properties`.
- Builds a typed detail object (`DpDetail`, `TrackSectionDetail`, …) via Lombok `@Builder`.

The 8 detail lists plus the `validationResults` list are then assembled (via setters) into a single `ValidationSummary` object, which is the 200 response body.

#### 3.5.8 Validation-pipeline call graph (one frame)

```
ConfigValidationController.validate
  └─ ConfigValidationService.validateParsedFiles
        ├─ DefaultPayloadValidator.validate                              ← Stage 2
        │     · resolves all inputs
        │     · enforces UIInputRequired / shape constraints
        │     ↳ throws InvalidUserValidationInputException (400)
        │
        ├─ DuplicateValueRegistry.clear                                  ← Stage 3
        │
        └─ for each file: for each key:                                  ← Stage 4
              RuleExecutionEngine.execute
                ├─ isFileEligible (marker check)
                ├─ ValidationDecisionEngine.decide
                │     · IGNORE / APPLY_RULE
                ├─ rulesByType.get(type).execute(RuleExecutionContext)
                │     · 9 implementations in validation/rules
                │     · may consult FileContext.hasBlock / .values
                │     · may consult DuplicateValueRegistry
                │     → emits 0..N ValidationResult
                └─ may return ruleExecuted = false
              if !ruleExecuted && no marker rule:
                  DefaultRuleExecutor.executeDefault
                    · synth InputMatch rule (origin = DEFAULT)
                    · ValidationDecisionEngine.decide (again, on synth)
                    · rulesByType.get(INPUT_MATCH).execute
                    → emits 0..N ValidationResult

  sort results by ruleType / block / entry

SummaryService.generateSummary                                           ← Stage 6
  └─ 8 ExtractorService calls
        └─ ValueMappingService.mapValue (per cell)
  → build ValidationSummary

ConfigValidationController returns 200 OK
```

#### 3.5.9 Failure modes summary for the validate path

| Where | Triggered by | Exception → HTTP |
|---|---|---|
| Controller guards | empty `parsedConfigFiles`, missing `userInput.sections` | `InvalidUserValidationInputException` → 400 |
| JSON deserialisation | malformed body, unknown field | `HttpMessageNotReadableException` → 400 `INVALID_PAYLOAD` |
| `DefaultPayloadValidator` | missing required input, wrong shape | `InvalidUserValidationInputException` → 400 `INVALID_USER_INPUT` |
| `RuleType.fromExternal` | rule JSON contains unknown ruleType (shouldn't happen — fail-fast at boot) | `RuleConfigurationException` → 500 |
| Any uncaught in the inner loop | rule impl bug, NPE, etc. | wrapped in `ValidationEngineException` → 500 `ENGINE_ERROR` |
| Last-resort catch-all | anything else | `Exception` handler → 500 `UNEXPECTED_ERROR` |

### 3.6 Path C — `POST /api/report/download` (Excel generation)

**Endpoint**: `ReportController.downloadReport(@RequestBody ValidationSummary validationSummary)`
**Consumes**: `application/json`
**Returns**: `ResponseEntity<byte[]>` with `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

```
Frontend ─► JSON POST /api/report/download
  Body: ValidationSummary (the unmodified response from /validate)
        │
        ▼
ReportController.downloadReport(validationSummary)
  ├─ guards:
  │    · summary == null              → 400 InvalidUserValidationInputException
  │    · summary.results null/empty   → 400
  │
  ├─ excelOutput = ExcelSummaryUtil.generate(summary)   ← static call
  │     · creates XSSFWorkbook (Apache POI 5.2.5)
  │     · ExcelStyleManager: fonts, fills, borders, conditional PASS/FAIL
  │     · ExcelSheetBuilder: one "Validation Results" sheet + one per
  │                          non-empty detail list (skips empty lists)
  │     · ExcelHeaderProcessor: column headers (uppercase, VTF-284)
  │     · ExcelColumnMapper: per-sheet column-name → field mapping
  │     · ExcelDataProcessor: writes cell values; if a field is List<String>,
  │                           joins entries with "\n" inside a single cell
  │                           (used for multi-IP and forward-ACD lists)
  │     · ExcelFreezePaneManager: freezes header rows
  │     · workbook.write(ByteArrayOutputStream).toByteArray()
  │
  └─ ResponseEntity 200
        Content-Disposition: attachment; filename="validation-summary.xlsx"
        Content-Type:       application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
        Body:               excelOutput (byte[])
```

Excel generation failure throws `ReportGenerationException` → 500 `REPORT_GENERATION_FAILED` via the global handler.

### 3.7 Path D — `GET /api/configoptions` (UI dropdown source)

**Endpoint**: `ConfigOptionsController.getConfigOptions()`
**Returns**: `ResponseEntity<List<ConfigOptions>>`

```
ConfigOptionsController.getConfigOptions
  └─ ConfigOptionsService.getConfigOptions
        └─ returns the configOptions field (final, populated in constructor)
```

`ConfigOptionsService` loads at construction time:

```
ConfigOptionsService() constructor:
    properties = new Properties()
    open classpath:value-mappings.properties (UTF-8)
    properties.load(stream)
    sortedKeys = properties.stringPropertyNames().sorted()

    grouped = new LinkedHashMap<String, List<OptionMapping>>()
    for each propKey:
        dotIdx = propKey.indexOf('.')
        if dotIdx < 0: skip
        paramName = propKey.substring(0, dotIdx)         // e.g. "INTERVAL"
        optionKey = propKey.substring(dotIdx + 1)         // e.g. "3" or "min"
        rawValue  = properties.getProperty(propKey).trim()

        // Range-metadata keys (min/max/step/description) sometimes have
        // values like "min - 0" — strip everything up to " - "
        if optionKey ∈ {min, max, step, description}
           and rawValue contains " - ":
            value = rawValue.substring(idx + 3).trim()
        else:
            value = rawValue

        grouped[paramName].add(new OptionMapping(optionKey, value))

    configOptions = grouped.entrySet()
                           .map(e -> new ConfigOptions(e.key, e.value))
                           .toList()
```

VTF-280 made `configOptions` `final` and initialised it in the constructor. Property reload requires a restart by design.

### 3.8 Cross-cutting concerns

#### 3.8.1 Thread model

- Tomcat NIO thread pool serves all endpoints concurrently.
- Every Spring-managed singleton on the hot path is either stateless (`ConfigValidationService`, `RuleExecutionEngine`, `DefaultRuleExecutor`, `SummaryService`, all extractors, all `ValidationRule` impls) or holds immutable post-construction state (`ConfigOptionsService.configOptions` — `final`).
- The only mutable shared state is `DuplicateValueRegistry`. Its internal map is `synchronized`, but **two concurrent validate requests will see each other's duplicate counts** because the registry is a singleton and is `.clear()`-ed at the top of each request — there is no per-request copy.
  - As of VTF-305 this is acceptable because the deployed environment is single-user, low concurrency. If concurrent validation becomes a requirement, the registry must become request-scoped.

#### 3.8.2 Validation universe — what triggers a check?

For a given file, the engine runs a check on `(block, entry)` if **any** of the following hold:

1. The pair has a configured rule in `ValidationConfiguration.json` AND the file passes the marker check AND `SkipComFile` is not violated.
2. The user submitted a value for the pair, the pair has no configured rule, and the pair has no marker-bearing sibling rule (default-rule path).
3. The pair has a `RangeCheck` or `DuplicateCheck` rule (these always run regardless of user input because `UIInputRequired = No`).

#### 3.8.3 What ends up in `ValidationResult`?

`ValidationResultFactory.create(...)` builds each result with:

- `fileName` — `parsedFile.fileName`
- `ruleType` — external name (e.g. `"InputMatch"`)
- `blockName`, `entryKey` — from the rule
- `expectedValue` — the user input or rule's `DefaultValue`, stringified
- `actualValue` — the joined value(s) from the file, or one of the sentinels:
  - `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` — the entry wasn't found anywhere
  - `CONFIG_BLOCK_NOT_FOUND` — the whole block was absent (used by `*OrBlockNotFound` rules)
- `status` — `"PASS"`, `"FAIL"`, or (rare) `"INVALID"`

`DuplicateCheckRule` is the only rule that consults `DuplicateValueRegistry` — every time it sees a value it `register()`s it; on the second occurrence the count reaches 2 and a `FAIL` is emitted for both files.

#### 3.8.4 Extractor filter cheat-sheet

| Extractor | Filter | Trigger blocks read |
|---|---|---|
| `DpDetailExtractorService` | `!file.isComDetails()` (applied in `SummaryService.extractDpDetails`) | ID, CFG_SECTION, CFG_BEHAV_TGGL, CFG_TIMEOUT, CFG_PARAM_TROLLEY_SUPP, CFG_RSR_TYPE, CFG_TYPE_PRTCT, CFG_PROJECT_AEB |
| `TrackSectionExtractorService` | `file.isTrackSectionDetails()` | CFG_ZP_FMA1, CFG_ZP_FMA2 |
| `CHCExtractorService` | `!file.isComDetails()` | CFG_CONTROL, CFG_ZP |
| `SupervisorExtractorService` | `!file.isComDetails()` | CFG_SUPERVIS_FMA1/FMA2, CFG_AUTORESET_FMA1/FMA2 |
| `IOEXBBehaviourExtractorService` | `file.isAcoIoexbDetails()` | CFG_AXCNT, CFG_COOP_RESET |
| `IOEXBAcoExtractorService` | `file.isAcoIoexbDetails()` | CFG_SECTION_OUT |
| `DataTransmissionExtractorService` | `file.isDtIoexbDetails()` | CFG_DATA_SAFETY_LEVEL, CFG_DATA_OUT |
| `EthernetDetailExtractorService` | `file.isComDetails()` | CFG_MY_IP_NW1/NW2, CFG_MY_MASK, CFG_INT_ID_DEST_NW1/NW2, CFG_FWRD_ACD, CFG_INTERVAL |

(Filtering happens inside each extractor or in `SummaryService.extractDpDetails` for DP.)

#### 3.8.5 Round-tripping & contract drift

Because the validate endpoint expects the **exact** JSON the upload endpoint produced (no field renames, no drops), `FAIL_ON_UNKNOWN_PROPERTIES = true` is intentional: it makes any frontend/backend contract drift fail loudly. VTF-297 (`ioexbDetails` → `acoIoexbDetails` + `dtIoexbDetails`) and VTF-303 (`destIpNw1` String → `List<String>`) are recent breaking changes that required frontend updates.

#### 3.8.6 Sizing & request limits

- 140 `.ADC` files ≈ 2–3 MB JSON.
- 4000 files ≈ 60–100 MB JSON.
- `server.tomcat.max-http-post-size = 52428800` (50 MB) — VTF-292. Validate-endpoint POST bodies near or above this need a Tomcat bump.
- nginx must mirror via `client_max_body_size 50m;` (infra, not code).
- Tomcat multipart parts cap: `server.tomcat.max-part-count = 4096`.

#### 3.8.7 Statelessness — what is and isn't persisted

- **Persisted**: nothing. No database, no file store, no cache between requests.
- **Per-request**: `DuplicateValueRegistry` (cleared at request start).
- **Per-process**: rules loaded from `ValidationConfiguration.json` (read once at startup; immutable thereafter) and config-options loaded from `value-mappings.properties` (same).
- **Per-file**: classification flags and block-occurrence metadata; computed once at parse time and never mutated.

---

## 4. Domain Model

### Hardware terminology (Frauscher FAdC)

| Term | Meaning |
|---|---|
| **DP** | Detection Point — counting head pair on the track (an axle counter section endpoint). |
| **AEB** | Advanced Evaluation Board — primary evaluation logic. |
| **IOEXB** | I/O Extension Board. Two variants: **ACO** (axle-counting I/O) and **DT** (data transmission). |
| **COM** | Communication board — Ethernet networking. |
| **FMA** | Track section (Field Manipulation Area). One AEB controls 1–2 FMAs. |
| **ZP** | Zone / counting head parameters. |
| **CHC** | Counting Head Controller. |
| **GS05 / GS06 / GS07** | Component generations (versions). GS05 differs in available parameters — VTF-290 added version-aware skipping. |
| **PWR-2+** | Backplane variant capable of hosting both ACO and DT IOEXB on the same AEB. |

### Core entities (Java models)

**`ParsedConfigFile`** — one parsed `.ADC` file.
- `fileName: String`
- `blocks: List<ConfigBlock>`
- `id: int` — extracted from the `ID` block's `ID` entry
- Classification flags (independent booleans, may all be combined):
  - `trackSectionDetails` — file has `CFG_ZP_FMA1` or `CFG_ZP_FMA2`
  - `acoIoexbDetails` — file has `CFG_AXCNT` or `CFG_SECTION_OUT` (VTF-297)
  - `dtIoexbDetails` — file has `CFG_DATA_SAFETY_LEVEL` or `CFG_DATA_OUT` (VTF-297)
  - `comDetails` — file has `CFG_MY_IP_NW1` *(known gap, see §10)*

**`ConfigBlock`** — one named section in an `.ADC` file.
- `name: String` (e.g. `CFG_AXCNT`, `ID`, `CFG_TIMEOUT`)
- `entries: List<ConfigEntry>`
- `sequenceNumber: int`
- `blockIndex: int` — 0-based index among blocks sharing the same name
- `totalOccurrence: int`
- `startSequenceNumber: int`, `endSequenceNumber: int`

**`ConfigEntry`** — one parameter inside a block.
- `key: String` (e.g. `BEHAV_INPUT1`, `TIMEOUT_VALUE`)
- `bits: int` — bit width
- `value: String`
- `comment: String` — inline annotation from the `.ADC` file

**`RuleConfig`** — one validation rule definition loaded from `ValidationConfiguration.json`.
- `ruleType: String` — external name (e.g. `InputMatch`)
- `configBlockName: String`, `configEntryKey: String`
- `uiInputRequired: String` — `"Yes"` or `"No"`
- `validateOnlyInFilesWith: String` — optional file marker (see `ConfigFileMarker`)
- `min`, `max: Integer` — for RangeCheck
- `defaultValue: String` — required for `*OrBlockNotFound` rule types
- `skipComFile: Boolean`
- `origin: RuleOrigin` — `CONFIGURED` or `DEFAULT`

**`ValidationResult`** — one check outcome.
- `fileName`, `ruleType`, `blockName`, `entryKey`, `expectedValue`, `actualValue`, `status`

**`ValidationSummary`** — aggregate response.
- `validation_results: List<ValidationResult>`
- 8 detail lists: dp, track_section, chc, supervisor, ioexb_behaviour, ioexb_aco, data_transmission, ethernet

### Detail models

| Model | Source blocks | Purpose |
|---|---|---|
| `DpDetail` | ID, CFG_SECTION, CFG_BEHAV_TGGL, CFG_TIMEOUT, CFG_PROJECT_AEB, trolley/RSR/type-protect | Per-AEB summary row |
| `TrackSectionDetail` | CFG_ZP_FMA1, CFG_ZP_FMA2 | Track section (counting-head pair) per FMA |
| `CHCDetail` | CFG_CONTROL, CFG_ZP | CHC type per file |
| `SupervisorDetail` | CFG_SUPERVIS_FMA1/FMA2, CFG_AUTORESET_FMA1/FMA2 | Supervisor + autoreset config per FMA |
| `IOEXBBehaviourDetail` | CFG_AXCNT, CFG_COOP_RESET | IOEXB input behaviour and coop reset |
| `IOEXBAcoDetail` | CFG_SECTION_OUT | Axle counting output per section |
| `DataTransmissionDetail` | CFG_DATA_SAFETY_LEVEL, CFG_DATA_OUT | DT-IOEXB safety + outputs |
| `EthernetDetail` | CFG_MY_IP_NW1/NW2, CFG_MY_MASK, CFG_INT_ID_DEST_NW1/NW2, CFG_FWRD_ACD, CFG_INTERVAL | COM-board network config |

### Enums

| Enum | Values |
|---|---|
| `RuleType` | `INPUT_MATCH`, `INPUT_MATCH_OR_BLOCK_NOT_FOUND`, `OPTIONAL_INPUT_MATCH`, `OPTIONAL_INPUT_MATCH_OR_BLOCK_NOT_FOUND`, `RANGE_CHECK`, `DUPLICATE_CHECK`, `MULTIPLE_BLOCK_SINGLE_INPUT_MATCH`, `MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH`, `PROJECT_BLOCK_CHECK` |
| `ValidationStatus` | `PASS`, `FAIL`, `INVALID` |
| `ValidationDecision` | `APPLY_RULE`, `APPLY_DEFAULT`, `IGNORE` |
| `RuleOrigin` | `CONFIGURED`, `DEFAULT` |
| `ConfigFileMarker` | `ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `TRACKSECTIONDETAILS`, `COMDETAILS` |
| `FileApplicability` | `ALL`, `TRACKSECTIONDETAILS`, `ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `COMDETAILS` (each implements `applies(ParsedConfigFile)`) |

**Note**: `RuleType` now has **9 values** (VTF-300 added `OPTIONAL_INPUT_MATCH_OR_BLOCK_NOT_FOUND`). Earlier docs may say 8 — that is stale.

### Key interfaces

- `ValidationRule` — `supportedType()` + `execute(RuleExecutionContext) → List<ValidationResult>`.
- `PayloadValidator` — `validate(UserValidationInputCriteria, rulesByKey) → ResolvedPayloadContext`.
- `RuleConfigValidator` — startup validation; throws on invalid rule config.

### DTOs

- `ValidationRequestWrapper` — `{ parsedConfigFiles, userInput }`.
- `UserValidationInputCriteria` — uses `@JsonAnySetter` to accept an arbitrary nested map `{ blockName: { entryKey: value | [values] | { BLOCK_EXISTS, value } } }`.
- `RuleExecutionResult` — `{ results, ruleExecuted }`.
- `ApiErrorResponse` — `{ errorCode, message, timestamp }`.

---

## 5. Validation Rule Catalogue (41 rules)

All rules live in `src/main/resources/ValidationConfiguration.json`, loaded at startup. The decision engine, file applicability marker, and `SkipComFile` flag gate each rule.

### Rule type semantics

| Rule type | Pass condition | Notes |
|---|---|---|
| **InputMatch** | All occurrences of `block.entry` equal expected value | Fail if entry missing → `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` |
| **InputMatchOrBlockNotFound** | Value matches **OR** block entirely absent (only when absent value equals `DefaultValue`). VTF-299 added the default-value gate. | Default `PASS` when block missing only if `DefaultValue` is in expected set |
| **OptionalInputMatch** | Any value in the user-provided list matches the file value | Skip if payload absent |
| **OptionalInputMatchOrBlockNotFound** (VTF-300) | Payload absent → skip; block missing → PASS iff `DefaultValue` ∈ expectedValues; block present → delegate to `OptionalInputMatch` | Multi-value-aware: uses `payload.asStringList()` + `Set.contains` (bug fixed in VTF-300) |
| **RangeCheck** | Integer value within `[min, max]` | Otherwise FAIL or INVALID |
| **DuplicateCheck** | Value not previously registered for this `block::entry` in the current run | Uses shared `DuplicateValueRegistry` |
| **MultipleBlockSingleInputMatch** | All occurrences of the same block share the same matching entry value | Fail-fast on first mismatch |
| **MultipleBlockMultipleInputMatch** | Every entry value across occurrences is in the user-provided allowed set | VTF-295 changed `HashSet` → `LinkedHashSet` to preserve UI input order; used by CFG_TIMEOUT |
| **ProjectBlockCheck** | If user says `BLOCK_EXISTS=true`: block must exist and `PROJECT_NUMBER` matches. If `BLOCK_EXISTS=false`: block must be absent. | Used only for `CFG_PROJECT_AEB.PROJECT_NUMBER` |

### Configured rules (by block)

| # | Block.Entry | Type | UIInput | Marker | SkipCom | Default |
|---|---|---|---|---|---|---|
| 1 | ID.ID | RangeCheck | No | – | false | 1–4095 |
| 2 | ID.ID | DuplicateCheck | No | – | false | – |
| 3 | CFG_BEHAV_TGGL.BEHAV_RESET | InputMatchOrBlockNotFound | Yes | – | true | 4 |
| 4 | CFG_BEHAV_TGGL.BEHAV_SIMUL | InputMatchOrBlockNotFound | Yes | – | true | 0 |
| 5 | CFG_SECTION.COMM_FAIL | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 0 |
| 6 | CFG_SECTION.BEHAV_GE | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 7 | CFG_SECTION.CLR_TRACK | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 0 |
| 8 | CFG_SECTION.RESET_IN | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 5 |
| 9 | CFG_SECTION.RESET_OUT | OptionalInputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 10 | CFG_AXCNT.BEHAV_INPUT1 | InputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 1 |
| 11 | CFG_AXCNT.BEHAV_INPUT2 | InputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 4 |
| 12 | CFG_AXCNT.BEHAV_INPUT3 | OptionalInputMatchOrBlockNotFound | Yes | ACOIOEXBDETAILS | true | 0 |
| 13 | CFG_AXCNT.TYPE_IN1 | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 14 | CFG_AXCNT.TYPE_IN2 | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 15 | CFG_AXCNT.TYPE_IN3 | InputMatchOrBlockNotFound | No | ACOIOEXBDETAILS | true | 0 |
| 16 | CFG_AXCNT.BEHAV_IOEXB | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 17 | CFG_SECTION_OUT.CLR_OCC | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 18 | CFG_SECTION_OUT.TYPE_AUX1 | InputMatch | No | ACOIOEXBDETAILS | true | – |
| 19 | CFG_SECTION_OUT.TYPE_AUX2 | InputMatch | No | ACOIOEXBDETAILS | true | – |
| 20 | CFG_SECTION_OUT.AUX1_OUT | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 21 | CFG_SECTION_OUT.AUX2_OUT | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 22 | CFG_SECTION_OUT.AUX1_NO_NC | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 23 | CFG_SECTION_OUT.AUX2_NO_NC | InputMatch | Yes | ACOIOEXBDETAILS | true | – |
| 24 | CFG_OCC.OCC_DELAY | InputMatchOrBlockNotFound | Yes | – | true | 0 |
| 25 | CFG_OCC.OCC_EXT | InputMatchOrBlockNotFound | Yes | – | true | 26 |
| 26 | CFG_RESET.RESET_LD_TIME | InputMatchOrBlockNotFound | Yes | – | true | 1 |
| 27 | CFG_RESET.RESET_OP_TIME | InputMatchOrBlockNotFound | Yes | – | true | 50 |
| 28 | CFG_PROJECT_AEB.PROJECT_NUMBER | ProjectBlockCheck | Yes | – | true | – |
| 29 | CFG_TIMEOUT.TIMEOUT_VALUE | MultipleBlockMultipleInputMatch | Yes | – | true | – |
| 30 | CFG_ZP.INTERVAL | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 3 |
| 31 | CFG_ZP.SUPERVIS_COUNT | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 2 |
| 32 | CFG_ZP.SYSTEM_COUNT | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 2 |
| 33 | CFG_ZP.PARTIAL_COUNT | InputMatchOrBlockNotFound | Yes | TRACKSECTIONDETAILS | true | 1 |
| 34 | CFG_ZP.SUPERVIS_COUNT_LMT | InputMatchOrBlockNotFound | No | TRACKSECTIONDETAILS | true | 0 |
| 35 | CFG_TROLLEY_SUPP.TROLLEY_SUPP | InputMatch | No | – | true | – |
| 36 | CFG_PARAM_TROLLEY_SUPP.AXLE_DISTANCE | InputMatch | No | – | true | – |
| 37 | CFG_PARAM_TROLLEY_SUPP.DIAMETER | InputMatch | No | – | true | – |
| 38 | CFG_PARAM_TROLLEY_SUPP.SPEED_TOLERANCE | InputMatch | No | – | true | – |
| 39 | CFG_PARAM_TROLLEY_SUPP.SUPP_TIME | InputMatch | No | – | true | – |
| 40 | CFG_RSR_TYPE.RSR_TYPE | InputMatch | No | – | true | – |
| 41 | CFG_TYPE_PRTCT.TYPE_PRTCT_CODE | InputMatch | No | – | true | – |

### Default rules

If the user submits an expected value for a `(block, entry)` that has **no configured rule**, the engine creates a one-off `InputMatch` rule with `origin=DEFAULT` (via `DefaultRuleExecutor`) and runs it. This makes the engine open-ended — the configured 41 are the minimum, not the maximum.

---

## 6. Validation Pipeline (Detailed)

### Step 1 — File parsing (`CfgParserUtil`)

```
for each MultipartFile:
  read line-by-line
  identify block headers vs entry lines
  build List<ConfigBlock> with List<ConfigEntry>
  enrichBlockOccurrences():
    for blocks sharing a name → set totalOccurrence, blockIndex, start/endSequenceNumber
  scan block names → set classification flags
  extract file id from ID.ID
  return ParsedConfigFile
```

### Step 2 — Payload validation (`DefaultPayloadValidator`)

For each configured rule whose `UIInputRequired=Yes`, the user must have supplied a value. Validator also format-checks:
- `RangeCheck` → input parses as integer.
- `ProjectBlockCheck` → input contains `BLOCK_EXISTS: boolean`, with `PROJECT_NUMBER` present iff `BLOCK_EXISTS=true`.
- `OptionalInputMatch` / `MultipleBlockMultipleInputMatch` / `OptionalInputMatchOrBlockNotFound` → array form.

Returns immutable `ResolvedPayloadContext = Map<ValidationKey, ResolvedPayload>`.

### Step 3 — Rule execution (`RuleExecutionEngine`)

```
for each ParsedConfigFile:
  FileContext fc = new FileContext(file)
  universe = configuredKeys ∪ userInputKeys
  for each key in universe:
    for each RuleConfig targeting key:
      if !isFileEligible(rule, file): continue           // marker mismatch
      ValidationContext ctx = { payloadPresent, rule, file }
      switch ValidationDecisionEngine.decide(ctx):
        APPLY_RULE     → ValidationRule impl from EnumMap → execute
        APPLY_DEFAULT  → DefaultRuleExecutor: build temp InputMatch rule, execute
        IGNORE         → no result
  sort results by ruleType, blockName, entryKey (case-insensitive)
```

Engine output: `List<ValidationResult>` per file. Collected-all (never fail-fast).

### Step 4 — Summary (`SummaryService`)

Stateless. Takes `(files, results)`, dispatches to each extractor:

```
DpDetailExtractorService          → dp_details
TrackSectionExtractorService      → track_section_details
CHCExtractorService               → chc_details
SupervisorExtractorService        → supervisor_details
IOEXBBehaviourExtractorService    → ioexb_behaviour_details   (filter: acoIoexbDetails)
IOEXBAcoExtractorService          → ioexb_aco_details         (filter: acoIoexbDetails)
DataTransmissionExtractorService  → data_transmission_details (filter: dtIoexbDetails)
EthernetDetailExtractorService    → ethernet_details          (filter: comDetails)
```

Assembles `ValidationSummary`. Most extractors use `ValueMappingService.mapValue(fieldKey, rawValue)` to convert raw numeric codes into UI-friendly labels (from `value-mappings.properties`).

### Step 5 — Excel report (`ExcelSummaryUtil`)

`ExcelSheetBuilder` produces a workbook with:
- "Validation Results" sheet (filterable PASS/FAIL).
- One detail sheet per non-empty detail list (Distribution Points, Track Sections, CHC, Supervisor, IOEXB Behaviour, IOEXB ACO, Data Transmission, Ethernet).

`ExcelStyleManager` provides single-theme light-grey headers with PASS/FAIL conditional formatting (VTF-266 / VTF-284). `ExcelDataProcessor.setCellValue()` accepts `List<String>` and joins with newlines (used for multi-IP and forward-ACD lists).

---

## 7. REST API Contracts

### `POST /api/upload/adcfiles`

- Body: `multipart/form-data` — `files` field with one or more `.ADC` files.
- 200: `List<ParsedConfigFile>` JSON.
- 400: `FILE_PARSE_ERROR`.
- Filters by case-insensitive `.adc` extension.

### `POST /api/upload/translate`

- Body: `multipart/form-data` — one XML file.
- 200: JSON string (translated). Strips `AEB_` / `COM_` prefixes from element names.

### `POST /api/config/validate`

- Body: `ValidationRequestWrapper { parsedConfigFiles, userInput }`.
- 200: `ValidationSummary`.
- 400: `INVALID_USER_INPUT` (payload validation), `INVALID_PAYLOAD` (malformed JSON).
- 500: `ENGINE_ERROR`, `RULE_CONFIGURATION_ERROR`.

### `GET /api/configoptions`

- 200: `List<ConfigOptions>` — UI dropdown groups. Built from `value-mappings.properties` (loaded once at startup; field is `final`, see VTF-280).

### `POST /api/report/download`

- Body: `ValidationSummary`.
- 200: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- 500: `REPORT_GENERATION_FAILED`.

### Exception → HTTP table

| Exception | HTTP | Error code |
|---|---|---|
| `InvalidUserValidationInputException` | 400 | `INVALID_USER_INPUT` |
| `FileParsingException` | 400 | `FILE_PARSE_ERROR` |
| `HttpMessageNotReadableException` | 400 | `INVALID_PAYLOAD` |
| `RuleConfigurationException` | 500 | `RULE_CONFIGURATION_ERROR` |
| `ValidationEngineException` | 500 | `ENGINE_ERROR` |
| `ReportGenerationException` | 500 | `REPORT_GENERATION_FAILED` |

All handled by `GlobalExceptionHandler` (`@RestControllerAdvice`) returning `ApiErrorResponse`.

---

## 8. Validation Engine Internals

### Strategy pattern

`ValidationRule` interface → 9 `@Component` implementations. `RuleExecutionEngine` constructor takes `List<ValidationRule>` (Spring DI) and indexes by `supportedType()` into an `EnumMap<RuleType, ValidationRule>`. Dispatch is O(1).

### Decision engine

`ValidationDecisionEngine.decide(ValidationContext)` — pure static function:

1. Rule present and not applicable to file (marker mismatch) → `IGNORE`.
2. `SkipComFile` (default true when unset) and `file.isComDetails()` → `IGNORE`.
3. No configured rule, payload present → `APPLY_DEFAULT`.
4. No configured rule, payload absent → `IGNORE`.
5. Otherwise (configured rule passing the two gates above) → `APPLY_RULE`.

`UIInputRequired` is **not** consulted here — `DefaultPayloadValidator` enforces it upstream in Stage 2 (§3.5.2).

### Context bundles (immutable)

- `FileContext` — wraps `ParsedConfigFile`; `hasBlock(name)`, `values(block, entry)`.
- `ValidationKey` — `(blockName, entryKey)` tuple; map key throughout.
- `ResolvedPayload` — wraps user-supplied value(s); `isPresent()`, `asString()`, `asStringList()`, `raw()`.
- `ResolvedPayloadContext` — full immutable map.
- `RuleExecutionContext` — bundle of FileContext + ValidationKey + RuleConfig + ResolvedPayload + ResolvedPayloadContext + DuplicateValueRegistry.

### `DuplicateValueRegistry`

The only shared mutable state per request. `synchronized` map of `block::entry::value → count`. Used by `DuplicateCheckRule`. Re-created per validation request, so no cross-request leak.

### Startup validation (`ValidationConfigurationStartupValidator`)

`@PostConstruct` loads `ValidationConfiguration.json`, calls `DefaultRuleConfigValidator.validate(rules)`. Fail-fast: any invalid rule throws `RuleConfigurationException` at boot. Checks:

- Mandatory fields: `ruleType`, `configBlockName`, `configEntryKey`, `uiInputRequired`.
- `ruleType` is a known external name.
- `min/max` present and ordered for `RangeCheck`.
- `defaultValue` present for `*OrBlockNotFound` (VTF-299).
- `ValidateOnlyInFilesWith` (if set) ∈ `ConfigFileMarker`.
- No duplicate (ruleType, block, entry, marker) tuples.

### Jackson config

`JacksonConfig` (`@Configuration`) wires the global `ObjectMapper`:
- `FAIL_ON_UNKNOWN_PROPERTIES = true` (intentional — surfaces frontend/backend contract drift early).
- `JavaTimeModule` registered.
- `WRITE_DATES_AS_TIMESTAMPS = false` (ISO-8601 strings).

---

## 9. `.ADC` File Format (Inferred)

Frauscher proprietary text format. Structure derived from parser code (no spec in repo).

### Layout

A file is a sequence of **blocks**. Each block:
- Header line — block name (e.g. `CFG_AXCNT`, `ID`).
- Entry lines — `key`, bit width, value, optional inline comment.

### Block taxonomy

**AEB-typical blocks**: `ID`, `CFG_BEHAV_TGGL`, `CFG_SECTION`, `CFG_ZP`, `CFG_ZP_FMA1`, `CFG_ZP_FMA2`, `CFG_TIMEOUT` (repeats), `CFG_OCC`, `CFG_RESET`, `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT`, `CFG_PROJECT_AEB`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1/FMA2`, `CFG_AUTORESET_FMA1/FMA2`, `COMPONENT`.

**IOEXB ACO additional**: `CFG_AXCNT`, `CFG_SECTION_OUT`, `CFG_COOP_RESET`.

**IOEXB DT additional**: `CFG_DATA_SAFETY_LEVEL`, `CFG_DATA_OUT`.

**COM-typical**: `ID`, `CFG_INTERVAL`, `CFG_PROJECT_COM`, `CFG_MY_IP_NW1/NW2`, `CFG_MY_MASK`, `CFG_INT_ID_DEST_NW1/NW2` (repeats), `CFG_FWRD_ACD` (repeats), `COMPONENT`.

### Repeating blocks

`CFG_TIMEOUT`, `CFG_SECTION_OUT`, `CFG_DATA_OUT`, `CFG_SUPERVIS_FMA1/FMA2`, `CFG_INT_ID_DEST_NW1/NW2`, `CFG_FWRD_ACD` may repeat. The parser tracks `totalOccurrence`, `blockIndex`, `startSequenceNumber`, `endSequenceNumber`. `CFG_TIMEOUT` is indexed and referenced by `SLCT_TIMEOUT` entries elsewhere.

### Component versions

The `COMPONENT` block carries a version field. AEB files have a value ≤ 3, COM files ≥ 4 (e.g. 105). This is a potential alternative discriminator for `comDetails` (see §10 known bug). VTF-290 made TYPE_IN1/2/3 and TYPE_AUX1/2 and SUPERVIS_COUNT_LMT `UIInputRequired=No` because GS05 components do not produce those entries.

---

## 10. Known Issues & Gotchas

### 1. COM file classification gap (KNOWN BUG)

`comDetails` is set only when `CFG_MY_IP_NW1` is present. COM files with minimal config (`CFG_INTERVAL` + `CFG_PROJECT_COM` only) are missed → AEB-only rules with `SkipComFile=true` then incorrectly run on them. Candidate fix: also trigger on `CFG_PROJECT_COM` or use `COMPONENT` version ≥ 4. Not yet fixed.

### 2. CFG_TIMEOUT parsing sequence (under investigation)

First timeout value appears last in validation output. Suspected ordering bug in `MultipleBlockMultipleInputMatchRule`'s actual-value iteration. VTF-295 partly addressed expected-value ordering via `LinkedHashSet`.

### 3. CFG_ZP input matching

Reported InputMatch anomaly on `CFG_ZP` block — needs investigation. May correlate with GS05/GS06 component version handling.

### 4. Multi-flag files

A single `.ADC` can legitimately have `acoIoexbDetails=true` and `dtIoexbDetails=true` simultaneously (PWR-2+ backplane). All extractors and rule selectors must treat the flags as independent — never as mutually exclusive.

### 5. `@JsonAnySetter` for user input

`UserValidationInputCriteria` accepts any nested structure. The backend does not pre-validate the schema; only rules with `UIInputRequired=Yes` are enforced. This is intentional (default rules consume arbitrary user inputs) but means typos in keys silently pass through.

### 6. `FAIL_ON_UNKNOWN_PROPERTIES = true`

Any new field added to a DTO without a matching frontend update will 400 the request. When you change `ParsedConfigFile` (e.g. flag rename in VTF-297), the frontend **must** match.

### 7. Block-occurrence enrichment lifetime

`enrichBlockOccurrences` runs at parse time. Anything downstream (extractors, rules) relies on the indices being already set. Do not construct `ConfigBlock` instances outside the parser without populating these fields.

### 8. Timeout unit conversion

`ConfigExtractionUtil.extractTimeoutValue` multiplies raw `TIMEOUT_VALUE` by 10 to produce milliseconds. Anywhere else reading timeouts must use the same helper or replicate the conversion.

### 9. No sample `.ADC` files in repo

All Cucumber tests use embedded JSON `ParsedConfigFile` representations. Real-file parsing is only exercised through manual upload tests.

### 10. Input file hygiene (pending)

No upload sanitisation of user `.ADC` files yet — open Phase 2 item.

---

## 11. Configuration Files

### `application.properties` highlights

| Property | Value | Source |
|---|---|---|
| `server.port` | 7443 | |
| `logging.level.root` | INFO | |
| `logging.file.name` | logs/configuration-validation-service.log | |
| `logging.logback.rollingpolicy.max-file-size` | 10MB | |
| `logging.logback.rollingpolicy.max-history` | 14 (days) | |
| `logging.logback.rollingpolicy.total-size-cap` | 200MB | |
| `management.endpoints.web.exposure.include` | health, info, metrics | actuator |
| `springdoc.swagger-ui.path` | /swagger-ui.html | |
| `cors.allowed-origins` | http://at-cvt01.frauscher.host:6443 | VTF-262 |
| `file.parser.extension` | .adc | |
| `server.tomcat.max-part-count` | 4096 | |
| `server.tomcat.max-http-post-size` | 52428800 (50 MB) | VTF-292 |

### `value-mappings.properties`

~150 entries. Format: `FIELD_KEY.RAW_VALUE = display label`. Consumed by `ValueMappingService.mapValue(fieldKey, rawValue)`. Used in extractor output, not in validation (validation matches raw values).

### `openapi.yaml`

Hand-maintained OpenAPI 3 spec. Important: when adding/changing DTO fields, update this file. `acoIoexbDetails` / `dtIoexbDetails` already replace the older `ioexbDetails`.

---

## 12. Testing Strategy

### Frameworks

- **Cucumber 7.15.0** + Spring + JUnit Platform Suite 1.10.2.
- Feature files under `src/test/resources/cucumber/features/{engine, extractors, rules}/`.
- Step definitions under `src/test/java/.../cucumber/stepdefs/{ValidationSteps, EngineSteps, ExtractorSteps, ConfigOptionsSteps}`.
- Test helpers: `CucumberHooks`, `TestContext` (ThreadLocal), `DataHelper`.

### Coverage map

| Area | Files | Coverage |
|---|---|---|
| Rule implementations (9 types) | ~8 feature files | Excellent — boundaries, missing-block, multi-value, defaults |
| Engine decisions | 4 feature files | Good — applicability, COM skip, default rule paths |
| Payload/config startup validation | 2 feature files | Excellent |
| Extractor services (8) | 8 feature files | One integration scenario per extractor |
| ConfigOptions API | 1 feature file | Good |
| **Gaps** | | |
| `CfgParserUtil` (raw `.ADC` parsing) | – | No real-file tests; all tests use pre-parsed JSON |
| Controller / HTTP layer | – | No `MockMvc` or `@WebMvcTest` tests |
| Excel generation | – | Not tested |
| Error response formatting | partial | Validation-layer scenarios only |

### Running tests

`./gradlew test` runs the full Cucumber suite. The runner is `CucumberSpringTestRunner` with `@SelectClasspathResource` pointing to `cucumber/features`.

---

## 13. Completed Tickets — Phase 1 Recap

Chronological summary of meaningful tickets. Use this when explaining "what changed and why" in technical docs.

### Foundation (VTF-130s–140s)

- **VTF-132 → VTF-146** — Core scaffolding: data models, DTOs, parser, validation framework, rule execution engine, extractor services, BDD test setup, REST controllers, exception handling, report generation.
- **VTF-26** — Dockerization (Dockerfile + CI build).
- **VTF-43** — Initial project setup.

### Validation rules & UX

- **VTF-252** — `/api/configoptions` API + SwaggerUI registration + parsing fix + BEHAV_IN3 description fix in `value-mappings.properties` + file-count-exceeded runtime error fix.
- **VTF-262** — CORS origin + port correction.
- **VTF-265** — Made TPF validation optional: 7 TPF rules use `UIInputRequired=No` with `InputMatch`. Default-value injection mechanism for TPF-dependent parameters (later removed in VTF-289).
- **VTF-266** — Excel report styling refresh: light grey theme with PASS/FAIL conditional formatting.
- **VTF-276** — `BEHAV_INPUT3.7` mapping update.
- **VTF-280** — Concurrency / immutability fixes:
  - `SummaryService` made stateless. `generateSummary()` now takes `List<ValidationResult>` as a parameter instead of relying on an instance field.
  - `ConfigOptionsService.configOptions` made `final`, initialized in constructor.
- **VTF-282** — `ProjectBlockCheck` config + rule-specific failure messages in `ValidationConstants`.
- **VTF-284** — Column headers all-uppercase; single-theme Excel styling.
- **VTF-289** — TPF validation made fully optional and switched to `InputMatch` exclusively (default-value injection removed).
- **VTF-290** — Component-version-aware skipping: 6 rules set `UIInputRequired=No` (`TYPE_IN1/2/3` in `CFG_AXCNT`, `TYPE_AUX1/2` in `CFG_SECTION_OUT`, `SUPERVIS_COUNT_LMT` in `CFG_ZP`). UI omits entries for GS05 components.
- **VTF-292** — `server.tomcat.max-http-post-size = 52428800` (50 MB) to fix 413 on 140+ file validate. Requires nginx `client_max_body_size 50m;`.
- **VTF-295** — `MultipleBlockMultipleInputMatchRule` switched `HashSet` → `LinkedHashSet` to preserve UI input order in expected values. Also addressed Timeout sequence ordering.
- **VTF-297** — Split `ioexbDetails` into `acoIoexbDetails` + `dtIoexbDetails`. 14 validation rules retargeted from `IOEXBDETAILS` → `ACOIOEXBDETAILS`. **Breaking API change** — frontend must read/send both flags. Documented in [split-ioexbdetails-flag.md](split-ioexbdetails-flag.md).
- **VTF-299** — `InputMatchOrBlockNotFound` extended with `DefaultValue` semantics: block missing → PASS only if absent value matches `DefaultValue`. Startup validator enforces `defaultValue` presence for `*OrBlockNotFound` rule types.
- **VTF-300** — New rule type **`OptionalInputMatchOrBlockNotFound`**: payload absent → skip; block missing → compare list against `DefaultValue`; block present → delegate to `OptionalInputMatch`. Used for `CFG_SECTION.RESET_OUT` and `CFG_AXCNT.BEHAV_INPUT3`. Multi-value bug fix (uses `asStringList()` + `Set.contains` instead of `asString().equals`). 5 Cucumber scenarios added.
- **VTF-303** — `EthernetDetail.destIpNw1`/`destIpNw2` changed from `String` to `List<String>` to surface all destination IPs per COM entry. `EthernetDetailExtractorService.buildIpAddressList()` iterates all `CFG_INT_ID_DEST_NW1/NW2` blocks (same pattern as `CFG_FWRD_ACD`). Excel and frontend handle the list automatically.

### Pending at VTF-305 boundary

- COM-file classification gap (see §10.1).
- CFG_TIMEOUT parsing sequence (§10.2).
- CFG_ZP InputMatch anomaly (§10.3).
- Input file hygiene checks (§10.10).
- CFG_SWITCH analysis (recommended `InputMatchOrBlockNotFound` for SWITCH_GE, SWITCH_GSF, PRERESET_ACT_TIME). Config-only + frontend fields; no parser/backend code changes needed. Not yet implemented.

---

## 14. Glossary for Doc Writers

| Term | Use in user-facing docs |
|---|---|
| **Rule** | The validation criterion for one `(block, entry)` pair. |
| **Block** | A named section in a `.ADC` file (e.g. `CFG_AXCNT`). |
| **Entry** | A single parameter inside a block (e.g. `BEHAV_INPUT1`). |
| **Marker** | The `ValidateOnlyInFilesWith` value gating a rule to a class of files. |
| **Detail sheet** | One of the per-concern tables in the Excel report (DP, Track Section, CHC, Supervisor, IOEXB Behaviour, IOEXB ACO, Data Transmission, Ethernet). |
| **Validation summary** | The combined JSON response containing all `ValidationResult`s + all detail lists. |
| **DP** | Distribution / Detection Point — one AEB-controlled track section endpoint. |
| **FMA** | Track section (a counting-head pair). |
| **GS05 / GS06 / GS07** | Hardware component generations; affect which entries are present. |
| **Default rule** | A `InputMatch` rule auto-generated by the engine for a user input with no configured rule. |
| **Default value** | A baked-in expected value used by `*OrBlockNotFound` rules when the block is absent. |

---

## 15. Pointers for Producing Technical Docs

When Claude (web) writes user-facing technical documentation, consider the following audience splits and which sections of this handover map to them.

| Audience | Sections to source from |
|---|---|
| End user (safety engineer using the UI) | §1, §5 (semantics only, not table), §7 (POST endpoints described as flows), §9 (high-level), §12 (Excel sheets) |
| Frontend developer | §4 (DTOs), §7 (full), §10 (gotchas 5, 6), §13 (VTF-297, VTF-303 API breaks) |
| Backend developer onboarding | All sections, with emphasis on §2, §3, §6, §8, §13 |
| QA / test author | §6, §12, §10, §13 |
| Ops / SRE | §1, §3 (deployed env), §11, §10.10 |
| Architect / reviewer | §3, §6, §8, §10, §13 |

### Things to avoid in user-facing docs

- Do not expose internal rule names (`INPUT_MATCH_OR_BLOCK_NOT_FOUND`) without their external counterpart (`InputMatchOrBlockNotFound`).
- Do not document `DefaultValue` semantics without also flagging that the user input still drives validation when present — `DefaultValue` only matters when the block is **absent**.
- Do not describe the four classification flags as mutually exclusive.
- Do not promise validation of `.ADC` files without user input — only `UIInputRequired=No` rules and `RangeCheck`/`DuplicateCheck` run without input.
- Do not mention `/docs` folder paths in Jira / MR descriptions (project convention).

---

*End of Phase 1 handover — current to develop @ VTF-305 prep.*
