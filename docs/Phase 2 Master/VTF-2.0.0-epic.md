# Epic: VTF Phase 2 (VTF-304)

- **Epic Name:** VTF Phase 2
- **Epic Key:** VTF-304
- **Component:** Validation Tool FR-IN
- **Version:** VTF 2.0.0

> *Revision note: this supersedes an earlier Phase 2 epic draft (seven-cluster framing, pre-preprocessor architecture). This version reflects the May 2026 design freeze, the June 2026 contract work (VTF-328/329/330), and the backend build stories imported as VTF-331 through VTF-345.*

## Summary

Phase 2 extends the Frauscher Configuration Validation Tool beyond Phase 1's single-file, ADC-only rule checks to project-specific validation that cross-correlates the delivered `.ADC` files against the design baseline. Two new upload endpoints parse the baseline (Baseline FCT archive, PDQ workbook) into reference values; a new v2 validate path runs a validate-time preprocessor that builds an Expectations JSON, which the existing rule engine consumes through a new composite-key lookup. Six validation clusters are scoped, plus supporting workstreams.

## Background

Phase 1 (FCVT 1.0.0) shipped in early April 2026: stateless Spring Boot backend, 41 configured ADC-only validation rules, declarative JSON rules validated at boot, Cucumber BDD coverage (157 scenarios) as the contract, and a five-endpoint REST surface. Phase 1 consumed ADC files plus a small `userInput` JSON and produced an Excel report.

Phase 2 was scoped across the March–April 2026 requirements meetings (Salai, Suresh, Max, the Chandrashekhar team) and locked at the May 2026 design freeze. The validation clusters target configuration blocks in the uploaded ADC files; the new input files (Baseline FCT, PDQ) are parsed so their values can serve as reference values during ADC validation.

## Architecture (Phase 2)

- **Two new upload endpoints.** `POST /api/upload/fct` parses the FCT2 archive (`Project.xml` only) and returns the ComAebMap. `POST /api/upload/pdq` parses the PDQ workbook and returns parsed JSON (`projectCode`, `aebEquipmentVersion`, block-grouped `cqIrParameters`, `controlTable`, conditional `dataTransmission`).
- **New v2 validate path.** `POST /api/config/v2/validate` is added. The Phase 1 `POST /api/config/validate` path is untouched and continues to serve the manual-fallback / no-upload case.
- **Coupled-artifacts gate.** The v2 path requires both FCT and PDQ. Neither uploaded uses the Phase 1 path; only one uploaded is an invalid state (the UI prevents it). Gate failure returns `HTTP 400 PHASE2_INPUTS_INCOMPLETE`. The tpf upload is independent of the gate.
- **Validate-time preprocessor (in-memory, stateless).** Built fresh per request from the FCT ComAebMap and the parsed PDQ. It produces an *Expectations JSON* keyed `(ADC file, block, instance, entry) → expected value`. Source-of-truth rule: ADC files are validation *targets* only; expectations come from the FCT + PDQ baseline, never from the artifact under test.
- **Validate-time cross-correlation rules.** Three consistency rules span files or blocks: project-block consistency (`CFG_PROJECT_AEB` / `CFG_PROJECT_COM`), dual-FMA consistency (`RESET_TYPE` / `RESET_DELAY` across `CFG_SUPERVIS_FMA1` / `FMA2`), and `CFG_IP_SWITCH` derivation from the FCT `redundantComPresent` field.
- **Engine extension.** The existing 9 rule types stretch to cover Phase 2 — no new rule types are required structurally. What is needed is a *composite-key lookup mode* for `(file, block, instance, entry)`; the validation key widens from `(block, entry)` to `(ADC file, block, instance, entry)`. Lookup mechanism (JSONPath in rule config vs named Java lookups) is open.
- **Rule Registry.** Finalises the Phase 2 `ruleType` vocabulary as cluster rules are built.
- **v2 response shape.** Preserves the Phase 1 shape: `validation_results[]` plus the eight detail tables (`dp_details`, `track_section_details`, `chc_details`, `supervisor_details`, `ioexb_behaviour_details`, `ioexb_aco_details`, `data_transmission_details`, `ethernet_details`). A detail-table cell gains a sibling `<field>_expected` key only on mismatch (no per-cell status flag). `validation_results[]` keeps the seven-field Phase 1 row shape. A zero-mismatch v2 response is shape-compatible with a Phase 1 response.

