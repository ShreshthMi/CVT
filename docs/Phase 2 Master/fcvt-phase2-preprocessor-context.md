# FCVT Phase 2 — Preprocessor Design Session (Context Capture)

> ⚠️ **SUPERSEDED (2026-06-20).** This is the original 2026-06-09 session proposing the *"expectations mirror the 8 detail tables"* model — that model was **reversed**. The authoritative locked design is [`v2-expectations-contract.md`](v2-expectations-contract.md) (two-bucket flatten: `scalarExpectations` + `instancedExpectations`; no `SummaryLookupService`; summary stays display-only). Retained for history only.

> Captured **2026-06-09**. Working notes from the design conversation on building the validate-time
> `ExpectationsPreprocessor` (**BE-05 / VTF-335**). Complements — does not replace —
> `fcvt-phase2-design.md` §4.1 and `fcvt-phase2-open-questions.md`.
>
> Status: **investigation paused mid-thread** on the ACO table problem (§6). No VTF-335 branch yet;
> current branch `VTF-334`. No code written this session.

---

## 0. Where this sits

- **BE-05 (VTF-335)** = the validate-time `ExpectationsPreprocessor`. It is **emission-only**; the engine
  that *consumes* the expectations (composite-key lookup → PASS/FAIL + `_expected` annotations) is
  **BE-06 (VTF-336)**.
- **BE-04 (VTF-334)** shipped the seam: `ExpectationsPreprocessor.preprocess(ComAebMap fctData,
  PdqUploadResponse pdqData) -> Expectations`, with `StubExpectationsPreprocessor` returning
  `Expectations.empty()`. Records today: `Expectations(List<Expectation> entries)`,
  `Expectation(fileName, block, instance, entryKey, expectedValue)`.
- Naming rule: **no `Phase2` in identifiers** (use `V2`). `PHASE2_INPUTS_INCOMPLETE` error code is a kept
  contract value.

---

## 1. The objective (restated plainly, the user's framing)

> "The objective of the preprocessor is simple: to create the expectations JSON."

- **Inputs:** the UploadFCT response (`ComAebMap`) + the UploadPDQ response (`PdqUploadResponse`).
- **Output:** the **expectations JSON**.
- **ValidatePhase1Response:** shows the shape the expectations must line up against.

Three repo samples are the working source of truth (under
`docs/Phase 2 Master/Phase 2 Endpoint analysis/`):
`UploadFCTResponse.json`, `UploadPDQResponse.json`, `validateResultPhase1.json`
(+ a hand-trimmed `validateResultPhase1 - Copy.json`).

**Stale-contract check (resolved):** the previously-flagged `UploadPDQResponse.json` staleness
(`RSR_TYPE` / `TYPE_PRTCT_CODE` in `cqIrParameters`) is **gone** — verified the sample is current. The
only stale material is the `Contracts/*.wiki` prose, *not* these three samples.

---

## 2. KEY MODEL CORRECTION — what the Expectations JSON actually is

This was the central correction of the session:

- The Expectations JSON **mirrors the 8 detail tables** (`dp_details … ethernet_details`), holding the
  **expected** values derived from FCT + PDQ.
- It is **NOT** the flat `validation_results[]` shape. `validation_results[]` is the **post-validation
  output**, emitted once validation completes — not the preprocessor's product.
- **Flow:** preprocessor builds the expected detail tables → engine **diffs** expected-table vs
  actual-table (actual = parsed from the ADCs) → emits `validation_results[]` rows **and** the
  `<field>_expected` sibling annotations on the actual detail-cell.
- **Consequence:** the flat `Expectation(fileName, block, instance, entryKey, expectedValue)` seam record
  from BE-04 is the wrong shape; the expectations need to be the **detail-table structures** instead.

---

## 3. The three inputs (samples read this session)

### 3.1 `UploadPDQResponse.json` → `PdqUploadResponse`

