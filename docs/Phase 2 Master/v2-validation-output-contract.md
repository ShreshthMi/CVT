# v2 Validate — Validation Output (response) contract

> **Status:** agreed 2026-06-29 with FE input (the *FAdC Configuration Validation Tool — Phase 2 Enhancements* doc, §2 Validation Output). Refines the response side of `POST /api/config/v2/validate`; the base envelope is the Phase-1 shape (`validate-v2-response-contract.wiki`). **Additive only** — a zero-mismatch v2 response is byte-identical to a Phase-1 response.
>
> **What this enables (FE §2):** red-highlight a mismatching cell *inside the detail tables*, show an Expected/Actual tooltip on hover, and on click jump to the **exact** validation-result log entry for that check (and highlight its ADC file).

> **FE-facing contract:** the authoritative wire shape — including the union-array model (`MISSING` is an empty array slot at a real `index`) and the value-check-vs-existence-check semantics — is in [`FCVT-v2-Validation-Response-Contract.md`](FCVT-v2-Validation-Response-Contract.md) (the version shared with the frontend). This document additionally records the backend implications.

## 1. Principles
1. **Two surfaces, two jobs.** The **detail tables** are the highlight/tooltip surface; **`validation_results[]`** is the drill-down log + navigation target.
2. **Additive annotations.** A clean cell is unchanged. Mismatches are carried in a per-row `_mismatches` block; presence of an entry there *is* the red flag (no per-cell status field).
3. **Navigate by check, not by file.** Every `validation_results` entry has an opaque `id`; each mismatch references it (`result_id`). The cell → log-entry jump is by `id`, so the filename-vs-file_id mismatch is irrelevant. The target entry's `fileName` drives the file-row highlight.
4. **Display form, not raw.** `expected`/`actual` in annotations are display values (DP **names**, mapped enums — e.g. `DP53`, not id `53`), matching what the detail cell shows.

## 2. New field — `validation_results[].id`
Each result entry gains an **opaque, response-scoped** unique `id` (e.g. `"r0".."rN"`, assigned by the backend as results are emitted). Unique within one response; not stable across re-runs (navigation only happens within a loaded response, so stability isn't required). Present on **all** entries (PASS and FAIL). No other change to the 7-field row.

## 3. New per-detail-row block — `_mismatches[]`
A detail-table row with one or more failures carries a `_mismatches` array. A row with none omits it (stays Phase-1-shaped).

Each entry:

| Field | Type | Meaning |
|---|---|---|
| `field` | string | the JSON field in this row the mismatch is on (e.g. `ch_dp_name`, `behav_input3`, `time_out`) |
| `index` | int \| null | array-element index for array fields; `null` for a scalar field or a `MISSING` item |
| `kind` | enum | `VALUE` \| `UNEXPECTED` \| `MISSING` (see below) |
| `expected` | string \| null | display-form expected value (`null` for `UNEXPECTED`) |
| `actual` | string \| null | display-form actual value = the cell/element value (`null` for `MISSING`) |
| `result_id` | string | the `id` of the `validation_results` entry for this check — the navigation target |

**`kind` semantics** (this is the set-vs-positional resolution):
- **`VALUE`** — cell/element present, value wrong. Covers scalar mismatches, positional-slot (ACO) mismatches, and a matched set-member's attribute (e.g. a head's `ch_slct_timeout`). `expected`+`actual` both set.
- **`UNEXPECTED`** — *set columns only*: the element at `index` is present in the ADC but **not in the baseline** (a stray/extra). `expected=null`.
- **`MISSING`** — *set columns only*: a baseline-expected item is **absent** from the ADC. `index=null`, `actual=null`.

> Why two kinds for sets: counting heads / CHC / forwarding are validated by **strict set-equality** (unordered), so a wrong head is *not* a positional "expected DP53 / actual DP52" — it is "DP52 is unexpected" **and** "DP53 is missing", two separate checks. Single-valued / positional cells use `VALUE`.

**FE rendering:**
- `VALUE` → red `field`(`[index]`); tooltip *Expected {expected} / Actual {actual}*.
- `UNEXPECTED` → red element `field[index]`; tooltip *Unexpected — not in baseline*.
- `MISSING` → render an extra red chip by `field` showing `expected`; tooltip *Missing — expected, not configured*.
- any → on click, jump to `validation_results` where `id == result_id`; highlight that entry's `fileName` row.

## 4. FE-behaviour coverage (§2)
| FE behaviour | Contract element |
|---|---|
| 2.1 mismatch detection / metadata to UI | `_mismatches[]` per row |
| 2.2 red highlight in output tables | render any field referenced by a `_mismatches` entry |
| 2.3 tooltip Expected/Actual | `expected` / `actual` (display form) |
| 2.4 click → locate the exact result log entry | `result_id` → `validation_results[].id` |
| 2.5 highlight associated ADC file row | target entry's `fileName` |

## 5. Example

