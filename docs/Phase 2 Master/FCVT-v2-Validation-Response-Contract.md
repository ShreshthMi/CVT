# FCVT — v2 Validation Response Contract

Response contract for `POST /api/config/v2/validate`. It supports the Phase 2 Validation Output screen: per-cell mismatch highlighting, Expected/Actual tooltips, and click-through from a highlighted cell to its validation-result log entry.

The response keeps the Phase 1 shape — `validation_results[]` plus the eight detail tables — and adds two things: a stable `id` on each result entry, and an optional `_mismatches` block on any detail row. **A run with no mismatches is identical to a Phase 1 response.**

---

## 1. Success response (HTTP 200)

```jsonc
{
  "validation_results": [ /* §2 */ ],
  "dp_details": [ ... ],
  "track_section_details": [ ... ],
  "chc_details": [ ... ],
  "supervisor_details": [ ... ],
  "ioexb_behaviour_details": [ ... ],
  "ioexb_aco_details": [ ... ],
  "data_transmission_details": [ ... ],
  "ethernet_details": [ ... ]
}
```

The eight detail tables carry the same fields as today, with two additions: an optional `_mismatches` array on a row that has one or more failures (§4), and a `slot` field on `ioexb_aco_details` (VTF-371).

`ioexb_aco_details` now carries **one row per `CFG_SECTION_OUT` block** rather than one per distinct ACO FMA name. An ACO card may legitimately drive the same track section from both of its outputs, and those two rows are identical in every other column — `slot`, the 0-based block ordinal, is what tells them apart. Do not key ACO rows on `aco_fma1` within a host AEB; it is no longer unique.

---

## 2. `validation_results[]`

The validation log. One entry per check.

| Field | Type | Description |
|---|---|---|
| `id` | string | **Unique key for this entry.** The navigation target a highlighted cell links to (§5). |
| `fileName` | string | The ADC file this check ran against (e.g. `C0005_00.ADC`). |
| `ruleType` | string | The rule that produced the verdict (e.g. `RangeCheck`, `InputMatch`, `IdentitySetMatch`). |
| `blockName` | string | Config block (e.g. `CFG_ZP_FMA1`). |
| `entryKey` | string | The entry / instance checked (e.g. `DIR_INV[ID=5]`). |
| `expectedValue` | string | Expected value, or a state marker (§6). |
| `actualValue` | string | Actual value, or a state marker (§6). |
| `status` | string | `PASS` \| `FAIL` \| `INVALID`. |

`id` is unique within a response; treat it as opaque (do not parse it).

---

## 3. Detail tables

Unchanged from Phase 1 in field shape. Each row may additionally carry a `_mismatches` array (§4). A row with no failures has no `_mismatches` key and renders exactly as today.

The tables: `dp_details`, `track_section_details`, `chc_details`, `supervisor_details`, `ioexb_behaviour_details`, `ioexb_aco_details`, `data_transmission_details`, `ethernet_details`.

---

## 4. Mismatch annotations — `_mismatches`

A detail row with failures carries a `_mismatches` array. Each element pinpoints one failing cell, gives its Expected/Actual, and links to the log entry.

```jsonc
{ "field": "ch_dp_name", "index": 1, "kind": "UNEXPECTED",
  "expected": null, "actual": "DP2B", "result_id": "r1" }
```

| Field | Type | Description |
|---|---|---|
| `field` | string | The row field the failure is on (matches a JSON key in this row, e.g. `behav_input3`, `ch_dp_name`). |
| `index` | int \| null | For an **array** field, the element index. `null` for a **scalar** field. |
| `kind` | string | `VALUE` \| `UNEXPECTED` \| `MISSING` (below). |
| `expected` | string \| null | Expected value (display form). `null` for `UNEXPECTED`. |
| `actual` | string \| null | Actual value (display form). `null` for `MISSING`. |
| `result_id` | string | The `validation_results[].id` for this check — the click target (§5). |

### 4.1 How to read it
- **A cell is a failure if, and only if, it is referenced by a `_mismatches` entry.** Do not infer state by comparing values — read the `kind`. A correct cell is simply absent from `_mismatches`.
- Expected/Actual are **display form** — the same form shown in the cell (DP names like `DP53`, mapped enums like `OR`), not raw ids/codes.

### 4.2 The three kinds
There are two categories of check. **Value checks** compare a value; **existence checks** ask whether an item belongs.

| `kind` | Category | `expected` | `actual` | Meaning |
|---|---|---|---|---|
| `VALUE` | value | the correct value | the cell value | the cell is present but its value is wrong |
| `UNEXPECTED` | existence | `null` | the cell value | the value is present but is **not** in the baseline — it shouldn’t be there (so there is no value it “should be”) |
| `MISSING` | existence | the expected value | `null` | the baseline expects this value but it is **absent** from the configuration |

