# FCVT — Phase 2 Design

> ⚠️ **The v2 expectations/preprocessor design here (esp. §4.1, §4.2, §7 clusters, §8 Check A/B) is SUPERSEDED by [`v2-expectations-contract.md`](v2-expectations-contract.md) (2026-06-20)** — the two-bucket flatten model (`scalarExpectations` + `instancedExpectations`), id-to-id join, per-block derivations, and the gate + error codes. The FCT/PDQ-parsing sections (§5/§6) remain current **except**: `IDENTIFICATION` min/max are now **numeric**; CHC main-vs-combination does **not** trust `trackType` (signed sensor-set algorithm instead). The contract wins on any conflict.

In-flight Phase 2 architecture. Companion to `fcvt-codebase.md` (Phase 1 reference) and `fcvt-phase1-history.md` (the path that got us here). Updated as design progresses; current state reflects the early-Phase-2 design freeze around May 2026.

Decisions are captured in narrative form. Where a decision was reached in a sync meeting, the meeting itself lives in `fcvt-meeting-and-strategy.md`; this document carries the resulting design intent.

---

## Setting

Phase 2 extends FCVT from project-agnostic single-file validation into **input-dependent project-specific validation**. Where Phase 1 asks the safety engineer to enter project values via UI dropdowns and then matches them against the corresponding `.ADC` file entries, Phase 2 takes its expectations from two upstream design-baseline artifacts — the FCT2 archive (the design tool's project export) and the PDQ workbook (the configuration control tables and CQ-IR mapping) — and validates the delivered `.ADC` files against those.

The framing from the Phase 1 rebuttal applies unchanged: the FCT2 export and the PDQ are human-authored, human-reviewed upstream artifacts. The tool flags where the delivered ADC diverges from them. This remains a designer-side preflight, not an attestation and not a verifier replacement.

Phase 2 keeps every Phase 1 property: stateless backend, no database, fail-fast on load, BDD-driven. The new work extends — never replaces — what Phase 1 ships.

---

## 1. Input model

Phase 2 introduces **three input streams**:

1. **ADC files** — same upload as Phase 1, via `POST /api/upload/adcfiles`. Treated as validation targets only.
2. **FCT2 archive** — `.fct2` zipped project export. New endpoint `POST /api/upload/fct`. Only `Project.xml` is parsed; the trackplan XMLs inside the archive are out of scope.
3. **PDQ workbook** — `.xlsx` containing the CQ-IR (parsed via a dedicated Config Key column, with step/enum values from `MappingProperties`) and the `ConfigControlTable` (via DP-name literal match, case/whitespace significant). New endpoint `POST /api/upload/pdq`. The Data Transmission Output subsheet is also extracted for Cluster 6.

The existing UI form (the one Phase 1 uses for direct value entry) becomes the **manual fallback** for projects without a properly formatted PDQ or FCT2. Once PDQ + FCT are both uploaded, the entire UI form freezes read-only — not just CFG_SWITCH fields, the whole form. Project code is surfaced for context.

Backend remains stateless. No database, no cross-request state beyond the per-request `DuplicateValueRegistry` from Phase 1. The Cucumber BDD suite is extended, not replaced — every Phase 1 scenario must continue to pass through the Phase 2 changes.

---

## 2. The coupled-artifacts decision (locked)

**The v2 validate path (`/api/config/v2/validate`) cannot be triggered unless BOTH PDQ and FCT are uploaded. With neither uploaded, the frontend calls the Phase 1 `/api/config/validate` path instead.** No partial-input validation — only one of the two uploaded is not a valid state. This is a hard architectural commitment.

The reasoning is failure-mode-driven: Cluster 1 Check A on FCT alone would pass `SLCT_TIMEOUT` consistency against an unverified reference AEB. Reference AEB correctness needs PDQ's `ConfigControlTable`. A passing rule against a wrong reference AEB **hides** real configuration errors, which is worse than no check at all. The same logic applies to every FCT-touching cluster.

The consequence is explicit: for projects without a properly formatted PDQ, Phase 2 validation is **not available** for that project. Manual review remains the path. Partial-input support would significantly increase development cost and time without increasing validation value.

This decision was reached in the May 2026 design freeze and recorded against the prior version of the design (which had treated FCT and PDQ as independent uploads — that earlier shape is superseded).

---

## 3. Endpoints — three endpoints total

### 3.1 `POST /api/upload/fct` (new)

- **In:** `.fct2` archive (multipart, ≤10 MB file / 15 MB request / 20 MB threshold; in-memory only, no spool).
- **Out:** `ComAebMap` JSON — the shape documented in §5.3.
- **Parses Project.xml only.** Trackplan XMLs out of scope.
- **Fail-fast** on the first invalid condition. Two external error codes; details in §5.4.

### 3.2 `POST /api/upload/pdq` (new)

- **In:** `.xlsx` workbook (typical 200 KB – 2 MB; ≤10 MB cap).
- **Out:** Parsed PDQ JSON — header metadata plus three top-level data sections.
- **In-scope sheets:** PDQ, CQ-IR, Control table, Data Transmission Inputs. The first three are mandatory. The DT sheet is conditionally required (absent → treated as valid, no DT for the project; present-but-empty → rejected as malformed).
- **Visibility:** Parser respects sheet/row/column visibility flags. Hidden content is out of scope and is not parsed.
- **Fail-fast** on the first invalid condition. Single external error code:
  - `PDQ_INVALID` — all parse and validation failures collapse here.
- Internally, an enum carries diagnostic detail (sheet missing, parametric row with empty Response, range invalid, lookup failed, value-not-divisible-by-step, etc.) for logging and debugging. Not surfaced externally.

#### Response shape

```json
{
  "projectCode": "123",
  "aebEquipmentVersion": "GS07",
  "cqIrParameters": {
    "CFG_SECTION":       { "COMM_FAIL": "0", "BEHAV_GE": "1", "CLR_TRACK": "0", "RESET_IN": "5" },
    "CFG_RESET":         { "RESET_OP_TIME": "50", "RESET_LD_TIME": "1" },
    "CFG_SECTION_OUT":   { "CLR_OCC": "0", "AUX1_OUT": "3", "AUX2_OUT": "3", "AUX1_NO_NC": "1", "AUX2_NO_NC": "0", "TYPE_AUX1": "0", "TYPE_AUX2": "0" },
    "CFG_AXCNT":         { "BEHAV_INPUT1": "6", "BEHAV_INPUT2": "6", "BEHAV_IOEXB": "7", "TYPE_IN1": "0", "TYPE_IN2": "0", "TYPE_IN3": "0" },
    "CFG_OCC":           { "OCC_EXT": "26", "OCC_DELAY": "0" },
    "CFG_ZP":            { "INTERVAL": "3", "SUPERVIS_COUNT": "2", "SYSTEM_COUNT": "2", "PARTIAL_COUNT": "1", "SUPERVIS_COUNT_LMT": "0" },
    "CFG_BEHAV_TGGL":    { "BEHAV_RESET": "7", "BEHAV_SIMUL": "1" },
    "CFG_TIMEOUT":       { "TIMEOUT_VALUE": ["34", "61", "0", "0", "0", "0", "0", "0"] },
    "CFG_SWITCH":        { "SWITCH_GE": "26", "SWITCH_GSF": "0", "PRERESET_ACT_TIME": "180" },
    "CFG_SUPERVIS_FMA1": { "RESET_TYPE": "3", "RESET_DELAY": "30" },
    "CFG_SUPERVIS_FMA2": { "RESET_TYPE": "3", "RESET_DELAY": "30" },
    "CFG_IP_SWITCH_TIME": { "IP_SWITCH_TIME": "10" },
    "CFG_PROJECT_AEB":   { "BLOCK_EXISTS": "true", "PROJECT_NUMBER": "123" },
    "CFG_PROJECT_COM":   { "BLOCK_EXISTS": "true", "PROJECT_NUMBER": "123" }
  },
  "controlTable": {
    "trackSections": [ /* track-section sub-table rows */ ],
    "dpTable": [ /* DP sub-table rows */ ]
  },
  "dataTransmission": null
}
```

- `projectCode` — from the PDQ sheet header.
- `aebEquipmentVersion` — from PDQ row 1.09 AEB board version (e.g. `GS05`, `GS07`). Governs the version-aware key group (§6.4): GS06-and-above is tested numerically on the `GSnn` value.
- `cqIrParameters` — **block-grouped**, mirroring the Phase 1 `userInput` structure: each ADC config key nests under its parent `CFG_*` block rather than sitting flat. Values are strings after numeric transform (§6.3); arrays for multi-value keys; `IDENTIFICATION` is a `{ min, max }` object. The former top-level `blockExistsForProjectCode` is removed — that information now lives in `CFG_PROJECT_AEB.BLOCK_EXISTS` and `CFG_PROJECT_COM.BLOCK_EXISTS`. Full block-to-key mapping in §6.4.
- `controlTable` — two sub-tables: `trackSections[]` from source cols A–I, `dpTable[]` from cols K–N. Col J is the empty boundary (frozen Ver14 layout; §6.5).
- `dataTransmission` — `null` when the DT sheet is absent; otherwise `{ dataSafetyLevels[], outputDataTransmission[] }`.

Parser detail for each section is in §6.

### 3.3 `POST /api/config/v2/validate` (new v2 path) and `POST /api/config/validate` (Phase 1, unchanged)

- Phase 2 adds a new endpoint `POST /api/config/v2/validate`. The Phase 1 `POST /api/config/validate` path is untouched and continues to serve the manual-fallback / no-upload case.
- On v2, `userInput` is upload-sourced: it carries `fctData` (ComAebMap) and `pdqData` (parsed PDQ, block-grouped `cqIrParameters`) as mandatory keys, plus the four tpf-sourced keys (`CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT`) when a tpf file was uploaded. Phase 1's per-block form keys are not sent on v2 — the UI form is frozen once both uploads succeed. `parsedConfigFiles` (ADCs) is unchanged.
- All Phase 1 behavior preserved on the Phase 1 path.

---

## 4. Validate-time backend flow

Phase 2 introduces a two-stage flow inside `/validate` once both PDQ and FCT are present.

### 4.1 Preprocessor (validate-time, in-memory)

Built fresh per `/validate` request. Scoped to method-local variables. Stateless — no caching, no shared singleton, no fields that survive the request.

**Inputs:** `ComAebMap` (from FCT upload response) + parsed PDQ (from PDQ upload response). Both are guaranteed present because of the coupled-artifacts rule.

**Outputs:**

- **Expectations JSON** — keyed `(ADC file, block, instance, entry) → expected value`. This is the external source the engine consumes as its expected-value lookup.

**Source-of-truth rule (locked):** ADC files are validation **targets** only. Nothing in the preprocessor reads from ADC. Expectations come from the design baseline (FCT + PDQ), never from the artifact under test.

This rule corrects an earlier design that had the preprocessor reading SLCT_TIMEOUT from ADCs. The current design derives expected SLCT_TIMEOUT from ComAebMap segment membership (see §8.1) and treats whatever the ADC contains as the actual value to be compared.

The `ComIpMap` and self-IP findings that featured in the older design have been **dropped**. COM file IP validation is out of scope for Phase 2 (§11). If it gets reintroduced later, it will be static checks only — format validity, distinctness, existence in the expected list — not file-sourced cross-referencing.

**Validate-time cross-correlation rules.** Beyond the per-entry expectation lookups, the preprocessor applies four consistency rules that span files or blocks:

- **Project-block consistency.** In the ADC files, `CFG_PROJECT_AEB` and `CFG_PROJECT_COM` must either both be absent or both be present (in their respective AEB and COM files), and where present must carry the same project value. The expected project value is the single `projectCode`-derived number the PDQ parser writes identically into both `CFG_PROJECT_AEB.PROJECT_NUMBER` and `CFG_PROJECT_COM.PROJECT_NUMBER`. Divergence fails the check.
- **Dual-FMA consistency.** `RESET_TYPE` and `RESET_DELAY` are parsed once from CQ-IR and written identically into `CFG_SUPERVIS_FMA1` and `CFG_SUPERVIS_FMA2`. At validate time, both ADC FMA values for each key are checked against that single source; if either diverges, the check fails.
- **`CFG_IP_SWITCH` derivation.** `CFG_IP_SWITCH` is not a CQ-IR key. The preprocessor derives it from the FCT response field `redundantComPresent`: when `true`, it adds `CFG_IP_SWITCH: { IP_SWITCH: "1" }` to the Expectations JSON; when `false`/absent, the block is omitted and IP switching is treated as disabled. This is distinct from `CFG_IP_SWITCH_TIME`, which is a CQ-IR key carried in `pdqData`.
- **`CFG_RSR_TYPE` consistency (PDQ ↔ tpf).** *(Frozen Ver14)* `RSR_TYPE` is supplied by both sources: the PDQ response (`pdqData.cqIrParameters.CFG_RSR_TYPE.RSR_TYPE`) and, when a tpf file is uploaded, the tpf-sourced `userInput.CFG_RSR_TYPE.RSR_TYPE`. The preprocessor cross-checks the two and requires them to be identical. If both are present and differ, validation stops and the v2 request is rejected with a baseline error (`HTTP 400`) — the design baseline and the tpf translation disagree on the wheel-sensor type, so the project cannot be validated. If only one is present (no tpf uploaded), that single value is used.

### 4.2 Engine extension

The existing rule engine consumes the expectations JSON as an additional external input source. Two rule-shape mappings:

- **Check A** → InputMatch-style. Per `(ADC file, block, instance, entry)`, engine looks up expected value in expectations JSON and compares against the actual ADC value.
- **Check B** → list-membership (closest existing analog: `MultipleBlockMultipleInputMatch`). Engine evaluates the actual `CFG_FWRD_ACD` entry set against the expected set per `(homeCom file, channel)`.

The existing 9 rule types (codebase.md §7) **stretch to cover Phase 2**. No new rule types are required structurally — what's needed is a new lookup mechanism.

**Engine extension needed:** composite-key lookup mode for `(file, block, instance, entry)`. **[TBD: mechanism still open — JSONPath in rule config vs named Java lookups. Tradeoff parked; to be resolved before BE-05 implementation starts.]**

All Phase 1 rules continue to pass; the Cucumber suite is extended, not replaced.

---

## 5. FCT upload endpoint — design depth

The FCT upload endpoint is the most fully specified piece of Phase 2 and the first BE story to land (BE-03). The Vinod sync notes captured the design as it stands; this section consolidates them with the architecture decisions made since.

### 5.1 File identification

`.fct2` extension check + ZIP magic-byte stream check. Sample only the first 8 KB → 40 KB for identification.

**Root layout expected:** 8 files + ≤2 folders (trackplan folders optional, can be 0, 1, or 2).

**Root layout (RESOLVED from the fixtures, 2026-06-05):** 8 files — `mimetype`, `project.gpf`, `project.xml`, `type_protected.xml`, `project.properties`, `aeb_header_template.tpl`, `com_header_template.tpl`, `revision_history.xml` — plus `META-INF/` (with `manifest.xml`) and an optional `trackplan/` folder. Identification keys on `.fct2` + ZIP magic + a mandatory `project.xml`; only `project.xml` is parsed.

### 5.2 Security and resource limits

- Multipart in-memory only, no spool: 10 MB file / 15 MB request / 20 MB threshold.
- Decompression ratio cap: 50:1.
- Total uncompressed size cap: 50 MB.
- Per-entry uncompressed cap: 20 MB.
- Nested-archive rejection via filename scan (no archives inside the FCT2).
- Password-protected archive rejection via ZIP General Purpose bit 0.

**[TBD: Spring Boot 4.0.2 multipart default override behavior]** — need to verify how SB4 defaults interact with the in-memory configuration before locking the multipart pipeline.

### 5.3 Project.xml hierarchy (parsed structure)

```
ProjectElements > Station > Cubicle > Rack > Bp
```

`Bp` children: `Psc`, `Com`, `Aeb`, `IoExb`. `Bp` has `@internId` and `CanConnections` previous/next (linked by `internId`).

**Implementation notes (verified against the fixtures, 2026-06-05):** the real root element is `<FAdcWizard>` (with `<Configuration>`/`<Project>` siblings of `<ProjectElements>`), and each level is wrapped in `<Parameter>` + `<Children>` (e.g. `Bp/Parameter/CanConnections`, `Bp/Children/{Com,Aeb,IoExb}`). A CAN segment = a connected component of Bps via `CanConnections`; **power-only backplanes (BP_PWR with just a `<Psc>`, empty `<CanConnections/>`, no COM/AEB) are skipped** — they are not CAN segments (a group with AEBs but no COM stays a legitimate "chain with no COM" error). The IoExb mode is its child tag: `<AxleCountingOutput>` → ACO, `<DataTransmission>` → DT (counted only), anything else → reject (`FCT_INCOMPLETE_BASELINE`). The AEB-level `<CountingHeads>`/`<CountingHeadControls>` are sensor/CHC config, not IoExb modes.

| Element | Attributes / children of interest |
|---|---|
| `Com` | `@name`, `Id`, `ComType`, `ComVersion` |
| `Aeb` | `@name`, `@cpName` (DP name), `@internId`, `@slotId`, `Id`, `Information/@device` (equipmentVersion, e.g. `GS07`), `Fmas`, `IoExbBehaviour`, `IoExbControl` |
| `Fma` | `@internId`, `@fmaId`, `@aebInternId` (no child `<Id>` — `@fmaId` is the FMA's id) |
| `IoExb` | `@name` (e.g. an ACO label), `RefAeb` → `Aeb` `internId`, `AxleCountingOutput/OutputFma1/2` → `Fma` `internId`s |

### 5.4 ComAebMap construction (parse-time)

CAN segment membership is built from the `Bp` chain. `Bp`s are linked via `CanConnections` previous/next by `@internId`. Each chain has:

- **1 Com** — normal case. `Com.@name` joins to ADC `[IDENTIFICATION] ID`. The chain's `Com.Id` and `Com.@name` are exposed on the ComAebMap.
- **2 Coms with `ComMode` MASTER + SLAVE** — redundancy case. The SLAVE is discarded; only MASTER survives. `redundantComPresent: true` is set on the chain.

All `Aeb`s in the chain are physical members of that CAN segment. The `Aeb`'s `Id` (exposed as `dpId`), `@cpName` (exposed as `dpName`), `Information/@device` (equipmentVersion), `Fmas`, and IoExb-related children are exposed per AEB. The AEB's `@name` (FCT design convention: `AEB_<DPName>`) is not surfaced — operationally each AEB is identified by its DP.

### 5.5 FCT upload 200 response shape

```json
{
  "chains": [
    {
      "com": { "comId": "...", "comName": "..." },
      "redundantComPresent": true,
      "aebs": [
        {
          "dpId": "...",
          "dpName": "...",
          "evaluatedFmas": [
            {
              "fmaName": "...",
              "fmaId": "...",
              "dpId": "..."
            }
          ],
          "acoIoExbs": [
            {
              "label": "...",
              "outputFma1Name": "...",
              "outputFma1Id": "...",
              "outputFma1DpId": "...",
              "outputFma2Name": "...",
              "outputFma2Id": "...",
              "outputFma2DpId": "..."
            }
          ],
          "dtIoExbCount": 0
        }
      ]
    }
  ]
}
```

Shape conventions:

- `chains[]` — one per CAN segment.
- `com` — single object per chain (post-redundancy collapse).
- `redundantComPresent` — always present, boolean.
- `aebs[]` — physical AEB members of the segment. Each AEB is identified by its DP (`dpId` from `Aeb<Id>`, `dpName` from `Aeb@cpName`), reflecting the 1:1 mapping between an AEB and its sensor/DP.
- `evaluatedFmas[]` — per AEB, FMAs whose `@aebInternId` resolves to this AEB. Surfaces `fmaName` (from `@name`), `fmaId` (from `@fmaId`), and the parent DP id per FMA. This entry corrects an earlier shape that did not include it.
- `acoIoExbs[]` — IoExbs with ACO mode. Each entry's `label` is the IoExb's `@name` (free-text). `OutputFma1` and `OutputFma2` are resolved at parse time: text → `Fma@internId` → `Fma@aebInternId` → `Aeb<Id>`. The resolved FMA's `@name`, `@fmaId`, and the parent AEB's `<Id>` are exposed under the `outputFma{1,2}Name/Id/DpId` triplet. Single-track ACOs omit `outputFma2*` fields. Note that an ACO IoExb's resolved FMA can reference an AEB different from the IoExb's host AEB — this is supported.
- `dtIoExbCount` — DT-mode IoExb count only. The inner DT data comes from PDQ, not from the FCT.
- All IDs serialised as JSON strings.

### 5.6 Error contract (locked)

External rejection: **single code, no detail**. Two external codes only:

| Code | Meaning |
|---|---|
| `FCT_TAMPERED` | Cross-reference or invariant fail during ComAebMap build. XML parse failure folds into this category. Catches the case where someone has manually edited the FCT after export. |
| `FCT_INCOMPLETE_BASELINE` | Pre-export incomplete-baseline FCT: dangling `CanConnections`, chain with no COM, multi-COM chain without redundancy pairing, unsupported IoExb mode, duplicate or missing entity `Id` where `Id` is `0` or `4096`. |

Parse-layer rejections (HTTP 400 for malformed multipart, HTTP 413 for size exceedance) remain unchanged — these are pre-business-logic and don't get folded into the two codes.

Internally, a `FctInvalidException` carries a lazy enum with `INCOMPLETE_*` variants for each specific cause + a `TAMPERED` catch-all. The external response only surfaces the two codes; the internal variants drive logging and debugging.

### 5.7 Counting-head-output IoExb rejection

Only ACO and DT modes are in scope for Phase 2. If a counting-head-output mode IoExb is present in the FCT, the upload is rejected (likely under `FCT_INCOMPLETE_BASELINE` since it's a baseline-shape constraint, **[TBD: exact code mapping]**).

---

## 6. PDQ upload endpoint — design depth

The PDQ parser scope was locked at the May 2026 design freeze. The shape and decisions below are what cluster-level consumers (Clusters 3, 4, 5, 6) depend on. Open items against AE and against the backend MappingProperties file are listed in §12.

### 6.1 In-scope sheets and gating

| Sheet | Status | Behaviour |
|---|---|---|
| PDQ | Mandatory | Drives `projectCode`, the project blocks (`CFG_PROJECT_AEB` / `CFG_PROJECT_COM`), and the AEB equipment version (row 1.09) |
| CQ-IR | Mandatory | Drives `cqIrParameters` |
| Control table | Mandatory | Drives `controlTable.trackSections[]` and `controlTable.dpTable[]` |
| Data Transmission Inputs | Conditional | Absent → `dataTransmission: null` (valid). Present-but-empty → reject as malformed. Present-and-populated → parse into `dataSafetyLevels[]` + `outputDataTransmission[]`. |

Hidden sheets, rows, and columns are skipped — the parser honours workbook visibility flags. The intent is that AE controls scope by hiding rather than deleting.

The parser locates sheets and within-sheet anchors via a `pdq-workbook.properties` contract: sheet names; the CQ-IR `Configuration Word` / `Response` / `Remarks` header labels; the `Project Code` label; the `Sl. No.` key for row 1.09 (AEB board version); the control-table column map; and the Data Transmission section titles + column headers. Columns and rows are found by **scanning for these labels**, not fixed positions — so AE inserting or moving rows/columns needs no code change; only a tab rename or relabel is a one-line config edit. (Frozen Ver14 removed the System Redundancy row 1.08; `BLOCK_EXISTS` / `PROJECT_NUMBER` now come from the CQ-IR `PROJECT_NUMBER` row's Response + Remarks — §6.4.)

### 6.2 CQ-IR row identification

The CQ-IR sheet carries a dedicated **Config Key** column (labelled **"Configuration Word"** in the workbook; validation-locked in the template). It is the sole parse-trigger:

- **Config Key non-empty** → parametric row. The parser reads the Response column and emits the value under that key.
- **Config Key empty** → meta row. Skipped entirely, regardless of Response content or any key-like text in the question prose.

The parser does not scan the question text for bracketed tokens. A key written in parentheses in the prose (e.g. a meta row `Behaviour input 3 (BEHAV_INPUT3)`) is not parsed because its Config Key column is empty. This supersedes the bracket-scanning model used in the earlier design. The PDQ workbook was rebuilt to add the Config Key column, normalise Response values, and apply validation/password gating to keep the template structure stable.

### 6.3 CQ-IR response normalization

The parser reduces each parametric Response cell to a canonical value:

- **Value/label split.** Response cells hold either a bare value (`2600`) or a `value: label` pair (`3: Clearing of track or partial traversing error`). The parser splits on the **first** colon, keeps the left token, and trims whitespace on both sides (`5 : Default`, `5: Default`, `5 :Default` all yield `5`). Units are stated in the question text only, never in the Response cell.
- **Array values.** Multi-value cells use ` & ` (space-ampersand-space) as the separator. Split on ` & `, trim each element, emit an array. Scalars yield a one-element result under the same rule.
- **Step division.** Time-valued fields are divided by a per-field step (from a dedicated `pdq-mappings.properties` — kept separate from the display-oriented Phase 1 `value-mappings.properties`, which it would otherwise collide with on `<field>.step`) to produce the stored ADC representation:

| Key | Block | Unit | Step | Example |
|---|---|---|---|---|
| OCC_EXT | CFG_OCC | ms | 100 | 2600 → 26 |
| OCC_DELAY | CFG_OCC | ms | 100 | 0 → 0 |
| RESET_OP_TIME | CFG_RESET | ms | 10 | 500 → 50 |
| RESET_LD_TIME | CFG_RESET | ms | 100 | 100 → 1 |
| SWITCH_GE | CFG_SWITCH | ms | 100 | 2600 → 26 |
| SWITCH_GSF | CFG_SWITCH | ms | 100 | 0 → 0 |
| PRERESET_ACT_TIME | CFG_SWITCH | s | 10 | 1800 → 180 |
| RESET_DELAY | CFG_SUPERVIS_FMA1 / FMA2 | s | 1 | 30 → 30 |
| IP_SWITCH_TIME | CFG_IP_SWITCH_TIME | s | 1 | 10 → 10 |
| TIMEOUT_VALUE | CFG_TIMEOUT | ms | 10 | 340 → 34, 610 → 61 |

- **INTERVAL enum mapping.** `INTERVAL` (CFG_ZP) is not divided; it maps a fixed value set to ADC codes: `10 → 0`, `40 → 1`, `80 → 2`, `160 → 3`.
- **Non-integer step result → reject.** `PDQ_VALUE_NOT_DIVISIBLE_BY_STEP` (internal; surfaces as `PDQ_INVALID`).
- **Enumerated lookup failure → reject.** `MAPPING_PROPERTIES_LOOKUP_FAILED` (same external surfacing).

The Remarks column is never read by the parser. Verbose labels and notes remain in the source sheet for AE readability and are not preserved in the JSON.

### 6.4 Block-grouped output, special cases, and derived groups

**Block-grouped shape.** `cqIrParameters` mirrors the Phase 1 `userInput` structure — each key nests under its parent `CFG_*` block. The parser maps every Config Key to its block:

| Block | Keys emitted by the PDQ parser |
|---|---|
| `CFG_SECTION` | COMM_FAIL, BEHAV_GE, CLR_TRACK, RESET_IN |
| `CFG_RESET` | RESET_OP_TIME, RESET_LD_TIME |
| `CFG_SECTION_OUT` | CLR_OCC, AUX1_OUT, AUX2_OUT, AUX1_NO_NC, AUX2_NO_NC (+ TYPE_AUX1, TYPE_AUX2 for GS06+) |
| `CFG_AXCNT` | BEHAV_INPUT1, BEHAV_INPUT2, BEHAV_IOEXB (+ TYPE_IN1, TYPE_IN2, TYPE_IN3 for GS06+) |
| `CFG_OCC` | OCC_EXT, OCC_DELAY |
| `CFG_ZP` | INTERVAL, SUPERVIS_COUNT, SYSTEM_COUNT, PARTIAL_COUNT (+ SUPERVIS_COUNT_LMT for GS06+) |
| `CFG_BEHAV_TGGL` | BEHAV_RESET, BEHAV_SIMUL |
| `CFG_TIMEOUT` | TIMEOUT_VALUE |
| `CFG_SWITCH` | SWITCH_GE, SWITCH_GSF, PRERESET_ACT_TIME |
| `CFG_SUPERVIS_FMA1` | RESET_TYPE, RESET_DELAY |
| `CFG_SUPERVIS_FMA2` | RESET_TYPE, RESET_DELAY |
| `CFG_IP_SWITCH_TIME` | IP_SWITCH_TIME |
| `CFG_PROJECT_AEB` | BLOCK_EXISTS, PROJECT_NUMBER |
| `CFG_PROJECT_COM` | BLOCK_EXISTS, PROJECT_NUMBER |

**Special-case values.**

- **`IDENTIFICATION`** — value `1 to 4095` is split on `to`, trimmed, emitted as a **top-level** `cqIrParameters` block `{ min, max }` with both bounds as **integers** (e.g. `{"min":1,"max":4095}`). Matches the Phase 1 `RangeCheck` userInput shape (numeric `min`/`max`); feeds the same engine path. Only `to`-separated, min/max-shaped value.
- **`CFG_TIMEOUT` / `TIMEOUT_VALUE`** — multi-value response split on ` & `, step-divided, then **padded to a fixed width of 8** with `0`. Example: `340 & 610` → `["34","61","0","0","0","0","0","0"]`. Always nested under the `CFG_TIMEOUT` block. The allowed value set on the cell is governed by a project-appropriate Excel validation list (Indian standard `340 & 610`); this is a template concern, not a parser constant.

**Project blocks.** `CFG_PROJECT_AEB` and `CFG_PROJECT_COM` each carry `BLOCK_EXISTS` (string `"true"`/`"false"`) and `PROJECT_NUMBER` (identical across both). This replaces the former top-level `blockExistsForProjectCode`. *Frozen Ver14:* both are derived from the CQ-IR `PROJECT_NUMBER` row (Sl. No. 44) — Response `YES` → `BLOCK_EXISTS:"true"` with `PROJECT_NUMBER` read from that row's **Remarks** column (blank Remarks → `"0"`); Response `NO` → `"false"` and the Remarks cell must be empty (else `PDQ_INVALID`). The earlier source — PDQ row 1.08 System Redundancy (`Single`/`Dual`) — was removed in Ver14. The top-level `projectCode` is still read from the PDQ sheet by anchoring on the **"Project Code"** label and reading the value cell to its right (merged cells resolved); blank/absent → `"0"`.

**Version-aware group.** PDQ row 1.09 (AEB Equipment Version) governs the keys `TYPE_AUX1/TYPE_AUX2` (CFG_SECTION_OUT), `TYPE_IN1/TYPE_IN2/TYPE_IN3` (CFG_AXCNT), and `SUPERVIS_COUNT_LMT` (CFG_ZP): omitted entirely for GS05-and-below; included with property-file defaults for GS06-and-above (no PDQ input). GS05-omit is the common case for most Indian deployments.

**Keys not emitted by the PDQ parser.** `RESET_OUT` (CFG_SECTION) and `BEHAV_INPUT3` (CFG_AXCNT) are derived from the control table at validate time. `TYPE_PRTCT_CODE` is sourced from the tpf (`/api/upload/translate`) response, not PDQ — its CQ-IR row is a meta row. `CFG_IP_SWITCH` is FCT-derived at preprocessing (§4.1), not a PDQ output. *(Frozen Ver14: `RSR_TYPE` is now PDQ-sourced and emitted as the `CFG_RSR_TYPE` block — no longer in this list.)*

### 6.5 Control table parsing

The Control table sheet contains two side-by-side sub-tables separated by an empty column (frozen PDQ Ver14 layout):

- **Track sections** — cols A–I. Becomes `controlTable.trackSections[]`. (Col G holds the FAdC auto-reset operands, col H the separate `Logic type` operator, col I the `Auto reset by timer circuit` flag.)
- **DP table** — cols K–N. Becomes `controlTable.dpTable[]`.
- **Col J** — empty boundary column. Never contains data.

Parsing rules:

- **Track Output column terminology.** Source values `PHYSICAL` and `VIRTUAL` are translated at parse time to `MAIN` and `COMBINATION` respectively. This avoids collision with Cluster 1's `physical/virtual` segment-boundary terminology (§8.1 step 3).
- **`fadcAutoReset` (Ver14).** Operands and operator live in two separate columns: the `FAdC - FAdC Auto reset` column (col G) carries the comma-separated operands (e.g. `"1AXT2,SUP1-AXT1"`), and the `Logic type` column (col H) carries the operator (`OR` / `AND`). Parsed into a logic tree of the shape `{ op: "OR" | "AND", operands: [...] }`:
  - Up to 8 operands.
  - A blank/`NA` operands cell yields `null` (no auto-reset). A single operand with no operator yields `{ op: null, operands: [...] }`. A `Logic type` other than `OR`/`AND` is rejected.
- **Yes/No cells.** `E-CHC` and `autoResetByTimer` columns contain `YES` / `NO`. Translated to boolean `true` / `false` in JSON.
- **POSITION column in the DP sub-table** is restricted to `ABOVE THE RAIL` and `BELOW THE RAIL`. Other values rejected. (Not to be confused with the DT-sheet `POSITION`, which is integer 0–31 — see §6.6.)
- **Comma is the only delimiter** for multi-DP cells (e.g. `"DP4,DP5"`). No other delimiters are recognised.
- **Reset Type** values are validated against a fixed catalog supplied by AE. Values outside the catalog are rejected. The catalog enumeration is currently an open AE item — see §12; until it closes, `resetType` is parsed as-is with no catalog check.
- **String-valued output.** `serialNo` (and any numeric cell) is emitted as a string, consistent with the string-valued `cqIrParameters`. `trackOutput`/`autoResetByTimer`/`eChc` remain translated (MAIN/COMBINATION, booleans).

### 6.6 Data Transmission Inputs sheet

Two sub-tables on a single sheet:

- **`dataSafetyLevels[]`** — columns `DP NAME`, `SAFETY_LEVEL_IN`, `SAFETY_LEVEL_OUT`, `SAFE_OUT_FDBCK_QUAD`.
- **`outputDataTransmission[]`** — columns `SOURCE DP NAME`, `NMBR_OUT`, `POSITION`.

Per-cell rules (numeric values are range-validated, then emitted as **strings** for consistency with `cqIrParameters`):

- **`SAFETY_LEVEL_IN` / `SAFETY_LEVEL_OUT`** — integer `0–3` (0 NOT USED, 1 SINGLE, 2 DUAL, 3 QUAD).
- **`SAFE_OUT_FDBCK_QUAD`** — `0` / `1` (disabled/enabled). **Independent** of `SAFETY_LEVEL_OUT` (not conditional on QUAD).
- **`NMBR_OUT`** — integer `0–15` (number of outputs).
- **`POSITION` (DT sheet only)** — integer `0–31` (position of the output info in the sender ID data packet). Out of range → reject. **Distinct** from the Control table DP sub-table's `POSITION` (`ABOVE/BELOW THE RAIL`, §6.5).
- **`DP NAME` / `SOURCE DP NAME`** — stored as literal strings; matching them to the ADC files (DP name / `ID` key) is a validate-time job, not the parser's.
- **Sheet absent, or present with only headers (no data rows)** → `dataTransmission: null` (valid — the project has no DT).
- **Sheet present but malformed** (e.g. missing the column headers) → reject.

### 6.7 Error contract (locked)

External rejection: **single code**.

| Code | Meaning |
|---|---|
| `PDQ_INVALID` | All parse and validation failures collapse here. |

Internal enum variants drive logging and debugging only. Indicative variants: sheet missing, parametric row with empty Response, range invalid, lookup failed, value-not-divisible-by-step, malformed DT sheet, mixed-operator fadcAutoReset cell, unsupported POSITION value, hex-format violation.

Parse-layer rejections (HTTP 400 for malformed upload, HTTP 413 for size exceedance) remain unchanged — pre-business-logic and not folded into `PDQ_INVALID`.

---

## 7. Cluster overview

Phase 2 validation organises around **6 clusters**, each owning a set of `(block, key)` validations. The clusters map to source artifacts as follows:

| Cluster | Concern | Source artifact |
|---|---|---|
| 1 — CAN Segment | Cross-COM/AEB ID resolution, SLCT_TIMEOUT, CFG_FWRD_ACD forwarding | FCT ComAebMap + PDQ CCT |
| 2 — IOEXB ACO | IoExb position within board rack; number of ACO IoExbs per AEB | FCT ComAebMap (acoIoExbs) |
| 3 — Track Section | Sub-checks for CHC counting-head assignment, sensors-per-track, supervisory track config, ACO/DT IO assignment | PDQ CCT |
| 4 — CHC / External CHC | Combined cluster; same block, different validation approaches per sub-cluster | PDQ CCT |
| 5 — Supervisor | FMA Supervisor validation | PDQ CCT |
| 6 — Data Transmission | `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVELS` | PDQ Data Transmission Output subsheet |

### 7.1 The Cluster 1 / Cluster 2 ownership rule (locked)

Both Cluster 1 and Cluster 2 touch `CFG_SECTION_OUT`. The ownership split is structural:

- **Cluster 1 owns `ID` and `SLCT_TIMEOUT` validation across every in-scope block.** This includes `CFG_ZP_FMA1`, `CFG_ZP_FMA2`, `CFG_SECTION_OUT`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1/2`, `CFG_DATA_OUT`. The cluster that nominally owns the host block (e.g. Cluster 2 owning `CFG_SECTION_OUT` for its other validations) does **not** also do ID/SLCT_TIMEOUT. Cluster 1 does.
- **Cluster 2 also validates `ID` and `SECTION` on `CFG_SECTION_OUT`**, but with **different semantics**: position-within-rack and IoExb-count, not cross-reference resolution.

Both clusters run on the same block; their failure modes do not overlap. This is the structural commitment, not an emergent property.

### 7.2 Cluster blocks and keys

Detailed block and key specifics for each cluster:

**Cluster 1 — CAN Segment**
- Blocks: `CFG_ZP_FMA1`, `CFG_ZP_FMA2`, `CFG_SECTION_OUT`, `CFG_CONTROL`, `CFG_SUPERVIS_FMA1`, `CFG_SUPERVIS_FMA2`, `CFG_DATA_OUT`
- Keys: `ID`, `SLCT_TIMEOUT` (validated wherever these keys appear, across all in-scope blocks); plus `CFG_FWRD_ACD` entries (Check B forwarding validation)
- Source: FCT ComAebMap + PDQ CCT
- Detail in §8.

**Cluster 2 — IOEXB ACO**
- Block: `CFG_SECTION_OUT`
- Keys: `ID`, `SECTION` (validates IoExb position within board rack + number of ACO IoExbs attached to each AEB); plus block count per file
- Source: FCT ComAebMap (`acoIoExbs`)
- Detail in §9.

**Cluster 3 — Track Section**
- Blocks: **[TBD from Confluence requirements doc]**
- Keys: **[TBD from Confluence requirements doc]**
- Source: PDQ CCT
- Known structure: 4 sub-checks — (1) CHC counting head assignment per sensor/ADC, (2) correct sensors per track section, (3) supervisory track config, (4) ACO/DT IO assignment. **All four validate the ADC against the baseline** — sub-check 2 against the Control Table `dpIn`/`dpOut`, sub-check 3 against `fadcAutoReset`. The baseline (PDQ + FCT) is the sole source of truth, so **no external input is required** (an earlier "station layout" assumption is dropped — validating the baseline against physical reality is out of scope).

**Cluster 4 — CHC / External CHC**
- Combined cluster; same block, different validation approaches per sub-cluster.
- Blocks: `CFG_CONTROL` (external CHC, ≤2 per ADC); also touches `CFG_ZP` for adjacency.
- Keys: **[TBD from Confluence requirements doc]**
- Source: PDQ CCT
- Known mapping: CHC count is derived from `BEHAV_IN3` in ConfigControlTable Track table — `BEHAV_IN3 = 7` → 1 `CFG_CONTROL` block; `BEHAV_IN3 = 6` → 2 `CFG_CONTROL` blocks. Adjacency information is derived from the same Track table.

**Cluster 5 — Supervisor**
- Blocks: **[TBD from Confluence requirements doc — 3B FMA Supervisor block details pending re-confirmation against the Confluence requirements doc]**
- Keys: **[TBD]**
- Source: PDQ CCT

**Cluster 6 — Data Transmission**
- Blocks: `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVELS`
- Keys: **[TBD from Confluence requirements doc]**
- Source: PDQ Data Transmission Output subsheet
- Note: this supersedes an earlier-noted scope that included `CFG_DATA_IO SAFETY_LEVEL_IN/OUT` — that reference was incorrect and has been discarded.

---

## 8. Cluster 1 detail (CAN Segment)

Cluster 1 is the most fully specified cluster in the current design. The two checks and their assumptions are below.

### 8.1 Check A — per-AEB identity and SLCT_TIMEOUT

For each AEB `.ADC` file:

1. **AEB identity lookup.** Read `[IDENTIFICATION] ID` from the ADC. Look it up in ComAebMap. If miss → emit an **ORPHANED** finding (the ADC corresponds to no AEB in the FCT2-defined CAN segments).
2. **Per in-scope block, ref AEB validation.** For each in-scope block with a referenced AEB id, look up that AEB.Id in ComAebMap. If miss → emit a **FILE NOT FOUND** finding (the referenced AEB doesn't exist in the design baseline).
3. **Physical / virtual classification.** Compare the ComAebMap segment membership of the referencing AEB vs the referenced AEB. Same segment → **physical**; different segment → **virtual**. Virtual classifications also feed Check B's forwarding expectations (§8.2).
   - The classification comes from **ComAebMap segment membership**, not from the ADC's `SLCT_TIMEOUT` value. The ADC value is the *actual* to be validated, not the source of truth.
4. **Expected SLCT_TIMEOUT.** Derived from the physical/virtual classification:
   - `0` = same segment (physical)
   - `1` = different segment (virtual)
5. **Engine compares actual SLCT_TIMEOUT vs expected** as an InputMatch-style rule.
   - `2..7` in actual → emit **INVALID SCOPE** finding (out of Phase 2's binary scope).
   - Any other value → emit **INVALID VALUE** finding.

### 8.2 Check B — virtual forwarding (CFG_FWRD_ACD)

Builds on the virtual classifications identified in Check A.

1. **Group virtual references by channel.** Virtual references from ComAebMap + PDQ (CCT + DT input) are grouped by `(homeCom, consumingCom)` into **channels**, each with a `requiredAebIds[]` list. This grouping happens inside the preprocessor while building expectations JSON; it is not a separately exposed artifact.
2. **Compute expected forwarding set per channel.** For each channel, the expected set of `CFG_FWRD_ACD` entries is computed per `(homeCom file, channel)` and emitted as expectations JSON entries.
3. **Engine evaluates** as a list-membership rule against the actual `CFG_FWRD_ACD` entries in the homeCom file. Missing expected entries → rule fail.

The earlier `DOWNSTREAM_IP_UNRESOLVED` finding has been **dropped** — it depended on ComIpMap, which is out of Phase 2's scope (§11).

### 8.3 Cluster 1 assumptions

Ten assumptions are documented for Cluster 1. They scope what Phase 2 commits to handling and what it doesn't:

1. All COMs in a single FCT2 share the same subnet. IP-level checks are out of scope.
2. One COM per CAN segment, with redundancy supported via `IP_SWITCH = 1`.
3. FCT2 is trusted as the source of truth. If FCT2 is wrong, FCVT's output is wrong by construction. This is acceptable because FCT2 is itself a human-reviewed artifact (the rebuttal frame applies).
4. Inter-COM references are only AEB IDs. (Originally an assumption about 3G being a separate cluster; 3G has since been subsumed into Cluster 1 because no non-AEB ID references exist between COM files. Cross-file ID resolution needed for 3G is identical to the FCT2-derived segment-membership lookup in Cluster 1. The derivation lives inside each Check A; no separate precomputed map.)
5. `SLCT_TIMEOUT` semantics: values 0–7 exist in the protocol, but Phase 2 only handles 0 and 1 (physical/virtual binary). Values 2–7 emit INVALID SCOPE findings.
6. ADC identity is resolved via `[IDENTIFICATION] ID` → FCT2 `Aeb.Id`.
7. Required forwarding destinations are derived bottom-up from the AEB-reference graph (referenced AEBs propagate their required-destination set up to their referencing AEBs).
8. Forwarding tuples are keyed by `(source AEB, destination COM)`.
9. Socket numbering: `socket = header − 32`. Check B uses NW1 only.
10. ADC export is assumed correct (the FCT export step itself is trusted; what's being validated is whether the configuration the designer authored matches what the baseline says it should be).

These assumptions are also surfaced to Salai/Max in the work-package breakdown for AE agreement.

---

## 9. Cluster 2 detail (IOEXB ACO)

Cluster 2 validates the ACO IoExb configuration against `CFG_SECTION_OUT`. The primary inputs come from FCT ComAebMap's `acoIoExbs` field.

What gets validated:

- **IoExb position within the board rack.** Each ACO IoExb has a slot/position in the rack; this must match what the ADC declares.
- **Number of ACO IoExbs attached to each AEB.** Count from ComAebMap vs count from ADC `CFG_SECTION_OUT` block occurrences.
- **Block count per file.** `CFG_SECTION_OUT` ≤ 16 per file is the documented bound.

Note again the Cluster 1/2 ownership rule (§7.1): Cluster 2 owns `ID` and `SECTION` on `CFG_SECTION_OUT` with different semantics than Cluster 1's ID/SLCT_TIMEOUT. Both run; no failure-mode overlap.

---

## 10. Clusters 3–6 (current state)

Clusters 3 through 6 have a known shape but most block/key specifics are pending against the Confluence requirements document. Inline TBDs are marked here; summary is in §12.

**Cluster 3 — Track Section.** 4 sub-checks (CHC counting head assignment, sensors-per-track, supervisory track config, ACO/DT IO assignment). **All validate the ADC against the baseline** (Control Table `dpIn`/`dpOut` + `fadcAutoReset`); the baseline (PDQ + FCT) is the sole source of truth, so **no external input is required** (earlier "station layout" assumption dropped).

**Cluster 4 — CHC / External CHC.** Combined cluster spanning the standard CHC and the External CHC sub-cluster. CHC count derivation is locked: `BEHAV_IN3 = 7 → 1 CFG_CONTROL block`; `BEHAV_IN3 = 6 → 2 CFG_CONTROL blocks`. Adjacency from ConfigControlTable Track table. External CHC scope: `CFG_CONTROL` ≤ 2 per ADC. **[TBD: full blocks, keys list, sub-cluster split]**

**Cluster 5 — Supervisor.** Mapped to FMA Supervisor work. **[TBD: blocks, keys — 3B FMA Supervisor block details pending re-confirmation against Confluence requirements doc]**

**Cluster 6 — Data Transmission.** Blocks locked: `CFG_DATA_OUT`, `CFG_DATA_SAFETY_LEVELS`. The earlier-noted `CFG_DATA_IO SAFETY_LEVEL_IN/OUT` reference was incorrect and is discarded. **[TBD: full keys list, rule semantics]**

---

## 11. Out of scope (Phase 2)

Items explicitly excluded from Phase 2; flag if asked.

- **COM file IP validation.** Deferred. If reintroduced later, **static checks only** (valid format, distinct, exists in expected list). Not file-sourced cross-referencing. The earlier `ComIpMap` and self-IP findings have been dropped.
- **3G as a separate cluster.** Subsumed into Cluster 1 (CAN Segment). No non-AEB ID references exist between COM files; cross-file ID resolution is identical to the segment-membership lookup in Cluster 1's Check A. Worth flagging in the work-package breakdown to Salai/Max — removes one new-engine-capability dependency from Phase 2 scope.
- **Trackplan XMLs inside the FCT archive.** Only `Project.xml` is parsed. Trackplan XMLs are not opened.
- **Counting-head-output mode IoExbs.** Only ACO and DT modes are in scope. FCT is rejected if a counting-head-output mode IoExb is present.
- **Hidden content inside the PDQ workbook.** The parser respects sheet/row/column visibility flags. Hidden sheets, rows, and columns are not parsed (§6.1).

---

## 12. Open items (TBDs, summary)

Tracked in two pools — items needing AE coordination, and items needing backend decisions or follow-up.

### AE-side

| # | Item | Blocks / context | Status |
|---|---|---|---|
| A1 | PDQ workbook corrections — originally CQ-IR bracket additions, `CFG_TIMEOUT` unit fix, new row 1.08, populated DT sample | PDQ parser correctness | **Resolved** — workbook rebuilt (Config Key column, normalized values, units in question text, rows 1.08 + 1.09 added). Bracket additions obsolete (Config Key column replaces bracket scanning). Populated DT Inputs sample still pending. |
| A2 | Confirm exact spelling/casing for the PDQ row 1.08 response (`Single`/`Dual`) | Project-block `BLOCK_EXISTS` derivation | **Superseded (Ver14)** — row 1.08 removed; `BLOCK_EXISTS` / `PROJECT_NUMBER` now derive from the CQ-IR `PROJECT_NUMBER` row (Response `YES`/`NO` + Remarks). |
| A3 | Reset Type catalog enumeration in the Control table — full set of allowed values | Control table parsing (§6.5) | **Open** — carried as a Control table validation list; full set pending AE. |
| A4 | MappingProperties values for `IP_SWITCH_TIME`, `RESET_DELAY`, `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME`, `NMBR_OUT`, plus hex pattern for protection code | Backend MappingProperties additions | **Resolved** — step values locked (§6.3 transform table). Hex pattern moot: `TYPE_PRTCT_CODE` is now tpf-sourced, not PDQ. |
| A5 | Phase 2 input files formally shared with Max; PDQ document circulated | AE coordination (see `fcvt-meeting-and-strategy.md`) | **Open**. |
| A6 | Cluster-by-cluster scope finalised as discrete work packages with explicit AE agreement | Cluster-to-BE story mapping | **Open**. |
| A7 | PDQ Excel format finalised (Control Table + CQ-IR) and Phase 2 validation report output format agreed | AE coordination | **Partially resolved** — PDQ Excel format rebuilt and locked with validation/password gating. Report output format still to be agreed. |
| A8 | Version-aware group source (`TYPE_IN1/2/3`, `TYPE_AUX1/2`, `SUPERVIS_COUNT_LMT`) | Version-aware emission (§6.4) | **Resolved** — sourced from PDQ row 1.09 (AEB Equipment Version): omitted for GS05-and-below, property-file defaults for GS06-and-above. |

### Backend-side

| # | Item | Blocks | Status |
|---|---|---|---|
| B1 | PDQ upload response shape | (§3.2, §6) | **Resolved** — block-grouped shape locked. |
| B2 | Engine composite-key lookup mechanism — JSONPath in rule config vs named Java lookups | BE-05 onwards | **Open**. |
| B3 | Exact 8 root filenames + 2 folder names in the FCT archive | FCT identification + malformed-FCT rejection (§5.1) | **Open**. |
| B4 | Spring Boot 4.0.2 multipart default override behaviour — verify | FCT and PDQ upload pipelines | **Open**. |
| B5 | MappingProperties file updates for the missing keys listed in A4 | PDQ parser unit conversion (§6.3) | **Resolved** — step values locked. |
| B6 | `CFG_TIMEOUT` alias resolution against `TIMEOUT_VALUE` | PDQ special-case row (§6.4) | **Resolved** — `TIMEOUT_VALUE` always nests under the `CFG_TIMEOUT` block (hardcoded special case). |
| B8 | Cluster 4 full blocks/keys (CHC / External CHC), sub-cluster split | BE-07 / BE-08 | **Open**. |
| B9 | Cluster 5 blocks and keys (Supervisor); 3B FMA Supervisor re-confirmation | BE-09 | **Open**. |
| B10 | Cluster 6 full keys list and rule semantics | BE-11 | **Open**. |
| B11 | Counting-head-output IoExb rejection — exact error code mapping | FCT upload error contract finalisation (§5.7) | **Open**. |
| B12 | Control table `fadcAutoReset` parsing decision — raw string vs structured logic tree | (§6.5) | **Resolved** — structured logic tree. |
| B13 | DT sheet `SAFE_OUT_FDBCK_QUAD` JSON shape — boolean vs numeric | (§6.6) | **Resolved** — numeric `0`/`1`. |
| B14 | Validate-time cross-correlation rules — project-block consistency, dual-FMA consistency, `CFG_IP_SWITCH` derivation from `redundantComPresent`, `CFG_RSR_TYPE` PDQ↔tpf consistency (Ver14) | Specified §4.1; implementation tied to BE cluster work | **Open** — specified, not yet implemented. |

---

## 13. Epic and child-story structure (VTF 2.0.0)

The Phase 2 epic is **VTF 2.0.0**. Formal target: end of July 2026. (See `fcvt-meeting-and-strategy.md` for Max's informal pressure toward end of June and the negotiation context.)

Child stories grouped by frontend / backend cluster work:

**Frontend (FE-01 through FE-03):**
- FE-01 — CFG_SWITCH UI (carries over the Phase 1 pending CFG_SWITCH work, codebase.md §14).
- FE-02 — FCT2 and PDQ upload wiring.
- FE-03 — Post-PDQ form freeze (the read-only state once both uploads are in).

**Backend infrastructure (BE-01 through BE-04):**
- BE-01 / BE-02 — PDQ parser. BE-01 covers CQ-IR; BE-02 covers ConfigControlTable.
- BE-03 — FCT2 parser. The endpoint design in §5.
- BE-04 — PDF report generation. **Stretch goal** for Phase 2; not on the critical path.

**Backend cluster work (BE-05 through BE-11):** one story per cluster.

- BE-05 — Cluster 1 (CAN Segment)
- BE-06 — Cluster 2 (IOEXB ACO) — *or BE-06 → Cluster 3 (Track Section); mapping by code rather than cluster number is the working convention*
- BE-07, BE-08, BE-09, BE-10, BE-11 — one per remaining cluster

The specific BE-cluster mapping is subject to finalisation in the work-package breakdown to AE (see meeting-and-strategy.md for the May 7 sync where this was actioned).

---

*End of Phase 2 design — current to May 2026 design freeze, with PDQ-parser detail absorbed from the Vinod sync notes (May 2026). Updates required as cluster requirements close out and TBDs resolve.*
