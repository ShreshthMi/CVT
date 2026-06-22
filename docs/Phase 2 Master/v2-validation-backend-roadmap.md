# FCVT v2 Validation — Backend Roadmap Overview

*A walkthrough of how configuration validation works today (Phase 1), what we are building for Phase 2, and how a request flows end-to-end through the v2 backend.*

---

## 1. Phase 1 — how validation flows today

Phase 1 answers one question: **does each uploaded config file match the expected values a user typed in?** The expected baseline is supplied by a human; the backend just compares it against the parsed files.

### The request

`POST /api/config/validate` receives a **`ValidationRequestWrapper`**:

```jsonc
{
  "parsedConfigFiles": [ /* the parsed .ADC/.cfg files under test */ ],
  "userInput": {                 // UserValidationInputCriteria — a flat block→entry→value map
    "CFG_SECTION":   { "COMM_FAIL": "0", "BEHAV_GE": "1" },
    "CFG_TIMEOUT":   { "TIMEOUT_VALUE": ["34", "..."] },
    "ID":            { "ID": { "min": "1", "max": "4095" } }
  }
}
```

- **`parsedConfigFiles`** — each file is already parsed into **blocks → entries (key/value/comment)**, and classified by type (**COM** vs **AEB**, with AEB sub-markers `TRACKSECTIONDETAILS` / `ACOIOEXBDETAILS` / `DTIOEXBDETAILS`).
- **`userInput`** — the human-supplied expected values: `block → { entry → expected }`, where an expected value is a scalar, a list, or a `{min,max}` range.

### What the backend does

```
ValidationRequestWrapper
        │
        ▼
1. Guards            ≥1 file? userInput present? sections non-empty?     (else 400)
        │
        ▼
2. Resolve payloads  PayloadValidator turns userInput into resolved expected values
        │            (400 if a required UI input is missing)
        ▼
3. Build universe    configured rule keys (ValidationConfiguration.json, ~41 rules)
        │            ∪ the (block,entry) keys present in userInput
        ▼
4. Per file × key    decision engine → APPLY_RULE / APPLY_DEFAULT / IGNORE
        │            (file applicability: ValidateOnlyInFilesWith, SkipComFile)
        ▼
5. Compare           the matched rule (1 of 9 types) compares
        │              EXPECTED (from userInput)  vs  ACTUAL (FileContext.values(block,entry),
        │              i.e. the raw values across all occurrences in the file)
        │            → ValidationResult(file, block, entry, expected, actual, PASS/FAIL)
        ▼
6. Summarise         SummaryService.generateSummary(files, results)
        ▼
   ValidationSummary  (HTTP 200)
```

**The 9 rule types** decide *how* a key is compared: `InputMatch`, `InputMatchOrBlockNotFound`, `OptionalInputMatch`, `OptionalInputMatchOrBlockNotFound`, `RangeCheck`, `DuplicateCheck`, `MultipleBlockSingleInputMatch`, `MultipleBlockMultipleInputMatch`, `ProjectBlockCheck`.

### The response

A **`ValidationSummary`**: the `validation_results[]` (one row per checked key) **plus 8 extracted detail tables** for display — `dp_details`, `track_section_details`, `chc_details`, `supervisor_details`, `ioexb_behaviour_details`, `ioexb_aco_details`, `data_transmission_details`, `ethernet_details`.

> **Phase 1 in one line:** *expected = what the human typed; actual = the raw ADC block/entry values; the rule type decides the comparison.*

---

## 2. What Phase 2 changes

The core shift: **a human no longer types the expected baseline.** Instead we derive it automatically from two uploaded engineering artifacts and validate the ADC against that derived, cross-checked baseline.

- **FCT** (functional-test export, `.fct2`) → parsed to **`ComAebMap`** — the *as-engineered logical truth*: COMs, the DPs/AEBs on each, their FMAs, and their ACO / DT cards.
- **PDQ** (project questionnaire workbook, `.xlsx`) → parsed to **`PdqUploadResponse`** — the *project requirements*: cqIR config words, the Control Table (track sections + DP table), and Data Transmission inputs.
- Optional **tpf** parameters (e.g. `CFG_RSR_TYPE`) supplied alongside.