## Scope — Validation Clusters (6)

- **Cluster 1 — CAN Segment.** Cross-COM/AEB ID resolution, `SLCT_TIMEOUT`, and `CFG_FWRD_ACD` forwarding. Owns `ID` and `SLCT_TIMEOUT` validation across every in-scope block (`CFG_ZP_FMA1`, `CFG_ZP_FMA2`, `CFG_SECTION_OUT`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1`, `CFG_SUPERVIS_FMA2`, `CFG_DATA_OUT`), plus `CFG_FWRD_ACD` forwarding entries. Check A: per-AEB identity + expected `SLCT_TIMEOUT` from ComAebMap segment membership. Check B: virtual forwarding set per channel. Source: Baseline FCT (ComAebMap) + PDQ Config Control Table. Report sheet: Ethernet Details.
- **Cluster 2 — IOEXB ACO.** IoExb position within the board rack and number of ACO IoExbs per AEB. Block: `CFG_SECTION_OUT` (block count ≤ 16 per file). Keys: `ID`, `SECTION` — position-within-rack and IoExb-count semantics, distinct from Cluster 1's `ID`/`SLCT_TIMEOUT` (both run on the same block; failure modes do not overlap). Source: Baseline FCT (ComAebMap `acoIoExbs`). Report sheet: IOEXB ACO Details.
- **Cluster 3 — Track Section.** Four sub-checks: (1) CHC counting-head assignment per sensor/ADC, (2) correct sensors per track section, (3) supervisory track config, (4) ACO/DT IO assignment. **All four validate the ADC against the baseline** (Control Table `dpIn`/`dpOut` + `fadcAutoReset`); the baseline (PDQ + FCT) is the sole source of truth, so **no external input is required**. Source: PDQ Config Control Table. Report sheet: Track Section Details.
- **Cluster 4 — CHC / External CHC (combined).** Same block, different validation approaches per sub-cluster. CHC count derivation is locked: `BEHAV_IN3 = 7 → 1 CFG_CONTROL block`; `BEHAV_IN3 = 6 → 2 CFG_CONTROL blocks`. External CHC: `CFG_CONTROL ≤ 2` per ADC. Adjacency derived from the Config Control Table Track table; also touches `CFG_ZP` for adjacency. Full blocks/keys and sub-cluster split pending AE confirmation. Source: PDQ Config Control Table. Report sheets: IOEXB Behaviour Details (CHC count) and CHC Details (external CHC).
- **Cluster 5 — Supervisor.** FMA Supervisor validation (`CFG_SUPERVIS_FMA1` / `CFG_SUPERVIS_FMA2`). Blocks and keys pending AE confirmation; 3B FMA Supervisor block detail to be re-confirmed against the Confluence requirements doc. Source: PDQ Config Control Table. Report sheet: Supervisor Details.
- **Cluster 6 — Data Transmission.** Blocks locked: `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVELS`. (The earlier `CFG_DATA_IO SAFETY_LEVEL_IN/OUT` reference was incorrect and is discarded.) Full keys list and rule semantics pending. Source: PDQ Data Transmission Output subsheet. Report sheet: Data Transmission Details.

## Scope — Supporting Workstreams

- **CFG_SWITCH block validation** — `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME` (carries over the Phase 1 pending CFG_SWITCH work via `InputMatchOrBlockNotFound`).
- **PDQ workbook ingestion** — CQ-IR subsheet (Config Key column parse model, block-grouped `cqIrParameters`), ConfigControlTable subsheet (DP-name literal match against `parsedConfigFile`), and the conditional Data Transmission Inputs subsheet.
- **Baseline FCT archive ingestion** — FCT2 archive, `Project.xml` parsing, necessary-info-only extraction into the ComAebMap; trackplan XMLs out of scope.
- **UI form freeze (read-only)** after a successful PDQ parse, with project code surfacing.
- **Validation output console (Phase 2 result shape)** — detail-table mismatch annotations plus verdict rows.
- **PDF report output** consolidating Validation Input and Validation Results (stretch).

## Out of Scope

- **COM file IP validation** — deferred. If reintroduced later, static checks only (format validity, distinctness, existence in expected list), not file-sourced cross-referencing.
- **3G as a separate cluster** — subsumed into Cluster 1 (CAN Segment); no non-AEB ID references exist between COM files.
- **Trackplan XMLs inside the FCT archive** — only `Project.xml` is parsed.
- **Counting-head-output mode IoExbs** — only ACO and DT modes are in scope; the FCT is rejected if a counting-head-output mode IoExb is present.
- **Hidden content inside the PDQ workbook** — the parser honours sheet/row/column visibility flags; hidden content is not parsed.

## Architectural Decisions Carried Forward

- **External inputs, not a frontend-driven control table.** The March decision to reject a UI-form-based internal control table stands. Control table information is sourced from the project-team-supplied PDQ workbook (ConfigControlTable subsheet), parsed alongside CQ-IR.
- **DP-name literal match for ConfigControlTable.** ID-based matching is not feasible (the PDQ does not carry the same identifiers as the ADC files). ConfigControlTable rows are matched against `parsedConfigFile` on DP name by exact literal comparison; case and whitespace are significant.
- **Stateless backend, no database.** No server-side persistence for uploaded files, parse results, or validation outcomes. The validate-time preprocessor is an in-memory object scoped to a single request.
- **Cucumber BDD coverage is extended, not replaced.** Phase 2 adds feature files and scenarios; the Phase 1 feature files run unchanged and must continue to pass.

## Target Delivery

Formal Phase 2 delivery: end of July 2026.

## Child Stories

### Contract stories (created)

| Key | Title | Endpoint |
|---|---|---|
| VTF-328 | FCT upload contract | `POST /api/upload/fct` |
| VTF-329 | PDQ upload contract | `POST /api/upload/pdq` |
| VTF-330 | Validate v2 contract (request + response) | `POST /api/config/v2/validate` |

### Frontend (keys pending)

| Label | Title |
|---|---|
| FE-01 | UI support for CFG_SWITCH block inputs |
| FE-02 | Upload controls and endpoint wiring for Baseline FCT and PDQ |
| FE-03 | Freeze validation form as read-only after successful PDQ parse (with project code surfacing) |
| FE-04 | Validation output console for the Phase 2 result shape |

### Backend — input parsers

| Key | Label | Title |
|---|---|---|
| VTF-331 | BE-01 | PDQ Parser: CQ-IR subsheet (Config Key model, block-grouped `cqIrParameters`) |
| VTF-332 | BE-02 | PDQ Parser: ConfigControlTable subsheet (DP-name literal match) + conditional Data Transmission Inputs subsheet |
| VTF-333 | BE-03 | Baseline FCT Parser (FCT2 archive, `Project.xml` extraction → ComAebMap) |

### Backend — validate-path infrastructure

| Key | Label | Title |
|---|---|---|
| VTF-334 | BE-04 | v2 validate endpoint and coupled-artifacts gate (`POST /api/config/v2/validate`, response assembly) |
| VTF-335 | BE-05 | Validate-time preprocessor and Expectations JSON (incl. the three cross-correlation rules) |
| VTF-336 | BE-06 | Rule engine composite-key lookup extension (`(file, block, instance, entry)`) |
| VTF-337 | BE-07 | Rule Registry and Phase 2 `ruleType` finalisation (`validation_results[]` + detail-cell `_expected` wiring) |

### Backend — validation clusters

| Key | Label | Title |
|---|---|---|
| VTF-338 | BE-08 | Cluster 1: CAN Segment (Check A identity/SLCT_TIMEOUT, Check B virtual forwarding) |
| VTF-339 | BE-09 | Cluster 2: IOEXB ACO (`CFG_SECTION_OUT` position + ACO count against Baseline FCT) |
| VTF-340 | BE-10 | Cluster 3: Track Section (four sub-checks; all validate the ADC against the baseline) |
| VTF-341 | BE-11 | Cluster 4a: CHC (`BEHAV_IN3` count-driven cross-check against `CFG_CONTROL` block count) |
| VTF-342 | BE-12 | Cluster 4b: External CHC (`CFG_CONTROL` counting-head control check, ≤ 2 per ADC) |
| VTF-343 | BE-13 | Cluster 5: Supervisor (`CFG_SUPERVIS_FMA1` / `FMA2` supervisor table checks) |
| VTF-344 | BE-14 | Cluster 6: Data Transmission (`CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVELS`) |

### Backend — output

| Key | Label | Title |
|---|---|---|
| VTF-345 | BE-15 | PDF report output *(stretch goal; not on the critical path)* |

## Acceptance Criteria

**Scenario 1: Coupled-artifacts gate routes the validate path correctly**

- **Given** the v2 validate path is available
- **When** both an FCT and a PDQ have been uploaded → validation runs via `POST /api/config/v2/validate`
- **When** neither has been uploaded → validation runs via the unchanged Phase 1 `POST /api/config/validate` path
- **When** only one of the two has been uploaded → the request is rejected with `HTTP 400 PHASE2_INPUTS_INCOMPLETE`

**Scenario 2: Expectations are sourced from the baseline, never from the artifact under test**

- **Given** an FCT and a PDQ have been parsed
- **When** the validate-time preprocessor builds the Expectations JSON
- **Then** every expected value is derived from the FCT ComAebMap and the parsed PDQ
- **And** no expected value is read from the ADC files
- **And** the ADC values are treated only as the actuals to be compared

**Scenario 3: v2 response preserves the Phase 1 shape and annotates mismatches**

- **Given** a v2 validation run completes
- **When** the response is produced
- **Then** it carries `validation_results[]` (seven-field rows) plus the eight detail tables
- **And** a detail-table cell carries a sibling `<field>_expected` key only when that cell mismatches
- **And** a run with zero mismatches is shape-compatible with a Phase 1 response

**Scenario 4: No regression in Phase 1 output for unchanged inputs**

- **Given** an ADC dataset and `userInput` payload accepted by FCVT 1.0.0
- **When** the same inputs are submitted to FCVT 2.0.0 via `/api/config/validate`
- **Then** the report is functionally equivalent to the Phase 1 output for the 41 Phase 1 rules
- **And** no Phase 1 rule is silently dropped, renamed, or reordered

**Scenario 5: Every new rule and parser is covered by Cucumber BDD**

- **Given** a Phase 2 story is marked Done
- **When** the CI pipeline runs the BDD suite
- **Then** at least one feature file exists for each new rule or parser introduced by that story
- **And** happy-path, malformed-input, and missing-input scenarios are covered
- **And** the Phase 1 feature files continue to pass unchanged

**Scenario 6: FCT ingestion is scoped to `Project.xml` only**

- **Given** an agreed list of `Project.xml` elements in scope for FCT ingestion
- **When** the FCT parser runs
- **Then** only fields on that list are extracted from `Project.xml`
- **And** trackplan XML files in the archive are ignored
- **And** a counting-head-output mode IoExb causes the upload to be rejected

**Scenario 7: ConfigControlTable matches `parsedConfigFile` on DP name by literal comparison**

- **Given** a PDQ workbook with a ConfigControlTable subsheet
- **And** a set of ADC files parsed into `parsedConfigFile`
- **When** ConfigControlTable rows are matched against `parsedConfigFile`
- **Then** matches are determined by exact case-sensitive, whitespace-sensitive DP-name comparison
- **And** mismatches are reported with both the PDQ value and the ADC value
- **And** unmatched rows from either side are returned as distinct categories rather than silently dropped

**Scenario 8: Phase 2 release ships all in-scope clusters and workstreams together**

- **Given** all six validation clusters and all supporting workstreams are complete
- **And** the PDF report output stretch story is either complete or explicitly deferred with a target release
- **When** the FCVT 2.0.0 release is cut on or before end of July 2026
- **Then** all six clusters are executable end-to-end in the released build
- **And** the release notes enumerate every cluster and workstream delivered with their originating child story IDs
- **And** any deferred items are listed explicitly with a target release