`UNEXPECTED` has `expected: null` by design — it is an existence verdict, not a value comparison. That `null` is not the same as a correct cell: a correct cell has **no `_mismatches` entry at all**.

### 4.3 Array fields and `MISSING` (union model)
For array columns (e.g. counting-head lists, forwarding lists), the array is the **union of the configured and the expected items**, so every item — including a missing one — has a real slot:
- a configured item that belongs → a normal cell (no entry);
- a configured item not in the baseline → `UNEXPECTED` at its index;
- an expected item that is absent → `MISSING` rendered as an **empty slot appended at a real index**, with its `expected` set.

Parallel arrays stay index-aligned: when a `MISSING` slot is added to one array (e.g. `ch_dp_name`), the sibling arrays (`ch_dp_id`, `ch_slct_timeout`) get an empty entry at the same index.

When nothing is missing or unexpected, arrays contain only configured items — identical to Phase 1.

### 4.4 Rendering
For each `_mismatches` entry, highlight the cell at `field`(`[index]`) and, on hover, show the tooltip:
- `VALUE` → *Expected `{expected}` / Actual `{actual}`*
- `UNEXPECTED` → *Unexpected — not in baseline* (value = `actual`)
- `MISSING` → *Missing — expected `{expected}`* (the slot is empty)

On click → navigate to the entry in `validation_results` whose `id == result_id` (§5).

---

## 5. Navigation (cell → log entry)

1. Each `_mismatches` entry carries `result_id`.
2. Find the `validation_results` entry where `id == result_id` — that is the exact check.
3. That entry’s `fileName` identifies the ADC file, for highlighting the file’s row in the result log.

The link is by `id`, so no filename/id correlation on the cell side is required.

---

## 6. State markers

`expectedValue` / `actualValue` in `validation_results` (and occasionally a tooltip) may hold a **state marker** instead of a literal value. Render these as a state, not as a value:

| Marker | Meaning |
|---|---|
| `CONFIG_BLOCK_NOT_FOUND` | the whole block is absent |
| `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` | the block/entry is absent |
| `EXPECTED_OCCURRENCE_NOT_FOUND` | an expected instance is absent (maps to a `MISSING` cell) |
| `UNEXPECTED_OCCURRENCE` | an instance is present but not expected (maps to an `UNEXPECTED` cell) |
| `not in baseline` | shorthand for the above, used as a tooltip-friendly `expected` |
| `RANGE_NOT_CONFIGURED` | a range check had no configured bounds |

---

## 7. Error responses (HTTP 400)

When the request cannot be validated, the endpoint returns **HTTP 400** with:

```jsonc
{ "errorCode": "PHASE2_BASELINE_INCONSISTENT",
  "message": "Human-readable explanation (e.g. which tracks did not reconcile)." }
```

| `errorCode` | When |
|---|---|
| `PHASE2_INPUTS_INCOMPLETE` | exactly one of FCT / PDQ supplied (both are required) |
| `PHASE2_CONTROL_TABLE_MISSING` | the PDQ Control Table is missing or empty |
| `PHASE2_TRACK_RECONCILIATION_FAILED` | PDQ tracks don’t reconcile with the FCT (the `message` names the not-found / extra tracks) |
| `PHASE2_BASELINE_INCONSISTENT` | the FCT/PDQ baseline is internally inconsistent (e.g. RSR_TYPE conflict, an unresolvable forwarding destination) |

Display the `message`; no `validation_results` are produced in this case.

---

## 8. Complete example

A validation run over two CAN segments (COM100, COM200). Four planted failures show every annotation kind; all other cells pass.