New endpoints:

| Endpoint | Body in | Body out |
|---|---|---|
| `POST /api/upload/fct` | `.fct2` | `ComAebMap` |
| `POST /api/upload/pdq` | `.xlsx` | `PdqUploadResponse` |
| `POST /api/config/v2/validate` | `ValidationRequestV2` | `ValidationSummary` |

### The shapes, briefly

**FCT response — `ComAebMap`** (the logical structure):
```
chains[]                              // one per CAN segment / COM
  com { comId, comName }
  redundantComPresent                 // drives CFG_IP_SWITCH
  aebs[]                              // the DPs on this COM
    dpId, dpName
    evaluatedFmas[] { fmaName, fmaId, dpId }      // the track sections
    acoIoExbs[]     { output FMA(s), incl. cross-DP refs }
    dtIoExbCount
```

**PDQ response — `PdqUploadResponse`** (the requirements):
```
projectCode, aebEquipmentVersion
cqIrParameters  : block → entry → value          // project-wide config words
controlTable    : { trackSections[], dpTable[] }  // track defs + DP table
dataTransmission: { dataSafetyLevels[], outputDataTransmission[] }
```

**Validation request — `ValidationRequestV2`**:
```
parsedConfigFiles[]                  // the ADC under test (same as Phase 1)
userInput (ValidationInputV2):
  fctData  : ComAebMap               // mandatory baseline
  pdqData  : PdqUploadResponse       // mandatory baseline
  <tpf blocks>                       // optional, e.g. CFG_RSR_TYPE
```

**Validate response — `ValidationSummary`**: *unchanged from Phase 1* (the same `validation_results[]` + 8 detail tables). Keeping the response shape stable means downstream consumers don't change.

---

## 3. Phase 2 — request to response (the main flow)

```
ValidationRequestV2  (parsedConfigFiles + fctData + pdqData + tpf)
        │
        ▼
1. Coupled-artifacts gate     both FCT and PDQ present?        (else 400 PHASE2_INPUTS_INCOMPLETE)
        │
        ▼
2. ExpectationsPreprocessor   ── turns FCT + PDQ into the expected baseline ──
        │
        ├─ (a) GATE / admission checks  (hard-stop 400 on a bad baseline)
        │       • Track reconciliation: every Control-Table track must be found among the
        │         FCT track sections (NOT-FOUND); no unmatched FCT extras (EXTRA).
        │       • RSR_TYPE consistency: PDQ cqIR vs tpf, when both supplied.
        │
        └─ (b) FLATTEN the baseline into two buckets
                • scalarExpectations    — project-wide config words (block/entry/value)
                • instancedExpectations — per-entity expected values, keyed by entity identity
                                          (track sections, ACO, DT, CFG_IP_SWITCH per COM)
        │
        ▼
3. Validation engine          ── extends the Phase-1 engine ──
        │
        ├─ scalarExpectations    → run through the EXISTING rule engine over the ADC files
        │                          (same 9 rule types, range/match/etc.)        → results
        │
        └─ instancedExpectations → instance-aware pass: resolve each entity's identity to the
                                   matching ADC occurrence, compare, emit results   → results
        │
        ▼
4. Summarise                  SummaryService.generateSummary(files, allResults)
        │
        ▼
   ValidationSummary          (HTTP 200)   —  or a baseline 400 from step 1/2(a)
```

### How this maps to Phase 1

| | Phase 1 | Phase 2 |
|---|---|---|
| **Expected source** | human-typed `userInput` | derived from FCT + PDQ by the preprocessor |
| **Actual source** | raw ADC block/entry values | raw ADC block/entry values *(unchanged)* |
| **Engine** | the rule engine | the **same** rule engine for `scalarExpectations`; a new instance-aware pass for `instancedExpectations` |
| **Response** | `ValidationSummary` | `ValidationSummary` *(unchanged)* |

The design intent is **maximum reuse**: the bulk of the work (all the project-wide config words, and the cross-correlation rules that reduce to scalar checks) flows straight through the existing Phase-1 engine. The genuinely new piece is the **preprocessor** (deriving + gating the baseline) and the **instance-aware validation** for per-entity expectations.