- `projectCode` (`"123"`), `aebEquipmentVersion` (`"GS06 and above"`).
- **`cqIrParameters`**: `Map<block, Map<configWord, value>>`. Blocks present:
  - `IDENTIFICATION` `{min,max}`
  - `CFG_SECTION` (COMM_FAIL, BEHAV_GE, CLR_TRACK, RESET_IN)
  - `CFG_RESET` (RESET_OP_TIME, RESET_LD_TIME)
  - `CFG_SECTION_OUT` (CLR_OCC, AUX1_OUT, AUX2_OUT, AUX1_NO_NC, AUX2_NO_NC, TYPE_AUX1, TYPE_AUX2)
  - `CFG_AXCNT` (BEHAV_INPUT1/2, BEHAV_IOEXB, TYPE_IN1/2/3)
  - `CFG_OCC` (OCC_EXT, OCC_DELAY)
  - `CFG_ZP` (INTERVAL, SUPERVIS_COUNT, SYSTEM_COUNT, PARTIAL_COUNT, SUPERVIS_COUNT_LMT)
  - `CFG_BEHAV_TGGL` (BEHAV_RESET, BEHAV_SIMUL)
  - `CFG_TIMEOUT` (TIMEOUT_VALUE = list)
  - `CFG_SWITCH` (SWITCH_GE, SWITCH_GSF, PRERESET_ACT_TIME)
  - `CFG_SUPERVIS_FMA1` / `CFG_SUPERVIS_FMA2` (RESET_TYPE, RESET_DELAY)
  - `CFG_IP_SWITCH_TIME` (IP_SWITCH_TIME) — **distinct** from `CFG_IP_SWITCH`
  - `CFG_PROJECT_AEB` / `CFG_PROJECT_COM` (BLOCK_EXISTS, PROJECT_NUMBER)
- **`controlTable`**:
  - `trackSections[]`: serialNo, name, dpIn[], dpOut[], resetType, trackType,
    fadcAutoReset {op, operands[]}, autoResetByTimer
  - `dpTable[]`: serialNo, name, position, eChc
- **`dataTransmission`**:
  - `dataSafetyLevels[]`: dpName, safetyLevelIn, safetyLevelOut, safeOutFdbckQuad (ints)
  - `outputDataTransmission[]`: sourceDpName, nmbrOut, position (ints)

### 3.2 `UploadFCTResponse.json` → `ComAebMap`

- `chains[]`: `{ com{comId, comName}, redundantComPresent, aebs[] }`
- `aebs[]`: `{ dpId, dpName, evaluatedFmas[], acoIoExbs[], dtIoExbCount }`
  - `evaluatedFmas[]`: fmaName, fmaId (`"0"`/`"1"`), dpId
  - `acoIoExbs[]`: label, **outputFma1Name/outputFma1Id/outputFma1DpId**, **outputFma2Name/outputFma2Id/outputFma2DpId** (Fma2 optional)
- Sample content:
  - Chain 1: COM `COM_A` (comId 201), `redundantComPresent=false`, AEBs `DP26`(855), `DP22`(846)
  - Chain 2: COM `COM_B` (comId 202), `redundantComPresent=true`, AEB `DP30`(860)

### 3.3 `validateResultPhase1.json` (the OUTPUT / target shape) → `ValidationSummary`

- **`validation_results[]`** row = 7 fields, **no `instance`**:
  `fileName`, `ruleType`, `blockName`, `entryKey`, `expectedValue`, `actualValue`, `status`.
  - Files are named by **ADC filename** (`C0001_00.ADC`), which the preprocessor can't know (see §5.1).
  - `ProjectBlockCheck` already exists as a Phase-1 ruleType (CFG_PROJECT_AEB / PROJECT_NUMBER).
