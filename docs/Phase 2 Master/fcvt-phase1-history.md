# FCVT — Phase 1 History

The story of how Phase 1 came to be — from initial scaffolding through the v1.0.0 release in early April 2026. Companion to `fcvt-codebase.md` (the reference for what the system is *now*) and `fcvt-phase2-design.md` (what's being built next).

Where Phase 1 history overlaps with the live codebase, codebase.md owns the reference tables and definitions — this document narrates the journey and links back rather than restating.

---

## Setting

FCVT was conceived as a **designer-side preflight** in the AE order-processing workflow. An AE designer authors the project configuration in FCT (a design tool optimised for authoring, not for producing production-ready output end-to-end), exports the `.ADC` files, performs a manual line-by-line self-check, and hands off to a verifier for the independent **four-eyes** review. The verifier traditionally prints each ADC file and reviews on paper; rejected files get rectified by the designer, resubmitted, and the full set is reprinted for the next verification pass.

At scale — projects with 200+ detection points under time pressure — both the designer's self-check and the print-review-reject-reprint cycle break down. Mechanical errors (key spelling, value-range, block-presence) that a rule engine could catch upfront end up consuming verifier hours, project time, and considerable paper.

FCVT sits inside the designer's workflow, between FCT export and the verifier handoff. The verifier still owns the final 4-eyes pass; FCVT just ensures they receive cleaner input. The Phase 1 ambition was narrow on purpose: validate single `.ADC` files against declarative project-independent rules, with the user supplying project-specific expected values via UI dropdowns. Project-specific cross-validation against design-baseline artifacts (FCT2 archive and PDQ workbook) is what Phase 2 adds.

The codebase that resulted from this is the one documented in `fcvt-codebase.md`. The path to it is the rest of this document.

---

## 1. Foundation (early VTF tickets)

The earliest tickets put the scaffolding in place:

- **VTF-43** — initial project setup.
- **VTF-26** — Dockerization (Dockerfile + GitLab CI build).
- **VTF-132 → VTF-146** — core scaffolding spanning data models, DTOs, the `.ADC` parser, the validation framework, the rule execution engine, the extractor services, BDD test setup, REST controllers, exception handling, and report generation.

By the end of this phase the system already had its enduring shape: a Spring Boot service with the 5 endpoints in their current form (see codebase.md §5), the Cucumber test harness, and a first rule set in `ValidationConfiguration.json`. The architecture decisions made here have all survived to VTF-305: stateless, no database, declarative rules in JSON, classification-flag-driven file applicability, and Apache POI for Excel reports.

---

## 2. Rule and extractor maturation

Through the VTF-252 to VTF-292 range, the rules engine and the surrounding ergonomics got fleshed out. The pattern in this period was a steady stream of relatively small, focused tickets — each one a polish to something already working.

### Dropdown plumbing and CORS

**VTF-252** added `/api/configoptions` (the UI dropdown source), wired it into Swagger, fixed a `value-mappings.properties` parsing issue, corrected the `BEHAV_IN3` description there, and patched a file-count-exceeded runtime error. **VTF-262** corrected the CORS origin and port (the now-canonical `http://at-cvt01.frauscher.host:6443`).

### Excel report styling

**VTF-266** refreshed the Excel report styling — light grey theme with PASS/FAIL conditional formatting. **VTF-284** made column headers all-uppercase and consolidated to a single Excel theme. The current `ExcelStyleManager` look comes from this pair of tickets.

### TPF validation — a two-step evolution

TPF (Train Protection Function) validation went through an arc. **VTF-265** initially made TPF validation optional: 7 TPF-related rules switched to `UIInputRequired=No` with `InputMatch`, and a default-value injection mechanism was introduced for TPF-dependent parameters. **VTF-289** then went further — TPF validation was made fully optional, switched to `InputMatch` exclusively, and the default-value injection mechanism was removed entirely. The two-step path reflects how the team's understanding of what "optional" should mean evolved between the two tickets.

(VTF-265 itself was the cover ticket for the much larger Feb 2026 frontend-payload campaign — see §3.)

### Mapping fixes

**VTF-276** updated the `BEHAV_INPUT3.7` mapping in `value-mappings.properties`.

### Concurrency and immutability

**VTF-280** addressed a stateful-singleton problem: `SummaryService` was holding a `List<ValidationResult>` as an instance field, which would cause cross-request contamination under concurrency. The fix:

- `SummaryService.generateSummary(...)` now takes `List<ValidationResult>` as a parameter.
- `ConfigOptionsService.configOptions` was made `final`, populated in the constructor. Property reload now requires a restart by design.

This is what the §3.8.1 thread-model and §11 statelessness notes in codebase.md ultimately reference — VTF-280 is the ticket that made the service genuinely stateless. The lone surviving mutable singleton (`DuplicateValueRegistry`) is acceptable because it's `.clear()`-ed at the top of every `/validate` request.

### ProjectBlockCheck configuration

**VTF-282** wired in the `ProjectBlockCheck` rule's configuration shape (`BLOCK_EXISTS` + `PROJECT_NUMBER`) and added rule-specific failure messages in `ValidationConstants`. The single configured `ProjectBlockCheck` on `CFG_PROJECT_AEB.PROJECT_NUMBER` (row 28 of the codebase.md §7 rules table) traces to here.

### Component-version awareness

**VTF-290** introduced version-aware skipping for GS05 components, which produce a smaller block/entry set than GS06/07. Six rules became `UIInputRequired=No`: `TYPE_IN1/2/3` in `CFG_AXCNT`, `TYPE_AUX1/2` in `CFG_SECTION_OUT`, and `SUPERVIS_COUNT_LMT` in `CFG_ZP`. The UI omits these entries for GS05 components; the rules still exist but no longer require input.

### Multipart-size bump

**VTF-292** raised `server.tomcat.max-http-post-size` from the default to 52428800 (50 MB) to fix HTTP 413 errors on validation of 140+ files (~2–3 MB JSON per ~140 ADCs). Required a corresponding nginx `client_max_body_size 50m;`. The combined limit is the one in codebase.md §2.

---

## 3. The February 2026 validation campaign

This is the most significant single chapter in Phase 1. By mid-Feb 2026 the system was functional but its actual validation outcomes against real customer data were poor — and nobody was sure how poor or why. Backend ran an end-to-end campaign that produced (a) a categorised list of 13 issues, (b) discovery of five frontend payload contract bugs, and (c) eventually a more than doubling of the pass rate.

### Pre-fix baseline (early Feb 2026)

Running 29 ADC files through `/api/config/validate` produced **1,076 result rows** with a **38.8% pass rate** (418 PASS / 658 FAIL). Failures were classified into 13 issues:

| Severity | Count | Issue IDs |
|---|---|---|
| Critical | 1 | ISS-009 |
| High | 4 | ISS-001, ISS-003, ISS-004, ISS-006 |
| Medium | 2 | ISS-008, ISS-012 |
| Low (observation) | 2 | ISS-010, ISS-011 |
| None (passing) | 4 | ISS-002, ISS-005, ISS-007, ISS-013 |

The pattern of failures clustered into four recurring root causes:

**Pattern 1 — `InputMatch` all-file check (ISS-001, ISS-008, ISS-012).** `InputMatch` rules were validating against every uploaded file regardless of whether the relevant block existed in that file. Track-section and IOEXB blocks were being checked against COM files (which legitimately don't contain them), producing `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` for every file/rule pair. The fix was to introduce file-type filtering — both via the `SkipComFile` flag in `RuleConfig` and via the `ValidateOnlyInFilesWith` marker that referenced `ConfigFileMarker` values (`ACOIOEXBDETAILS`, `DTIOEXBDETAILS`, `TRACKSECTIONDETAILS`, `COMDETAILS`). This is what made the decision-engine logic in codebase.md §6 the way it is.

**Pattern 2 — Block not found in default configs (ISS-003, ISS-004).** `CFG_OCC` and `CFG_RESET` blocks are not present in default configuration files, so every check against them failed. Resolution path: make the corresponding rules `InputMatchOrBlockNotFound` so absence is acceptable — this is what those four rules (codebase.md §7 rows 24–27) look like today.

**Pattern 3 — Parser/lookup bug for CFG_TIMEOUT (ISS-009, Critical).** `CFG_TIMEOUT` exists in every file but the engine was returning `CONFIG_BLOCK_OR_PARAM_NOT_FOUND`. Sample failures from the bug hunt referenced the entry as `TIMEOUT_VALUED` (with a stray `D`) — a frontend key typo that the backend was unable to match given `FAIL_ON_UNKNOWN_PROPERTIES=true` semantics on its own keys but loose `@JsonAnySetter` semantics on inputs (codebase.md §13 gotcha #5). Resolution required both a frontend key fix and the new `MultipleBlockMultipleInputMatch` rule shape that accepts an array of timeout values (now row 29 of the rules table).

**Pattern 4 — Category classification observations (ISS-010, ISS-011).** `CFG_TROLLEY_SUPP` and `CFG_TYPE_PRTCT` were both passing but had been raised as observations to clarify whether they belonged under a separate `CFG_TPF` category. These were no-code-change items pending domain-team review.

The 4 already-passing rules in the pre-fix run (`CFG_BEHAV_TGGL`, `CFG_TPF`, `CFG_RSR_TYPE`, `ID`) confirmed that the basic `InputMatch` and `RangeCheck` mechanics worked end-to-end when the block was actually present in all files.

### Frontend payload contract bugs (raised 2026-02-27)

In parallel with the bug-hunt, integration testing surfaced **five issues** in the frontend's `/api/config/validate` payload — all preventing validation from running at all on the affected blocks:

| # | Block | Issue | Severity | Action |
|---|---|---|---|---|
| 1 | `CFG_PROJECT_AEB` | Frontend sent `BLOCK_EXIST: "0"` (string); backend expects `BLOCK_EXISTS: false` (boolean) — wrong key spelling **and** wrong type | Blocker | Frontend fix |
| 2 | `CFG_TIMEOUT` | Frontend sent indexed keys `TIMEOUT_VALUE0/1/2`; backend expects `TIMEOUT_VALUE: [...]` array. Empty-string entries to be excluded. | Blocker | Frontend fix |
| 3 | `CFG_SECTION_OUT` | Indexed/prefixed keys: `CLR_OCC1`, `TYPE1_AUX1`, `TYPE1_AUX2`, `AUX1_OUT1`, `AUX2_OUT1` instead of `CLR_OCC`, `TYPE_AUX1`, `TYPE_AUX2`, `AUX1_OUT`, `AUX2_OUT` | Blocker | Frontend fix |
| 4 | `CFG_AXCNT` | Frontend missing `TYPE_IOEXB` (`UIInputRequired=Yes`); backend rejected the request | Blocker | Frontend fix |
| 5 | `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVEL`, `CFG_MY_IP_NW1` | Frontend not sending these sections at all | Deferred | Backend rules removed for now; to be re-added when frontend UI catches up |

The deeper issue exposed by these five was structural: the backend performs **exact string matching** on all keys, with no normalization, trimming, or index-stripping. Every `userInput` key has to match the corresponding `ConfigEntryKey` in `ValidationConfiguration.json` and the entry keys in parsed ADC files exactly. Indexed frontend keys (a UI convenience for repeating sub-fields) had to be converted to backend-shaped arrays in the frontend. The complete corrected `userInput` structure that resulted from these fixes is now the reference payload shown in codebase.md §9.

Backend deliberately did **not** add normalisation — the strict match is intentional, because letting silent normalisation through would mask future contract drift. The same `FAIL_ON_UNKNOWN_PROPERTIES=true` philosophy applies.

### Post-fix snapshot (VTF-265 close, 2026-02-27)

The 29-file run, replayed after the corrections:

| Metric | Before | After | Δ |
|---|---|---|---|
| Total results | 1,076 | 846 | reduced noise |
| PASS | 418 (38.8%) | 734 (86.8%) | **+48.0 pp** |
| FAIL | 658 (61.2%) | 112 (13.2%) | –48.0 pp |

By rule type, after fixes:

| Rule type | PASS | FAIL | Total |
|---|---:|---:|---:|
| `DuplicateCheck` | 29 | 0 | 29 |
| `RangeCheck` | 29 | 0 | 29 |
| `ProjectBlockCheck` | 28 | 0 | 28 |
| `MultipleBlockMultipleInputMatch` | 28 | 0 | 28 |
| `InputMatch` | 400 | 52 | 452 |
| `InputMatchOrBlockNotFound` | 220 | 60 | 280 |
| **Total** | **734** | **112** | **846** |

The 112 remaining failures split into three categories:

- **32 legitimate value mismatches** — `CFG_SECTION.BEHAV_GE` (frontend 0 / file 1) and `CFG_SECTION.RESET_OUT` (frontend 2 / file 1) across 16 track-section files. Real validation findings; the engine was working correctly. Action: verify frontend form defaults.
- **56 block-or-parameter-not-found failures** — `CFG_PROJECT_AEB.PROJECT_NUMBER` failing on all 28 non-COM files (block absent → default-rule path runs `InputMatch` and fails); `CFG_ZP.SUPERVIS_COUNT_LMT` failing on all 28 non-COM files (entry missing from existing block). The second one surfaced the open question: does `InputMatchOrBlockNotFound` handle missing entries within existing blocks the same way it handles missing blocks? (See codebase.md §13 known issues.) Action: conditional logic tied to `BLOCK_EXISTS` for the first; rule-type investigation for the second.
- **24 IOEXB-specific entry-missing failures** — `TYPE_IN1/2/3`, `TYPE_IOEXB`, `TYPE_AUX1/2` missing in 4 specific IOEXB files. Hypothesis: older firmware / different IOEXB configurations don't produce these entries. This is what eventually drove **VTF-290**'s GS05-aware `UIInputRequired=No` treatment (see §2 above).

The `ValidationConfiguration.json` snapshot at VTF-265 had **42 rules** — already with the 10 deferred rules for `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVEL`, `CFG_MY_IP_NW1` removed pending frontend UI work. Between VTF-265 and VTF-305 the net change is –1 rule (current state is 41).

### Why this chapter matters

The Feb 2026 campaign is the inflection point where FCVT stopped being "a working validation skeleton" and became "a validation tool customers can use." Three things came out of it that are now structural:

1. **The strict-match-no-normalisation contract** between frontend and backend `userInput` keys is now load-bearing.
2. **File-type markers** (`ValidateOnlyInFilesWith` + the four `ConfigFileMarker` enum values) are the primary mechanism for scoping rules to applicable file classes.
3. **Block-absence is a first-class outcome** in the rule set — the `*OrBlockNotFound` family of rule types and the `DefaultValue` mechanism are the result.

---

## 4. Late-Phase-1 contract evolution

After the Feb 2026 campaign, several tickets evolved the rule type set and the parser/extractor contract in ways that would have been disruptive earlier in Phase 1 but were defensible once the system was stable. Three of these (VTF-297, VTF-299, VTF-300) added or shifted rule semantics; one (VTF-303) extended a detail-model field's type.

### VTF-295 — input order preservation

`MultipleBlockMultipleInputMatchRule` switched its expected-values store from `HashSet` to `LinkedHashSet` so the UI input order is preserved in expected values. Same ticket also addressed timeout sequence ordering. This is what makes the current `MultipleBlockMultipleInputMatch` rule (codebase.md §7, row 29) deterministic in result output.

### VTF-297 — split `ioexbDetails` (breaking change)

The single `ioexbDetails` boolean flag on `ParsedConfigFile` conflated two distinct hardware scenarios:

- **ACO IOEXB boards** produce `CFG_AXCNT` and `CFG_SECTION_OUT` blocks.
- **DT IOEXB boards** produce `CFG_DATA_SAFETY_LEVEL` and `CFG_DATA_OUT` blocks.

An AEB can have only ACO, only DT, or both (on PWR-2+ backplanes). The single flag was forcing all three IOEXB-relevant extractors to run on every IOEXB-flagged file even when the relevant blocks were absent, and it was making CFG_AXCNT/CFG_SECTION_OUT validation rules attempt to run on DT-only files.

The fix was a split into two independent boolean flags — `acoIoexbDetails` and `dtIoexbDetails` — that can both be `true` on the same file (multi-flag files are now a permanently supported case, codebase.md §13 gotcha #4). Files touched:

- **Model:** `ParsedConfigFile.java` (constructor field order changed — Lombok `@AllArgsConstructor` makes this matter).
- **Parser:** `CfgParserUtil.java` (the single block-detection `if` split into two).
- **Validation engine:** `ConfigFileMarker.java` (enum `IOEXBDETAILS` → `ACOIOEXBDETAILS` + `DTIOEXBDETAILS`); `FileApplicability.java` (lambda dispatch updated); `RuleExecutionEngine.java` (`isFileEligible()` switch).
- **Validation rules:** `ValidationConfiguration.json` — 14 rules retargeted from `IOEXBDETAILS` to `ACOIOEXBDETAILS` (the CFG_AXCNT and CFG_SECTION_OUT rules in codebase.md §7 rows 10–23). No DT-specific rules existed yet.
- **Extractors:** `IOEXBBehaviourExtractorService`, `IOEXBAcoExtractorService` filtered on `acoIoexbDetails`; `DataTransmissionExtractorService` filtered on `dtIoexbDetails`.
- **API contract:** `openapi.yaml` updated.
- **Tests:** `ExtractorSteps.java`, `EngineSteps.java`, `DataHelper.java` step-definition signatures changed; 9 feature files updated (`file-eligibility`, `engine-buisness-logic`, `ioexb-behaviour-extractor`, `ioexb-aco-extractor`, `data-transmission-extractor`, `default-rule-executor`, `rule-execution-engine`, `validation-decision-engine`, plus the cross-cutting JSON updates).

VTF-297 was the most disruptive single ticket of late Phase 1 — **breaking API change for the frontend**, which had to start reading and sending both flags instead of the single `ioexbDetails`. The change was justified by the structural cleanliness it produced; the cost was one frontend release coordinated with the backend deploy.

### VTF-299 — DefaultValue semantics on `*OrBlockNotFound`

`InputMatchOrBlockNotFound` was extended so block-absence only PASSes when the absent value matches `DefaultValue`. The startup validator was strengthened to require `DefaultValue` on every `*OrBlockNotFound` rule (codebase.md §7 startup invariant #5). This is what makes the `DefaultValue` column non-blank on every `*OrBlockNotFound` row in the codebase.md §7 rules table.

### VTF-300 — new rule type `OptionalInputMatchOrBlockNotFound`

A new rule type was added with three-way semantics:

- Payload absent → skip.
- Block missing → PASS iff `DefaultValue` ∈ user's expected list.
- Block present → delegate to `OptionalInputMatch`.

Used for two entries today: `CFG_SECTION.RESET_OUT` (row 9) and `CFG_AXCNT.BEHAV_INPUT3` (row 12). A multi-value bug in the same area was fixed in this ticket — the rule now uses `payload.asStringList()` + `Set.contains` instead of `asString().equals` (which was failing whenever the payload was an array). 5 Cucumber scenarios were added.

### VTF-303 — `EthernetDetail` multi-IP support

`EthernetDetail.destIpNw1` and `destIpNw2` were changed from `String` to `List<String>` to surface all destination IPs per COM entry rather than just the first. `EthernetDetailExtractorService.buildIpAddressList()` now iterates all `CFG_INT_ID_DEST_NW1` / `CFG_INT_ID_DEST_NW2` blocks (mirroring the existing `CFG_FWRD_ACD` pattern). Excel handles the list automatically — `ExcelDataProcessor.setCellValue` joins entries with `"\n"` inside a single cell. The frontend handles the list shape transparently.

This is the change that's now visible in codebase.md §10's note about multi-IP / forward-ACD handling.

---

## 5. Lisa's challenge questions and the FCVT rebuttal

> *Source: memory only — verbatim rebuttal text from the original conversation. Not covered in the uploaded Phase 1 docs.*

ADC files are not being modified outside FCT.

FCVT expects the unmodified FCT export as input. There is no "post-export modification" workflow that the tool exists to validate. Questions 1, 2, and 3 all assume that workflow, and once the actual workflow is clear, they resolve differently.

Here's what's actually happening:

- FCT is a design tool. An AE engineer uses it to author the project configuration. FCT is optimized for authoring, not for producing a production-ready output end-to-end.
- The exported ADC files still require a production-grade check. Today, this is a manual line-by-line review, done first by the designer as a self-check and then by the verifier as the independent 4-eyes check.
- At scale — projects exceeding 200 detection points, under time pressure — manual line-by-line checking fails. WHY?
The verifier prints each ADC file to do the review on paper; rejected files get rectified by the designer, resubmitted, and the full set is reprinted for the next verification pass. Verification closes only when every value cross-checks correctly. Across multiple rejection cycles on a large project, that's significant paper, toner, review-hours, and project-time burnt on mechanical errors a rule engine could have caught upfront.

Where FCVT fits:
It's a supplementary validation layer for the designer, sitting between FCT export and the verifier.
A preflight that catches the mechanical rule violations humans reliably miss at scale.
Less error-prone input into verification = shorter, cheaper, more effective verification cycles — and measurably less paper printed.

On Lisa's specific questions, reframed against that workflow:

- **Why are ADC files modified outside FCT?** — They aren't. FCVT validates the unmodified FCT export against configuration rules that FCT, as a design tool, doesn't enforce end-to-end for production output.
- **Why are changes required so late?** — The "changes" are error rectifications the designer has to make after the verifier flags them. Today that detection happens late, in a slow print-review-reject-reprint loop. FCVT moves the detection earlier, into the designer's own workflow, before verification.
- **What kind of changes — the checks look generic?** — Yes, Phase 1 is deliberately generic — it's our equivalent of Frauscher Australia's ADCQuickCheck Tool. That scope is intentional — generic checks are reusable across every project, which is exactly what a baseline validation layer should be. Project-specific validation is a separate class of problem, and that's what Phase 2 addresses.
- **Phase 2 validation of Project inputs against ADC, without replacing 4-eyes?** — Phase 2 uses the baseline FCT export and the project control tables as validation inputs — both human-authored and human-reviewed artifacts produced upstream. The tool flags where the delivered ADC diverges from them. This is still a designer-side preflight, not an attestation and not a verifier replacement. The 4-eyes verifier owns the final pass; FCVT ensures they receive cleaner input.

The consistent pattern across Phase 1 and Phase 2: FCVT strengthens the verification process by reducing the mechanical-error noise the verifier has to wade through. It doesn't replace verification — it makes it cheaper, faster, and more focused.

---

## 6. Documentation arc (March – April 2026)

> *Source: memory only — uploaded docs cover the codebase, not the surrounding documentation work.*

Through March and into early April, a parallel documentation push ran alongside the code work. The main artifacts:

- **FCVT How-To Guide** — drafted in Word, then migrated to Confluence. Walks a safety engineer through uploading, configuring inputs, running validation, and reading the Excel report. Pending approval from Shekhar and Max at the Apr 2 release meeting (see §7) → then promoted to the Frauscher Knowledge Base.
- **Agreed Output baseline document** — 23 screenshots curated into a 28-page Word doc that defines what FCVT should produce for a given canonical input. Acts as the visual contract for the frontend and the regression target for future changes.
- **Validation report worksheet corrections** — corrections to the Excel report templates and the field-to-column mapping for the detail sheets.
- **Confluence meeting notes, Jira tickets, release announcement** — the standard project-hygiene cadence around the v1.0.0 release.
- **Internal rebuttal documents** — the formal version of the responses to Lisa's challenge questions (§5).

---

## 7. Phase 1 release (April 2, 2026)

Phase 1 was released as **v1.0.0**. The release meeting on Apr 2 produced the following action items:

- **Deploy** to `at-cvt01.frauscher.host` and create the matching Jira release task.
- **Release email** to stakeholders.
- **Coffee post** (internal celebration).
- Participant list for the comms to be sourced from Salai and the Release channel.
- **Close the Idea Hatchery post** — FCVT graduated from idea-tracking.
- **Update technical documentation** for the rule engine and the duplicate-value registry.
- **How-to-Use Guide approval** from Shekhar and Max → move to the Knowledge Base.

Items deferred to the next phase or backlog:

- Remove `OptionalInputMatch` rule type (still implemented but unconfigured — codebase.md §7).
- Add `DefaultValue` validation in `RuleConfig` (this was actually done under VTF-299; the action item was for the validation work that hadn't landed by the release meeting).
- Update test cases.

> *Source: memory entry 16 + brief history.*

---

## 8. What was outstanding at the VTF-305 boundary

By the time Phase 2 design started in earnest, Phase 1 had stabilised but was carrying a known set of issues and pending work — documented in detail in codebase.md §13 (bugs / gotchas) and §14 (pending feature work). At a glance:

- **Known bugs / gotchas** — the comDetails classification gap (only set on `CFG_MY_IP_NW1`), CFG_TIMEOUT parsing sequence anomaly, CFG_ZP InputMatch anomaly, input file hygiene, plus 6 gotchas around multi-flag files / `@JsonAnySetter` schema-laxness / `FAIL_ON_UNKNOWN_PROPERTIES` contract breaks / block-occurrence enrichment lifetime / timeout unit conversion / no sample `.ADC` files in repo.
- **Pending feature work** — CFG_SWITCH validation (three new rules using `InputMatchOrBlockNotFound` for `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME`).

These are open inputs into Phase 2 planning, not Phase 1 work items.

---

## 9. Phase 1 → Phase 2 handoff

The Phase 1 system as released has three properties that the Phase 2 design specifically builds on:

1. **The rule engine is open-ended.** The default-rule mechanism (codebase.md §7) lets the user validate any `(block, entry)` the file contains, not only the 41 configured ones. Phase 2 takes advantage of this by introducing expectations from FCT + PDQ that the user wouldn't manually enter.
2. **Statelessness is real.** No persistence, no cross-request state beyond a per-request `DuplicateValueRegistry`. Phase 2 preserves this — the validate-time preprocessor is an in-memory object scoped to a single request.
3. **BDD coverage is the contract.** 157 Cucumber scenarios (recount pending) gate the existing behaviour. Phase 2 extends the suite — it does not replace it. Every Phase 1 scenario must continue to pass through the Phase 2 changes.

The detail of how Phase 2 builds on Phase 1 is in `fcvt-phase2-design.md`.

---

*End of Phase 1 history — current to v1.0.0 release (Apr 2 2026) and the VTF-303 / VTF-305 codebase boundary.*