```json
{
  "validation_results": [
    { "id": "r1", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_ZP_FMA1", "entryKey": "ID=6", "expectedValue": "not in baseline", "actualValue": "DP2B", "status": "FAIL" },
    { "id": "r2", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_ZP_FMA1", "entryKey": "ID=1", "expectedValue": "DP2A", "actualValue": "EXPECTED_OCCURRENCE_NOT_FOUND", "status": "FAIL" },
    { "id": "r3", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_ZP_FMA1", "entryKey": "DIR_INV[ID=5]", "expectedValue": "0", "actualValue": "0", "status": "PASS" },
    { "id": "r4", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_ZP_FMA1", "entryKey": "SLCT_TIMEOUT[ID=5]", "expectedValue": "0", "actualValue": "0", "status": "PASS" },
    { "id": "r5", "fileName": "C0001_00.ADC", "ruleType": "RangeCheck", "blockName": "ID", "entryKey": "ID", "expectedValue": "1 - 4095", "actualValue": "1", "status": "PASS" },
    { "id": "r6", "fileName": "C0001_00.ADC", "ruleType": "InputMatch", "blockName": "CFG_SECTION_OUT", "entryKey": "CLR_OCC", "expectedValue": "0", "actualValue": "0", "status": "PASS" },
    { "id": "r7", "fileName": "C0002_00.ADC", "ruleType": "RangeCheck", "blockName": "ID", "entryKey": "ID", "expectedValue": "1 - 4095", "actualValue": "2", "status": "PASS" },
    { "id": "r8", "fileName": "C0100_00.ADC", "ruleType": "ProjectBlockCheck", "blockName": "CFG_PROJECT_COM", "entryKey": "PROJECT_NUMBER", "expectedValue": "9898", "actualValue": "9898", "status": "PASS" },
    { "id": "r9", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_SUPERVIS_FMA2", "entryKey": "SLCT_TIMEOUT[ID=1,SECTION=0]", "expectedValue": "1", "actualValue": "1", "status": "PASS" },
    { "id": "r10", "fileName": "C0001_00.ADC", "ruleType": "InputMatch", "blockName": "CFG_AXCNT", "entryKey": "BEHAV_INPUT3", "expectedValue": "6", "actualValue": "7", "status": "FAIL" },
    { "id": "r11", "fileName": "C0008_00.ADC", "ruleType": "RangeCheck", "blockName": "ID", "entryKey": "ID", "expectedValue": "1 - 4095", "actualValue": "8", "status": "PASS" },
    { "id": "r12", "fileName": "C0100_00.ADC", "ruleType": "OptionalInputMatchOrBlockNotFound", "blockName": "CFG_IP_SWITCH", "entryKey": "IP_SWITCH", "expectedValue": "0", "actualValue": "CONFIG_BLOCK_NOT_FOUND", "status": "PASS" },
    { "id": "r14", "fileName": "C0006_00.ADC", "ruleType": "InputMatch", "blockName": "CFG_AXCNT", "entryKey": "BEHAV_INPUT3", "expectedValue": "6", "actualValue": "6", "status": "PASS" },
    { "id": "r15", "fileName": "C0001_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_CONTROL", "entryKey": "SLCT_TIMEOUT[ID=5,SECTION=0]", "expectedValue": "0", "actualValue": "0", "status": "PASS" },
    { "id": "r20", "fileName": "C0005_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_SUPERVIS_FMA2", "entryKey": "LOGIC_TYPE[ID=1,SECTION=0]", "expectedValue": "OR", "actualValue": "AND", "status": "FAIL" },
    { "id": "r30", "fileName": "C0100_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_FWRD_ACD", "entryKey": "CAN_TX_ID=2,DEST_COM=200", "expectedValue": "not in baseline", "actualValue": "DP3A to COM200", "status": "FAIL" },
    { "id": "r31", "fileName": "C0100_00.ADC", "ruleType": "IdentitySetMatch", "blockName": "CFG_FWRD_ACD", "entryKey": "CAN_TX_ID=1,DEST_COM=200", "expectedValue": "DP2A to COM200", "actualValue": "EXPECTED_OCCURRENCE_NOT_FOUND", "status": "FAIL" }
  ],

  "dp_details": [
    { "dp_can_id": "5", "dp_name": "DP1A", "time_out": ["620", "620"], "comm_fail": "0", "behav_ge": "1", "clr_track": "0", "reset_in": "5", "reset_out": "1", "behav_reset": "4", "behav_simul": "0" },
    { "dp_can_id": "1", "dp_name": "DP2A", "time_out": ["620"], "comm_fail": "0", "behav_ge": "1", "clr_track": "0", "reset_in": "5", "reset_out": "1", "behav_reset": "4", "behav_simul": "0" }
  ],

  "track_section_details": [
    {
      "ts_name": "1AXT1", "e_dp_id": "5", "e_dp_name": "DP1A", "fma_1_2": "1",
      "ch_dp_id": ["5", "6", ""], "ch_dp_name": ["DP1A", "DP2B", ""], "ch_slct_timeout": ["0", "0", ""],
      "i_ch_dp_id": [], "i_ch_dp_name": [], "i_ch_slct_timeout": [],
      "_mismatches": [
        { "field": "ch_dp_name", "index": 1, "kind": "UNEXPECTED", "expected": null,   "actual": "DP2B", "result_id": "r1" },
        { "field": "ch_dp_name", "index": 2, "kind": "MISSING",    "expected": "DP2A", "actual": null,   "result_id": "r2" }
      ]
    },
    {
      "ts_name": "2AXT1", "e_dp_id": "1", "e_dp_name": "DP2A", "fma_1_2": "1",
      "ch_dp_id": ["1", "2"], "ch_dp_name": ["DP2A", "DP3A"], "ch_slct_timeout": ["0", "0"],
      "i_ch_dp_id": [], "i_ch_dp_name": [], "i_ch_slct_timeout": []
    }
  ],

  "chc_details": [
    { "dp_id": "1", "dp_name": "DP2A",
      "ts_name_1": "1AXT1", "timeout_1": "620", "dp_id_1": "5", "dp_name_1": "DP1A", "fma_dtl_1": "1",
      "ts_name_2": "2AXT1", "timeout_2": "620", "dp_id_2": "1", "dp_name_2": "DP2A", "fma_dtl_2": "1",
      "interval": "3", "supervis_count": "2", "system_count": "2", "partial_count": "1" }
  ],

  "supervisor_details": [
    {
      "sup_name": "SUP1-AXT1", "dp_id": "5", "dp_name": "DP1A",
      "sup_by_ts": ["2AXT1"], "sup_by_ts_dp_id": ["1"], "sup_by_ts_dp_name": ["DP2A"], "sup_by_ts_fma": ["1"],
      "time_out": ["620"], "logic_type": ["AND"],
      "reset_type": "direct", "reset_delay": "3", "auto_reset_type": "1", "reset_timer": "0",
      "_mismatches": [
        { "field": "logic_type", "index": 0, "kind": "VALUE", "expected": "OR", "actual": "AND", "result_id": "r20" }
      ]
    }
  ],

  "ioexb_behaviour_details": [
    {
      "dp_id": "1", "dp_name": "DP2A",
      "behav_input1": "1", "type_in1": "0", "behav_input2": "4", "type_in2": "0",
      "behav_input3": "7", "type_in3": "0", "behav_ioexb": "1", "type_ioexb": "0",
      "is_coop_reset": false, "coop_reset_type": "0", "coop_control_type": "0", "reset_timeout": "0",
      "_mismatches": [
        { "field": "behav_input3", "index": null, "kind": "VALUE", "expected": "6", "actual": "7", "result_id": "r10" }
      ]
    },
    {
      "dp_id": "2", "dp_name": "DP3A",
      "behav_input1": "1", "type_in1": "0", "behav_input2": "4", "type_in2": "0",
      "behav_input3": "6", "type_in3": "0", "behav_ioexb": "1", "type_ioexb": "0",
      "is_coop_reset": false, "coop_reset_type": "0", "coop_control_type": "0", "reset_timeout": "0"
    }
  ],

  "ioexb_aco_details": [
    { "dp_id": "1", "dp_name": "DP2A", "slot": "0", "aco_fma1": "2AXT1", "clr_occ": "0",
      "type_aux1": "0", "type_aux2": "0", "aux1_out": "1", "aux1_no_nc": "0", "aux2_out": "1", "aux2_no_nc": "0",
      "fma_1_2": "1", "time_out": "620" }
  ],

  "data_transmission_details": [],

  "ethernet_details": [
    {
      "com": "COM100", "id": "100",
      "ip_nw_1": "10.1.106.53", "subnet_mask_1": "255.255.255.0",
      "ip_nw_2": "10.2.106.53", "subnet_mask_2": "255.255.255.0",
      "dest_ip_nw_1": ["10.1.106.54"], "dest_ip_nw_2": ["10.2.106.54"],
      "fwrd_acd_to_dp_ids": ["5", "2", ""], "fwrd_acd_to_dp_dtls": ["DP1A", "DP3A", ""],
      "interval": "1",
      "_mismatches": [
        { "field": "fwrd_acd_to_dp_dtls", "index": 1, "kind": "UNEXPECTED", "expected": null,   "actual": "DP3A", "result_id": "r30" },
        { "field": "fwrd_acd_to_dp_dtls", "index": 2, "kind": "MISSING",    "expected": "DP2A", "actual": null,   "result_id": "r31" }
      ]
    },
    {
      "com": "COM200", "id": "200",
      "ip_nw_1": "10.1.106.54", "subnet_mask_1": "255.255.255.0",
      "ip_nw_2": "10.2.106.54", "subnet_mask_2": "255.255.255.0",
      "dest_ip_nw_1": ["10.1.106.53"], "dest_ip_nw_2": ["10.2.106.53"],
      "fwrd_acd_to_dp_ids": ["6"], "fwrd_acd_to_dp_dtls": ["DP2B"],
      "interval": "1"
    }
  ]
}
```

---

## 9. Output-screen behaviour → contract

| Output-screen behaviour | Driven by |
|---|---|
| Detect mismatches | the `_mismatches` block on each detail row |
| Highlight mismatched cells (red) | every cell referenced by a `_mismatches` entry |
| Tooltip: Expected / Actual | `_mismatches.expected` / `_mismatches.actual` (display form) |
| Click failure → its validation-result entry | `_mismatches.result_id` → `validation_results[].id` |
| Highlight the associated ADC file | that entry’s `fileName` |