- **8 detail tables** (the `ValidationSummary` model order):
  1. `dp_details`: dp_can_id, dp_name, time_out[], comm_fail, behav_ge, clr_track, reset_in, reset_out, behav_reset, behav_simul
  2. `track_section_details`: ts_name, e_dp_id, e_dp_name, fma_1_2, ch_dp_id[], ch_dp_name[], ch_slct_timeout[], i_ch_dp_id[], i_ch_dp_name[], i_ch_slct_timeout[]
  3. `chc_details`: dp_id, dp_name, ts_name_1, timeout_1, dp_id_1, dp_name_1, fma_dtl_1, ts_name_2, timeout_2, dp_id_2, dp_name_2, fma_dtl_2, interval, supervis_count, system_count, partial_count
  4. `supervisor_details`: sup_name, dp_id, dp_name, sup_by_ts[], sup_by_ts_dp_id[], sup_by_ts_dp_name[], sup_by_ts_fma[], time_out[], logic_type[], reset_type, reset_delay, auto_reset_type, reset_timer
  5. `ioexb_behaviour_details`: dp_id, dp_name, behav_input1/2/3, type_in1/2/3, behav_ioexb, type_ioexb, is_coop_reset, coop_reset_type, coop_control_type, reset_timeout
  6. `ioexb_aco_details`: dp_id, dp_name, **aco_fma1**, clr_occ, type_aux1, type_aux2, aux1_out, aux1_no_nc, aux2_out, aux2_no_nc, **fma_1_2**, time_out  ← **see §6**
  7. `data_transmission_details`: dp_id, dp_name, safety_level_in, safety_level_out, safe_out_fdbck_quad, source_dp_id, source_dp_name, timeout, nmbr_out, position
  8. `ethernet_details`: com, id, ip_nw_1, subnet_mask_1, ip_nw_2, subnet_mask_2, dest_ip_nw_1, dest_ip_nw_2, **fwrd_acd_to_dp_ids[]**, fwrd_acd_to_dp_dtls[], interval

> ⚠️ The user's first `validateResultPhase1 - Copy.json` had **deleted** `ioexb_aco_details` (and trimmed
> rows). It was restored mid-session. Always cross-check against the full `validateResultPhase1.json`.

---

## 4. Source map — which input feeds which expected table

It's a **join**, not two independent fills:

- **FCT = the skeleton (entities + links):** per chain, the COM (comId/comName) and each AEB's
  dpId/dpName, its `evaluatedFmas`, and its attached IO cards (`acoIoExbs` = ACO, `dtIoExbCount` = DT).
- **PDQ control table = the structural tables:** `trackSections` → `track_section_details`; SUP /
  combination sections + `fadcAutoReset` → `supervisor_details`; `dpTable.eChc` + sections →
  `chc_details` (derived, not a raw sheet).
- **They meet on the FMA/DP link:** a track section names DPs (PDQ), but the FMA number on it
  (`fma_1_2`, `fma_dtl`, `sup_by_ts_fma`) comes from that DP's AEB FMAs (FCT).
- **CQ-IR scalars** (CFG_SECTION, CFG_ZP, CFG_AXCNT, CFG_SUPERVIS_FMA, CFG_BEHAV_TGGL, CFG_TIMEOUT…) fill
  the config columns inside the rows.

Per-table (working hypothesis, user-confirmed at the level above):

| Expected table | Source |
|---|---|
| `dp_details` | FCT AEB identity + PDQ CFG_SECTION / CFG_BEHAV_TGGL / CFG_TIMEOUT |
| `track_section_details` | PDQ `controlTable.trackSections` + FCT FMA link |
| `chc_details` | PDQ `dpTable.eChc` + sections + FCT FMA + CFG_ZP |
| `supervisor_details` | PDQ SUP/combination sections + `fadcAutoReset` + CFG_SUPERVIS_FMA + FCT FMA |
| `ioexb_behaviour_details` | FCT AEBs with IO cards + CFG_AXCNT |
| `ioexb_aco_details` | FCT AEB ACO cards (`acoIoExbs`) + CFG_SECTION_OUT  ← **§6** |
| `data_transmission_details` | FCT DT cards (`dtIoExbCount`) + PDQ `dataTransmission` |
| `ethernet_details` | FCT COMs + forwarding (Check B; `fwrd_acd_to_dp_ids`) |

---

## 5. Identity & parsing facts (verified against Phase 1 code)

### 5.1 `dp_id` / `dp_name` come from the ADC `[ID]` block

In `IOEXBBehaviourExtractorService` and `IOEXBAcoExtractorService`:
```java
String dpId   = extractEntryValue(file,   "ID", "ID");  // the entry's VALUE
String dpName = extractEntryComment(file, "ID", "ID");  // the entry's COMMENT
```
So each ADC has an `[ID]` block with an entry like `ID = 8 ; DP01`: **`dp_id` = value (`"8"`)**,
**`dp_name` = inline comment (`"DP01"`)**. (`ConfigEntry` carries both `value` and `comment`.)