> A complete, validated example — all 8 detail tables populated, every `_mismatches` kind exercised, `result_id`s cross-checked (0 dangling) — is in [`v2-validation-output-sample.json`](v2-validation-output-sample.json). Inline snippet:

```jsonc
{
  "validation_results": [
    { "id":"r12", "fileName":"C0011_00.ADC", "ruleType":"IdentitySetMatch",
      "blockName":"CFG_ZP_FMA1", "entryKey":"ID=52",
      "expectedValue":"not in baseline", "actualValue":"DP52", "status":"FAIL" },
    { "id":"r13", "fileName":"C0011_00.ADC", "ruleType":"IdentitySetMatch",
      "blockName":"CFG_ZP_FMA1", "entryKey":"ID=53",
      "expectedValue":"DP53", "actualValue":"EXPECTED_OCCURRENCE_NOT_FOUND", "status":"FAIL" },
    { "id":"r20", "fileName":"C0011_00.ADC", "ruleType":"InputMatch",
      "blockName":"CFG_AXCNT", "entryKey":"BEHAV_INPUT3",
      "expectedValue":"6", "actualValue":"7", "status":"FAIL" }
  ],

  "track_section_details": [
    { "ts_name":"1AXT1", "e_dp_id":"11", "e_dp_name":"DP11", "fma_1_2":"1",
      "ch_dp_name":["DP52"], "ch_dp_id":["52"], "ch_slct_timeout":["1"],
      "i_ch_dp_name":[], "i_ch_dp_id":[], "i_ch_slct_timeout":[],
      "_mismatches":[
        { "field":"ch_dp_name", "index":0,    "kind":"UNEXPECTED", "expected":null,   "actual":"DP52", "result_id":"r12" },
        { "field":"ch_dp_name", "index":null, "kind":"MISSING",    "expected":"DP53", "actual":null,   "result_id":"r13" }
      ]
    }
  ],

  "ioexb_behaviour_details": [
    { "dp_id":"11", "dp_name":"DP11", "behav_input3":"7",
      "_mismatches":[
        { "field":"behav_input3", "index":null, "kind":"VALUE", "expected":"6", "actual":"7", "result_id":"r20" }
      ]
    }
  ],

  "dp_details":[ { "dp_can_id":"11", "dp_name":"DP11", "comm_fail":"0" } ],   // clean → no _mismatches
  "chc_details":[], "supervisor_details":[], "ioexb_aco_details":[],
  "data_transmission_details":[], "ethernet_details":[]
}
```

## 6. Backend implications (the work behind the shape) — IMPLEMENTED (BE-07 / VTF-337, 2026-06-29)
The shape is additive and small; populating it is the engineering — the **verdict→detail-cell join** (reconciliation findings C1/C2). As built (`MismatchAnnotator`, `origin/VTF-337`; see *vtf-337-scope.md*):
1. The **`(block,entry) → (detail table, column)` registry** lives in the annotator's per-block dispatch (the mapping is not 1:1 — e.g. counting-head DIR_INV selects ch vs i_ch — so it is code, not a data table).
2. **Lossless join inputs:** rather than dropping the ACO comment-dedup in the *shared* extractor (which would change Phase 1 output), the annotator **reconstructs** the un-deduped block-order ACO view itself. Set columns are aligned to array indices by matching the result's raw identity against the row's parallel raw-id array (e.g. `ch_dp_id`), and the instanced evaluator emits an `InstancedFinding` carrying the raw coordinate so no result-string parsing is needed.
3. **Set-equality → `UNEXPECTED` + `MISSING`** for set columns (with union-array padding across parallel arrays); **`VALUE`** for scalar/positional.
4. **Raw→display translation** of `expected`/`actual` mirrors each extractor's per-field transform (`ValueMappingService`, FCT id→name, `SLCT_TIMEOUT`→`CFG_TIMEOUT`×10, `SECTION`+1).
5. The **`MismatchAnnotator`** post-pass runs after `SummaryService.generateSummary` (v2 only), mutating the summary in place; a clean run adds nothing (Phase-1-shaped).

**Results-only (no faithful cell):** counting-head `DIR_INV`, ACO `ID` (`aco_fmaId` not displayed), ACO/CHC `MISSING` with no array slot, and scalar cross-rules with no detail column (project / RSR / switch). DT has no validation results yet (BE-14).

## 7. Open items / coverage notes
- **Not every result maps to a cell.** Scalar results with no detail column (e.g. `CFG_SWITCH`, and the M4-noise FAILs) appear only in `validation_results[]`, not as red cells. That's expected.
- **Named verdicts** (`ORPHANED`/`INVALID SCOPE`/…, phased to BE-08): land on the `validation_results` entry (`status` + sentinel), **not** the cell tooltip (cells stay Expected/Actual). Confirm with FE if any need surfacing on cells.
- **Completeness:** an expected ADC file that was not uploaded is currently skipped; if it should surface as a failure, it becomes a synthetic `validation_results` entry (no detail cell).
