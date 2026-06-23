# FCVT v2 Validation — Expectations Contract (consolidated, authoritative)

> **Status:** consolidated from the 2026-06-17→20 design sessions; supersedes `preprocessor-lockdown-draft.md`. Locked decisions unless marked **OPEN**/**PARKED**.
> **Verification:** names/behaviour checked against **VTF-334** (`d3516a6`) **where the code reads them**, plus real ADC samples (DP164 AEB `C0351`, COM `C0391`). A few fields are sample/memory-sourced because no VTF-334 code reads them yet — flagged inline (notably `CFG_FWRD_ACD.INT_ID_DEST`). Revised 2026-06-20 after an adversarial review pass (`wf_7e40e6ac-40f`). Per-block detail also in memory (`phase2-*-semantics`).

---

## 1. The model in one picture

The `ExpectationsPreprocessor` runs at `POST /api/config/v2/validate`, built fresh per request from the **FCT** baseline (`ComAebMap`), the **PDQ** baseline (`PdqUploadResponse`), and optional **tpf** blocks — all under `userInput` (`ValidationInputV2`). ADC files (`parsedConfigFiles`) are validation **targets only**; every expectation derives from FCT + PDQ, never from the ADC under test.

Two phases:
1. **GATE (input checking)** — hard `HTTP 400` admission control *before* expectations are built (§3).
2. **FLATTEN → expectations** — two buckets the engine consumes:
   - **`scalarExpectations`** — a `block → entry → value` map; validated across all in-scope files by the **existing Phase-1 engine** (§4).
   - **`instancedExpectations`** — `(fileID, block, linkedID?, key, value)`; validated against the one ADC file picked by `fileID` (§5).

The only structural difference is `fileID`. (An FCT-tree intermediate may be used internally; **the flat buckets are the engine contract.**)

---

## 2. Identity & the id-to-id join

- **`fileID` = `parsedConfigFile.id` = the ADC `ID`-block value = DP id (AEB) / COM id (COM).** Confirmed: `C0351` → `id 351`, `ID`-value `351`, comment `DP164`; `C0391` → `id 391`, value `391`, comment `COM-AdC (HUT7)`, `comDetails=true`.
- **Type caveat (must implement explicitly):** `ParsedConfigFile.id` is an **`int`**; every FCT id (`FctAeb.dpId`, `FctCom.comId`, `EvaluatedFma.dpId/fmaId`, `AcoIoExb.outputFmaXDpId`) is a **numeric-valued `String`**. So the join is **`Integer.parseInt(expectation.fileID) == parsedConfigFile.getId()`**, *not* a raw `==` — the preprocessor parses the FCT String ids to int. The non-numeric strings in the FCT (DP names like `DP166A`, track/operand names like `1AXT2`/`SUP1-AXT1`) are **names** used only for FCT name→id resolution and the track-reconciliation gate — they are never parsed as ids, so `Integer.parseInt` is safe.
- **Instanced identity = `(fileID, config_block, linkedID, config_key)`.** `linkedID` present only for linked/repeated blocks (counting heads, supervisor refs, ACO outputs, CHC tracks); it is often **composite** (`(ID, SECTION)`). Singleton blocks omit it. Single-vs-set handled by `ruleType`, not a separate bucket.
- **`SECTION` is 0-based** everywhere it appears (`FMA_number = SECTION + 1`; the extractors all do `Integer.parseInt(section)+1`). So `SECTION 0` = FMA1, `SECTION 1` = FMA2. Do **not** conflate with the 1-based `FMA{1|2}` block-name suffix.

---

## 3. The gate (hard `HTTP 400`, before building expectations)

Runs after the coupled-artifacts gate (`fctData`/`pdqData` non-null, else `PHASE2_INPUTS_INCOMPLETE` — a **kept** code for *missing artifact* only).

> **Implemented in `BaselineGate` (VTF-335 M2):** §3.1 + §3.2, called first from `DefaultExpectationsPreprocessor.preprocess`. The 3 codes are concrete `ConfigValidationException` subclasses with explicit `@ExceptionHandler`→400. §3.3 build-time checks land with the instanced blocks (M5).

### 3.1 Track reconciliation
PDQ **Control Table** track list = authoritative. Iterate its track names; literal-match each against the FCT track universe (flattened `EvaluatedFma.fmaName` over all chains/AEBs — **ACO output names excluded**), first-found.
- Match = literal, **case-sensitive**, after a defensive trim; internal whitespace **not** collapsed.
- **NOT-FOUND** (track absent from FCT) — can only ever prove not-found, never mismatch (typo/casing/whitespace ≡ absence).
- **EXTRA** (FCT track not consumed) — per-element/multiset consume on the FCT side, so a duplicated FMA surfaces.
- Duplicate name within the PDQ list → 400; `controlTable` null or `trackSections` empty → 400.
- Accumulate all NOT-FOUND + EXTRA, report together.
- **Codes:** NOT-FOUND/EXTRA → `PHASE2_TRACK_RECONCILIATION_FAILED` (carries both lists). Null/empty Control Table → `PHASE2_CONTROL_TABLE_MISSING`. Duplicate PDQ track name → `PHASE2_BASELINE_INCONSISTENT`.

### 3.2 RSR_TYPE dual-source cross-check
- PDQ: `pdqData.cqIrParameters["CFG_RSR_TYPE"]["RSR_TYPE"]` (scalar `String`). tpf: `userInput.tpfSections["CFG_RSR_TYPE"]["RSR_TYPE"]` (`Object`; coerce both `String.valueOf(...).trim()`).
- Both present & differ → `PHASE2_BASELINE_INCONSISTENT`. Exactly one present → use it. Present-but-blank ≡ absent. Both absent → `PHASE2_BASELINE_INCONSISTENT` (PDQ is mandatory).

### 3.3 Build-time gate errors (raised while assembling instanced expectations)
- `CFG_CONTROL` >2-tracks-per-middle-DP (§5.4) → `PHASE2_BASELINE_INCONSISTENT`.
- `CFG_FWRD_ACD` dest-IP-matches-no-present-COM / missing-NW2 (§5.6) → `PHASE2_BASELINE_INCONSISTENT`.
- A counting-head DP in a track's `dpIn`/`dpOut` absent from the `dpTable` (§5.1) → `PHASE2_BASELINE_INCONSISTENT`.

---

## 4. Scalar bucket — Phase-1 engine reuse

`scalarExpectations` is emitted as a **`Map<String, Map<String, Object>>`** (block → entry → value, the exact shape of `ConfigValidationService.validateParsedFiles(...)`'s second arg and `UserValidationInputCriteria.getSections()`), populated from PDQ `cqIrParameters` (+ scalar cross-rule values). The **existing** rule registry does the comparison; the preprocessor only supplies values.

- **Semantics are per-rule:** `InputMatch` / `InputMatchOrBlockNotFound` / `OptionalInputMatch…` (most cqIR words), `MultipleBlockMultipleInputMatch` (`CFG_TIMEOUT.TIMEOUT_VALUE`), `RangeCheck` (`ID` ← IDENTIFICATION).
- **File targeting** is in the rule config (`SkipComFile`, `ValidateOnlyInFilesWith`). The marker mechanism is engine-supported for `TRACKSECTIONDETAILS`, `ACOIOEXBDETAILS`, **and `COMDETAILS`** — but only the first two are *currently used by any rule*; `COMDETAILS` has **no** consumer until the new `CFG_PROJECT_COM` rule (below) is added. So scalar expectations need no `fileScope` of their own.
- **Name alignment:** cqIR `IDENTIFICATION` → rule block `ID` (preprocessor maps it); other cqIR block names match directly.
- **RangeCheck — NOT a no-op today (corrected):** `RangeCheckRule` does prefer a payload `{min,max}` over the config min/max, **but**:
  1. **Fix (locked):** `CqIrValueNormalizer.range()` must emit **numeric** `{min,max}` (Integer) — it currently emits Strings while `RangeCheckRule` casts `((Number) map.get("min")).intValue()`. Emitting numeric aligns with the normalizer's own javadoc and leaves `RangeCheckRule` **unchanged**. **Ripple:** this changes the `cqIrParameters.IDENTIFICATION` value type (String→numeric) **in the PDQ response** → update `UploadPDQResponse.json` + the pdq-upload contract + the Ver14 parser tests (see §8).
  2. **CORRECTED 2026-06-23 (VTF-350) — do NOT flip:** the `ID` rule stays **`UIInputRequired: No`**. The override does **not** depend on the flag — `DefaultPayloadValidator` resolves *all* supplied payload entries unconditionally (lines 29–46) and `ValidationDecisionEngine.decide()` returns `APPLY_RULE` for any configured rule, so a supplied PDQ `{min,max}` overrides the config bounds regardless of `UIInputRequired`. Flipping to `Yes` would instead make an `ID` payload **mandatory** (`DefaultPayloadValidator` lines 81–87 throw `HTTP 400` when absent) and break the shared Phase-1 `/api/config/validate` no-payload path (the config `min:1/max:4095` fallback becomes dead; `DefaultRuleConfigValidator.validateRangeCheck` confirms `No` ⇒ config bounds are the intended "fixed range, no payload" mechanism). If V2 must mandate a PDQ-supplied range, enforce that in the preprocessor — not via the shared registry flag. *(The earlier "No short-circuits so the override never fires" rationale was a code misread.)*
  3. **both-absent (no config + no PDQ) currently NPEs** (`actual >= null`) → add an explicit "missing range" validation error. (Independent fix.)

### Cross-rules that dissolve into the scalar bucket
- **Project-block** → two file-scoped `ProjectBlockCheck`s: `CFG_PROJECT_AEB` (`SkipComFile:true`) + a **new** `CFG_PROJECT_COM` (`ValidateOnlyInFilesWith: COMDETAILS` — the first COMDETAILS consumer), both fed the single PDQ `BLOCK_EXISTS`+`PROJECT_NUMBER`.
- **Dual-FMA** → four `InputMatch` rules: `CFG_SUPERVIS_FMA1`/`FMA2` × {`RESET_TYPE`,`RESET_DELAY`} (`ValidateOnlyInFilesWith: TRACKSECTIONDETAILS`, `SkipComFile:true`, `DefaultValue:1`).
- **New `InputMatch` rules to add:** `CFG_SWITCH` {`SWITCH_GE`,`SWITCH_GSF`,`PRERESET_ACT_TIME`}, `CFG_IP_SWITCH_TIME` {`IP_SWITCH_TIME`}.
- **Modified existing rule:** `CFG_AXCNT.BEHAV_INPUT3` — see §5.4 (its registered `OptionalInputMatchOrBlockNotFound`/default-0 entry is **superseded** by a preprocessor-derived instanced value).

(Of the 4 design §4.1 cross-correlation rules, only `CFG_IP_SWITCH` survives as a genuine instanced derivation — §5.5.)

---

## 5. Instanced bucket — per-block derivations

Cross-cutting: **`SLCT_TIMEOUT` = same/different COM chain.** Chain membership from FCT `ComAebMap.chains[].aebs[]`: linked DP in the **same** chain as the file's DP → `0`; **different** chain → `1` (external/Ethernet). The different-chain set is exactly the **virtual references** that drive `CFG_FWRD_ACD` (§5.6).

### 5.1 `CFG_ZP_FMA1` / `CFG_ZP_FMA2` — counting heads
- One occurrence per head; `linkedID = ID` = the head DP. Head set = the track's `dpIn ∪ dpOut` (PDQ Control Table), names→ids via FCT. **No injection.**
- `block` = `CFG_ZP_FMA1`/`FMA2` per the track's FMA (header comment = track name). `(fileID, block, linkedID)` is the locator (a DP can be a head of both its own FMA1 and FMA2).
- **`DIR_INV` = `(isOut == isBelow) ? 0 : 1`** — direction `IN`(in `dpIn`)=0/`OUT`(in `dpOut`)=1; rail position from PDQ `DpTableRow.position`, which is the **full string `"ABOVE THE RAIL"`/`"BELOW THE RAIL"`** (`ABOVE…`=0/`BELOW…`=1; match the full value, not `"ABOVE"`/`"BELOW"`). A counting-head DP **must** have a `DpTableRow` (guaranteed input); a head present in a track's `dpIn`/`dpOut` but **absent from the `dpTable`** ⇒ no rail position ⇒ `DIR_INV` underivable ⇒ **malformed PDQ → `PHASE2_BASELINE_INCONSISTENT` (400)**.
- **`SLCT_TIMEOUT`** = same/diff-chain.

### 5.2 `CFG_SUPERVIS_FMA1` / `FMA2` — supervisor
- Source = the Control-Table `fadcAutoReset` column: **one block per operand**. Operands are track/supervisor names; no classification needed — each resolves via FCT.
- `block` = `FMA{1|2}` per the host track's own FMA; `fileID` = host evaluating DP.
- Per operand `R`: `linkedID = (ID, SECTION)` where `ID` = FCT evaluating-DP of `R`, `SECTION` = `R`'s **0-based** FMA index (`SECTION = FMA−1`).
- `LOGIC_TYPE` = `fadcAutoReset.op` (OR=0/AND=1), **constant across all of a track's supervisor occurrences** (one op per cell). **`op == null` ⇒ no supervisor:** a null operator means no operands, and **no `CFG_SUPERVIS_FMA` block is emitted** (nothing to validate). A supervisor block requires `op ∈ {OR, AND}` (≥2 operands).
- `SLCT_TIMEOUT` = same/diff-chain. `RESET_TYPE`/`RESET_DELAY` = cqIR scalar (not the track's `resetType`).

### 5.3 `CFG_SECTION_OUT` — ACO (positional)
- ACO validates **positionally**, not by semantic identity. Backplane (BP-EXB 1/2/4/8) → X cards; blocks are **paired per card** (`2i-1, 2i` = card i's track-1/track-2), count **always even** (= 2 × cards). "2× per IO-EXB" = the **two section outputs per card** (not a separate relay axis).
- A **single-track** card (track-1 mandatory, track-2 optional) has slot-2 = a **filler duplicate of track-1**. The preprocessor **generates the expected sequence per FCT `acoIoExbs[]` card** (2 blocks each, filling slot-2 when `outputFma2` is absent) → positions align by construction. Never flatten across card boundaries.
- Per position validate: `ID` (= **`aco_fmaId`** = the block's `ID` value = FCT `outputFmaXDpId`) + `SECTION` (= `outputFmaXId`, 0-based) + `SLCT_TIMEOUT` (same/diff-chain) + the **scalar aux** (`CLR_OCC`/`TYPE_AUX*`/`AUX*_OUT`/`AUX*_NO_NC`, via the cqIR `InputMatch` rules).
- **P1/extractor fix:** the current extractor never reads `ID` and **dedupes by header comment** — which silently drops the **filler** duplicate. P1 adds `aco_fmaId = ID` and must **not** dedup-by-comment (key by position / `(ID, SECTION)`).

### 5.4 `CFG_CONTROL` — CHC (sensor-set adjacency algorithm) + E-CHC
First block needing a real preprocessor **algorithm** over the Control Table:
1. **Main vs combination tracks.** A track is *combination/supervisory* if its signed sensor set (`+`=`dpIn`, `−`=`dpOut`) is derivable from the sum of **any number** of other main tracks' sets (e.g. `SUP1-AXT1 (+DP1A,−DP3A) = 1AXT1 (+DP1A,−DP2A) + (+DP2A,−DP3A)`). **`trackType` is NOT trusted** — `TrackSection.trackType` (PHYSICAL→MAIN/VIRTUAL→COMBINATION) is not relied upon; the signed sensor-set arithmetic is the source of truth for main-vs-combination.
2. Sum all main tracks' signed sensors → DPs that **cancel** = **middle**; non-cancelling = **boundary**.
3. Emission per DP (`fileID` = that DP):
   - **Middle** → **2** blocks (its 2 adjacent main tracks). >2 ⇒ hard 400.
   - **Boundary + `eChc=YES`** → **1** block.
   - **Boundary + `eChc=NO`** → **0** blocks.
   `eChc` (PDQ `DpTableRow.eChc`) is **complementary** to the algorithm (a boundary DP can be `eChc=NO`), not a cross-check.
- Per block: `linkedID = (ID, SECTION)` of the referenced adjacent track — `ID` = its **evaluating DP**, `SECTION` = its 0-based FMA. **The evaluating DP can differ from `fileID`/the host middle DP** (DP164 was a same-DP special case; DP02 is the general cross-DP case — the host hosts the blocks but need not evaluate the tracks). The two tracks can be on different DPs. Validated value = `SLCT_TIMEOUT` (same/diff-chain). **Unordered** (membership).
- **`BEHAV_INPUT3`** (in `CFG_AXCNT`) is a **derived, per-DP (instanced)** expectation: `= 7` iff boundary + `eChc=YES`; `= 6` otherwise (middle's 2 blocks *or* boundary+`eChc=NO`'s 0 blocks). This **supersedes** the existing registry rule (`CFG_AXCNT.BEHAV_INPUT3` = `OptionalInputMatchOrBlockNotFound`, default `0`, scoped `ACOIOEXBDETAILS`) — the registry entry must be removed/overridden, and the derived value is **instanced** (per-DP), not the scalar allMatch. **File scope confirmed:** middle/boundary counting-head DP files **do carry the `ACOIOEXBDETAILS` marker** (these DPs have ACO IO-EXB cards), so the derived value stays `ACOIOEXBDETAILS`-scoped.

### 5.5 `CFG_IP_SWITCH` — per COM (singleton)
- One per FCT chain, `fileID = chain.com.comId`, no `linkedID`.
- `redundantComPresent == true` → `InputMatch`, `IP_SWITCH = "1"` (absent or ≠1 → FAIL).
- `false` → `OptionalInputMatchOrBlockNotFound`, `IP_SWITCH = "0"`, `DefaultValue "0"` (absent → PASS; present must be 0; present `1` → FAIL). One COM file per chain (no redundant-partner file).

### 5.6 `CFG_FWRD_ACD` — Check-B virtual forwarding
- **Expected set** per home COM (`fileID = comId`): the **direct, one-hop** cross-COM counting-head references (every `SLCT_TIMEOUT=1` ref), as `(source DP, consuming COM)` tuples, **deduped** to one per `(DP, COM)`. Grouped by the home COM owning the source DP. No transitive propagation.
- **Entry shape:** `(CAN_TX_ID = source DP, INT_ID_DEST = socket)`. *Note:* only `CAN_TX_ID` is read by current VTF-334 code (`EthernetDetailExtractorService`); **`INT_ID_DEST` is sourced from the `C0391` sample + memory, not yet code-verified** — the P-fix that reads the socket must confirm the entry key name.
- **Socket → destination COM** (one entry per `(DP, COM)` covers **both** networks; resolve via NW1, NW2 is the consistency mirror):
  - NW1: `CFG_INT_ID_DEST_NW1` header = `socket + 32` → `DEST_IP_INT_ID_NW1_B1..B4` → the COM file whose `CFG_MY_IP_NW1` equals it.
  - NW2 (mirror): header = `socket + 48` → `CFG_INT_ID_DEST_NW2.DEST_IP_INT_ID_NW2_B1..B4` → COM file whose `CFG_MY_IP_NW2` equals it. **Both mandatory:** NW1 and NW2 must resolve to the **same** COM; missing `CFG_MY_IP_NW2` or `CFG_INT_ID_DEST_NW2` → error.
  - The expected tuple's consuming COM = `comId` (FCT chain); compare to the IP-resolved COM.
- **Network-consistency principle:** Phase 2 supplies **no external IP baseline** (all design §11's "COM IP out of scope" meant). IPs are used to **resolve** which COM a socket points to and as an **internal consistency** check — every forwarding dest IP must match the self-IP of a COM file **present in the validation set**; no match → error.
- **Comparison** = set-equality: missing expected → fail; extra actual → flag.

### 5.7 DT (`CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT`) — **PARKED (AE)**
Deferred 2026-06-20 — scenarios unclear, needs AE input. Known: two ADC block types; PDQ `DataTransmission` (`dataSafetyLevels` by `dpName`, `outputDataTransmission` by `sourceDpName`, cross-DP); `FctAeb.dtIoExbCount` = card count.

---

## 6. Validation flow (extension of the Phase-1 engine)

- **Scalar:** feed the `Map<block,Map<entry,value>>` straight into `ConfigValidationService.validateParsedFiles(parsedFiles, scalarMap)` — existing universe-build + dispatch + `FileContext.values(block, entry)` across in-scope files. No new code beyond populating the map.
- **Instanced:** per expectation, select the `parsedConfigFile` whose `id == fileID`, run a `FileContext` over that one file, reuse `ValidationRule`/`RuleType`/`ValidationResultFactory`. The new BE-06 piece is **identity-based occurrence selection** within the file (the `linkedID`), since `FileContext.values` flattens occurrences. ACO uses **positional** selection; forwarding & counting-head sets use **list-membership** (`MultipleBlockMultipleInputMatch`-style / a `membershipSets` carrier).

---

## 7. Wire-up changes required

1. **Carrier records — DONE (VTF-335 M1).** `Expectations` now holds `scalarExpectations: Map<String,Map<String,Object>>` + `instancedExpectations: List<InstancedExpectation>`. `InstancedExpectation(int fileId, String block, MatchMode matchMode, Map<String,String> linkedId, Integer position, String key, String expectedValue)` (factories `single`/`byIdentity`/`positional`); `MatchMode = SINGLE | BY_IDENTITY | POSITIONAL` (§8). The old flat `Expectation(fileName, block, instance, entryKey, expectedValue)` is removed. *(Carrier D5 `fileName`→numeric `fileId` join / D6 0-based ordinals applied.)*
2. **Widen the seam — DONE (VTF-335 M1).** `ExpectationsPreprocessor.preprocess(ValidationInputV2)` (gives `fctData` + `pdqData` + `tpfSections`).
3. **Error codes + exception wiring** (all `HTTP 400`; `PHASE2_INPUTS_INCOMPLETE` kept for missing-artifact only):
   - Codes: `PHASE2_CONTROL_TABLE_MISSING`, `PHASE2_TRACK_RECONCILIATION_FAILED` (carries `notFoundTracks[]`+`extraTracks[]`), `PHASE2_BASELINE_INCONSISTENT` (RSR mismatch, RSR both-absent, duplicate PDQ track name, `CFG_CONTROL` >2-tracks, `CFG_FWRD_ACD` dest-IP-no-COM / missing-NW2).
   - **Each new code needs a concrete `ConfigValidationException` subclass AND `@ExceptionHandler` registration** — `GlobalExceptionHandler` maps by concrete class and the base `ConfigValidationException` is abstract with no handler, so an unregistered subclass falls through to the catch-all → `UNEXPECTED_ERROR` / HTTP 500. (Either one handler per subclass, or a single `@ExceptionHandler(ConfigValidationException.class)` base handler mapping to 400 with `ex.getErrorCode()`.)
   - **Decided 2026-06-23:** `ApiErrorResponse` stays **flat** for VTF-335; the track lists are encoded in a **verbose `message`** that names which tracks are NOT-FOUND vs EXTRA. **No fail-fast (direction):** a *later* story adds an error **array** to `ApiErrorResponse` collecting every preprocessing error; until then each failing gate throws one verbose message.
4. **`RangeCheck`** — the IDENTIFICATION-via-PDQ path (String-vs-Number cast + `UIInputRequired:No` gate, §4) **and** the both-absent NPE guard.
5. **Registry edits** — add `CFG_SWITCH` / `CFG_IP_SWITCH_TIME` `InputMatch`; add `CFG_PROJECT_COM` `ProjectBlockCheck` (COMDETAILS); add dual-FMA rules; **supersede** the `CFG_AXCNT.BEHAV_INPUT3` entry (§5.4).

---

## 8. Open items / decisions

**Resolved 2026-06-20:**
- **RangeCheck IDENTIFICATION** → emit **numeric** `{min,max}` (fix `CqIrValueNormalizer.range()`), `RangeCheckRule` unchanged except a both-absent NPE guard. **CORRECTED 2026-06-23:** **do NOT** flip the `ID` rule — it stays `UIInputRequired:No` (the override fires on a supplied payload regardless of the flag; flipping breaks the shared Phase-1 no-payload path — see §4 fix 2). *Consequence tracked:* `cqIrParameters.IDENTIFICATION` min/max became numeric (`UploadPDQResponse.json` + `fcvt-phase2-design.md` §6.3 + the Ver14 parser tests updated). **All of this — the numeric `range()` fix, the `RangeCheckRule` guard, the test/doc updates — was carved out of BE-05 into story `VTF-350` "Parser Alignment to locked PDQ" (merges before VTF-335).**
- **`BEHAV_INPUT3` file scope** → stays `ACOIOEXBDETAILS` (those DP files carry the marker); the derived value is instanced and supersedes the registry entry (§5.4).
- **`CFG_CONTROL` main/combination** → the signed sensor-set algorithm; `TrackSection.trackType` is **not trusted** (§5.4).
- **`LOGIC_TYPE` / `op==null`** → null operator ⇒ no operands ⇒ **no `CFG_SUPERVIS_FMA` block** (§5.2).
- **ACO** → 2 sections per card + filler (the locked per-card generation, §5.3); no separate relay axis.
- **Carrier D5/D6 ratified:** `fileName`→`fileID` (numeric id-to-id join), and all ordinals **0-based** (`SECTION`, `fmaId`, ACO card/slot index).
- **`DIR_INV` missing-dpTable** → every counting-head DP is guaranteed a `dpTable` row; a head absent from the `dpTable` is a malformed PDQ (`PHASE2_BASELINE_INCONSISTENT`) (§5.1).
- **`id-to-id` parse** → all ids are numeric-valued Strings (names like `DP166A`/`1AXT2` are never parsed as ids); `Integer.parseInt` is safe (§2).

**Resolved 2026-06-23 (VTF-335 scoping — detail in `vtf-335-scope.md`):**
- **List-membership carrier** → a **3-mode** `matchMode` on flat instanced rows (`SINGLE` / `BY_IDENTITY` / `POSITIONAL`), **no** separate `membershipSets` bucket; the selector is an identity-map `linkedID` (`{ID,SECTION}`, `{CAN_TX_ID,INT_ID_DEST}`) or a position ordinal. **`BY_IDENTITY` = strict set-equality** (missing→FAIL, **extra→FLAG**; order-independent; per-member value check) for `CFG_ZP_FMA*` / `CFG_SUPERVIS_FMA*` / `CFG_CONTROL` / `CFG_FWRD_ACD` — a **new BE-06 rule** (stock `MultipleBlockMultipleInputMatch` only does `actual ⊆ expected`). **`POSITIONAL` (ACO only)** = ordered slot match where `(ID,SECTION)` is a *checked value at slot i* (the `CFG_SECTION_OUT` pairs map to physical IO-EXB cards, so a complete-but-re-sequenced config must FAIL). `SINGLE` = `CFG_AXCNT.BEHAV_INPUT3` / `CFG_IP_SWITCH`. Grounded in real ADCs (C0351/C0358/C0391); detail in `vtf-335-scope.md` §6/§6.1.
- **Full per-`(block, entry)` scalar-vs-instance enumeration** → done (`vtf-335-scope.md` §6.1); completeness vs unlisted instanced-block entries to confirm against ADC dumps in M5.

**Still open / parked:**
- **DT** (`CFG_DATA_*`) — PARKED, AE input (§5.7).