**Join-key implication:** `validation_results` name files by ADC **filename** (`C0001_00.ADC`), which the
preprocessor never sees. So an expectation must carry an **entity identifier** the engine matches to a
file; the engine swaps in the filename when emitting results.
- `dp_name` (ADC `[ID]` comment) ↔ FCT `dpName` — **safe join key**.
- `dp_id` (ADC `[ID]` value, a small 1–4095 CAN id) — **uncertain**: may *not* equal FCT `dpId`
  (e.g. `855`). Not yet confirmed against a matched ADC+FCT pair.

### 5.2 Track identity = (AEB ID, SECTION)

- **`SECTION` = fmaId (0/1)**, and it **recurs across multiple config blocks** inside a single AEB's ADC.
- A **track is identified by (AEB ID, SECTION)** — one AEB hosts up to **two** tracks (sections 0/1).
- Display value `fma_1_2 = SECTION + 1` (so fmaId `0`→`"1"`, fmaId `1`→`"2"`).

### 5.3 `SECTION` is never validated (and never surfaced as identity)

- **Zero** `"entryKey": "SECTION"` rows in `validation_results`.
- `ValidationConfiguration.json` has **no SECTION rule**: CFG_SECTION rules validate
  COMM_FAIL/BEHAV_GE/CLR_TRACK/RESET_IN/RESET_OUT; CFG_SECTION_OUT rules validate
  CLR_OCC/TYPE_AUX1/TYPE_AUX2/AUX1_OUT/AUX2_OUT/AUX1_NO_NC/AUX2_NO_NC. Never SECTION.
- SECTION is read by extractors **only** to compute the display `fma_1_2`. Every detail row is keyed by
  the AEB (`dp_id`/`dp_name`), never by SECTION.

---

## 6. THE ACO PROBLEM — `ioexb_aco_details` only emits `aco_fma1`  ⛔ (decision: needs an update)

This is where the session paused.

### 6.1 What `ioexb_aco_details` is (Phase 1, today)

- `IOEXBAcoExtractorService.extractAcoDetailsFromFile` emits **one `IOEXBAcoDetail` row per
  `CFG_SECTION_OUT` block** (deduped by the block's comment via `processedComments`).
- Row fields: `dp_id`/`dp_name` (host AEB, from the `[ID]` block), **`aco_fma1`** (the CFG_SECTION_OUT
  **comment** = the **track-section name** being output; Excel column = "TS NAME"), `clr_occ`,
  `type_aux1`/`type_aux2`, `aux1_out`/`aux2_out`, `aux1_no_nc`/`aux2_no_nc` (the section's two **aux relay**
  outputs — *not* the two FMAs), **`fma_1_2`** (= SECTION+1), `time_out`.

### 6.2 The FCT side (per-card, up to two FMA outputs)

- `acoIoExbs[]` models an **ACO card**: `outputFma1Name/Id/DpId` (+ optional `outputFma2Name/Id/DpId`).
  One physical card outputs up to **two** FMAs.
- User's intended mapping: **`outputFma1Name = aco_fma1`, `outputFma2Name = aco_fma2`**.

### 6.3 The mismatch (the issue)

- **There is no `aco_fma2`** anywhere — verified across the whole codebase (`IOEXBAcoDetail`, the
  extractor, `ExcelColumnMapper`, `ExcelHeaderProcessor` — only `aco_fma1`/`acoFma1`).
- A card's two outputs become **two independent rows**, both filling `aco_fma1`, told apart only by
  `fma_1_2`:
  - `outputFma1Name` (Id `0`) → row with `fma_1_2 = "1"`
  - `outputFma2Name` (Id `1`) → row with `fma_1_2 = "2"`
- Per host AEB the **unique key is the track name `aco_fma1`** (deduped); `fma_1_2` repeats — e.g. DP01
  has two `fma_1_2 = "1"` rows (`C1T`, `200AT`).

### 6.4 Root cause

The extractor has **no concept of a card** — it flattens every `CFG_SECTION_OUT` block into its own
single-`aco_fma1` row. The FCT *does* carry the card→(fma1, fma2) grouping.

### 6.5 The fork (and the constraint)

This collides with the **"Phase 1 untouched / zero-mismatch v2 == byte-identical Phase 1"** invariant.

- **(A) Additive — no Phase 1 change.** Preprocessor emits *expected* ACO rows in the same flat shape:
  one row per FMA output `(dp_name, aco_fma1 = outputFmaXName, fma_1_2 = fmaId + 1)`. Match is
  list-membership per host AEB, keyed on the **track name `aco_fma1`**. Validation still works.
- **(B) Fix the Phase 1 extractor** to pair a card's outputs into one row (`aco_fma1` + `aco_fma2`).
  Truer to the FCT, but changes the Phase 1 response **and** the Excel report
  (`ExcelHeaderProcessor` / `ExcelColumnMapper` key on `acoFma1`), and breaks byte-identical v2.

**User decision (2026-06-09): "this definitely needs an update"** → leaning **(B)**.

### 6.6 OPEN feasibility question — where the session stopped

> **Does the ADC let us pair the two outputs into a card, or does that pairing exist only in the FCT?**

- If the **ADC** encodes the card grouping (a card/IoExb marker on the `CFG_SECTION_OUT` blocks), the
  Phase 1 extractor can be fixed to emit `aco_fma1` + `aco_fma2`.
- If the pairing exists **only in the FCT**, the Phase 1 extractor (ADC-only) **cannot** produce
  `aco_fma2` — the fix would have to live in the **v2 / preprocessor** layer, cross-referencing the FCT.

Investigation note: there are **no `.ADC`/`.cfg` fixtures** committed in the repo to inspect. The ACO
classification flag is `ParsedConfigFile.isAcoIoexbDetails()` (set during `CfgParserUtil` parsing); the
ACO + behaviour extractors gate on it. The structure of a real `CFG_SECTION_OUT` block (whether it carries
a card/output-channel index) was **not yet confirmed** — this is the next thing to resolve.

---

## 7. The 3 scalar cross-correlation rules (design §4.1, for the simpler half of BE-05)

1. **Project-block** — emit `CFG_PROJECT_AEB.PROJECT_NUMBER` + `CFG_PROJECT_COM.PROJECT_NUMBER` from one
   PDQ `projectCode`-derived value (so AEB/COM stay consistent). `ProjectBlockCheck` ruleType already exists.
2. **Dual-FMA** — emit identical `RESET_TYPE` / `RESET_DELAY` into both `CFG_SUPERVIS_FMA1` and
   `CFG_SUPERVIS_FMA2` from one CQ-IR value.
3. **`CFG_IP_SWITCH` derivation** — for chains with `redundantComPresent == true`, emit
   `CFG_IP_SWITCH.IP_SWITCH = "1"`; otherwise omit. (Distinct from the PDQ key `CFG_IP_SWITCH_TIME`.)

---

## 8. Open questions carried in from `fcvt-phase2-open-questions.md`

- **Q1** Expectations keying (per-entity by ADC id vs scoped) + the exact join key (id vs name) — see §5.1.
- **Q2** Check B forwarding (`CFG_FWRD_ACD`) — in BE-05 scope per the user, but its channel-grouping
  algorithm is unspecced (`(homeCom, consumingCom)` channels, `socket = header − 32`, NW1-only). Surfaces
  in `ethernet_details.fwrd_acd_to_dp_ids`.
- **Q3** Engine lookup mechanism (JSONPath vs named Java lookups) — BE-06, shapes the output.
- **Q4** `instance` indexing (0- vs 1-based) — relevant to multi-instance blocks; relates to SECTION (§5.2).
- **Q5** Where project-block "the check fails" surfaces (engine-time mismatch vs preprocessor-time error).

---

## 9. Where we stopped / next step

- **Paused** at §6.6: confirm whether the ACO **card pairing** is derivable from the **ADC** or only the
  **FCT** — that decides whether the ACO fix is a Phase-1 extractor change (B) or a v2/preprocessor-layer
  concern (A). The user has chosen to **update** the ACO handling; the layer is pending this answer.
- No code written. No VTF-335 branch. Branch `VTF-334` is the base.
