# VTF-335 (BE-05) — Expectations Preprocessor: implementation scope

> **Status:** scoping locked 2026-06-23. Detailed semantics are authoritative in [`v2-expectations-contract.md`](v2-expectations-contract.md); this doc is the *implementation plan* only.
> **Branch:** `VTF-335`, off the `VTF-350` tip (`bb05f29`) in the single `FCVT-VTF334` worktree. One branch, phased commits (M1→M5). Merges after VTF-350.

## 1. What this story is
Replace `StubExpectationsPreprocessor` (returns empty) with the real `ExpectationsPreprocessor` that turns the **FCT + PDQ (+ optional tpf)** baseline under `ValidationInputV2` into `Expectations`. **Emission-only:** the engine that *consumes* expectations against parsed ADC files is **BE-06 (VTF-336)**. VTF-335 is therefore verified by asserting the **emitted** expectations against the committed FCT/PDQ fixtures — not by an end-to-end validate.

## 2. Scope boundary
**In:** carrier reshape, widen seam, input gate (§3) + error codes, scalar bucket (§4), registry edits (§4/§7.5), instanced bucket (§5).
**Out:** engine consumption of expectations → BE-06. RangeCheck numeric + both-absent guard → **done in VTF-350**.
**Parked:** DT blocks (`CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT`) — awaiting AE input.

## 3. Current seam (what changes)
- `Expectation(String fileName, String block, int instance, String entryKey, String expectedValue)` — old flat shape; reshape required.
- `Expectations(List<Expectation>)` — must carry **two buckets**.
- `ExpectationsPreprocessor.preprocess(ComAebMap, PdqUploadResponse)` — widen to `preprocess(ValidationInputV2)` (needs `tpfSections`).
- `ConfigValidationV2Service.validate(...)` calls the seam; `StubExpectationsPreprocessor` to be replaced.

## 4. Milestones (one reviewable commit each; M5 splits into 6 sub-commits)

| # | Deliverable | Key files | Verification |
|---|---|---|---|
| **M1** ✅ | **Carrier reshape + widen seam** (as-built — §6.2). `Expectations` = `scalarExpectations: Map<String,Map<String,Object>>` + `instancedExpectations: List<InstancedExpectation>`; new `InstancedExpectation` record + `MatchMode` enum; old flat `Expectation` removed; seam widened to `preprocess(ValidationInputV2)`. Stub + `ConfigValidationV2Service` updated. | `Expectations`, `InstancedExpectation`, `MatchMode`, `ExpectationsPreprocessor`, `StubExpectationsPreprocessor`, `ConfigValidationV2Service` | full suite green; no test referenced the carrier symbols |
| **M2** ✅ | **Input gate §3.1/§3.2 (as-built).** `BaselineGate` runs inside the new `DefaultExpectationsPreprocessor` (which replaces the stub): track reconciliation (Control Table names ↔ FCT `evaluatedFmas.fmaName`, trim + case-sensitive, multiset consume → NOT-FOUND + EXTRA accumulated) + RSR_TYPE dual-source cross-check (mismatch / both-absent → fail; one present → use it). 3 new `ConfigValidationException` subclasses — `ControlTableMissingException`, `TrackReconciliationFailedException` (keeps `notFound`/`extra` lists + a **verbose message naming the tracks**), `BaselineInconsistentException` — each with an **explicit** `@ExceptionHandler`→400 (a base `ConfigValidationException` handler would have mis-mapped the unhandled-500 `ReportGenerationException`). `PHASE2_INPUTS_INCOMPLETE` unchanged. **§3.3 build-time checks (CFG_CONTROL >2-tracks, CFG_FWRD_ACD dest-IP, missing-dpTable) deferred to M5** (raised when those blocks are assembled). | `BaselineGate`, `DefaultExpectationsPreprocessor`, 3 exceptions, `GlobalExceptionHandler`; stub removed | `BaselineGateTest` (8 paths) + happy-path fixtures reconcile live; full suite green |
| **M3** ✅ | **Scalar bucket §4 (as-built).** New `ScalarExpectationsBuilder` (called by `DefaultExpectationsPreprocessor`) emits `scalarExpectations` = `cqIrParameters` copied verbatim — which **already** carries `CFG_PROJECT_AEB/COM` (appended by `PdqParsingService`) and the cqIR scalars (`CFG_RSR_TYPE`, `CFG_SWITCH`, `CFG_IP_SWITCH_TIME`, `CFG_SUPERVIS_FMA*` RESET_TYPE/DELAY) — with the `IDENTIFICATION`→`ID.ID` rewrite + a tpf-block merge (PDQ wins on overlap; tpf supplies `CFG_RSR_TYPE`/`CFG_TROLLEY_SUPP`/`CFG_PARAM_TROLLEY_SUPP`/`CFG_TYPE_PRTCT` when absent). | `ScalarExpectationsBuilder`, `DefaultExpectationsPreprocessor` | `ScalarExpectationsBuilderTest` (4 paths); full suite green |
| **M4** ⏸️ | **DEFERRED past BE-06 too — to a follow-up (decided 2026-06-24).** Registry edits (`CFG_SWITCH`/`CFG_IP_SWITCH_TIME`, `CFG_PROJECT_COM` on COMDETAILS, the dual-FMA rules) need confirmed firmware `DefaultValue`s, and the real ADCs **contradict** the old design note (`CFG_SWITCH` 26/0/180, `RESET_TYPE=0`/`RESET_DELAY=3` vs note's `1`; `CFG_IP_SWITCH_TIME` in no sample). `CFG_AXCNT.BEHAV_INPUT3` is **kept, not removed** (dormant in v2; the rule short-circuits on absent payload — see `v2-expectations-contract.md` §7.5). The shared `ValidationConfiguration.json` stays untouched; the BE-06 v2 engine picks up the rules whenever they land (no code change). Follow-up task spawned with the evidence. | (follow-up) | (follow-up) |
| **M5** ✅ | **Instanced bucket (§5)** — six derivations, one sub-commit each: **#1 ✅ counting-head `CFG_ZP_FMA1/2`** (DIR_INV, SLCT_TIMEOUT — `CountingHeadExpectationsBuilder`; **every** EvaluatedFma incl. combination tracks, no MAIN filter); **#2 ✅ supervisor `CFG_SUPERVIS_FMA1/2`** (`SupervisorExpectationsBuilder`; one block per fadcAutoReset operand, linkedID=(ID,SECTION), LOGIC_TYPE/SLCT_TIMEOUT; op==null ⇒ no block); **#3 ✅ ACO `CFG_SECTION_OUT`** (`AcoExpectationsBuilder`; POSITIONAL, 2/card, filler dup, ID=aco_fmaId+SECTION+SLCT_TIMEOUT per slot); **#4 ✅ CHC `CFG_CONTROL`** (`ControlExpectationsBuilder`; signed sensor-set subset/cancellation main-detection [trackType not trusted], middle/boundary, 2/1/0 + E-CHC, derived BEHAV_INPUT3 6/7, >2→400); **#5 ✅ `CFG_IP_SWITCH`** (`IpSwitchExpectationsBuilder`; per-COM SINGLE — redundant→mandatory "1", non-redundant→optional "0"/default "0"); **#6 ✅ `CFG_FWRD_ACD`** (`ForwardingExpectationsBuilder`; Check-B forwarding — per cross-COM head ref [SLCT_TIMEOUT=1], emit on the head's home COM a BY_IDENTITY forward to the consuming COM, linkedID=`{CAN_TX_ID,DEST_COM}`, deduped per (DP,destCOM); socket→COM resolution + set-equality are BE-06). | preprocessor instanced assembly | per-family unit test vs fixture |

M1→M2→M3 form the spine; **M4 is deferred to BE-06** (2026-06-24); **M5 (instanced bucket) is complete** (2026-06-24, all 6 sub-commits) — the BE-05 emission side is done. DT (`CFG_DATA_*`) stays parked (AE).

## 5. Locked decisions (from the scoping pass)
- **Story size:** one `VTF-335` branch, phased commits M1–M5 (single PR).
- **Registry edits:** **deferred to BE-06** (VTF-336) — *revised 2026-06-24* (was "in VTF-335 M4"). Rule-type / DefaultValue / file-scope are domain calls best made with the consuming engine; the shared registry stays untouched in VTF-335.
- **RangeCheck:** out — shipped in VTF-350.
- **`UIInputRequired` flip:** NOT done (see `v2-expectations-contract.md` §4 fix 2) — the `ID` rule stays `No`.

## 6. Resolved design decisions (2026-06-23)

**#1 — list-membership carrier → 3-mode `matchMode` on flat rows (no separate bucket); grounded in real ADCs (C0351 / C0358 / C0391).** `instancedExpectations` stays one flat list; each `Expectation` carries a `matchMode` + a selector (an identity-map `linkedID`, or a position ordinal) the BE-06 engine reads to select the ADC occurrence(s):

| `matchMode` | Blocks | Selection | Comparison |
|---|---|---|---|
| `SINGLE` | `CFG_AXCNT.BEHAV_INPUT3`, `CFG_IP_SWITCH` | the one file picked by `fileID` | value equality |
| `BY_IDENTITY` | `CFG_ZP_FMA*`, `CFG_SUPERVIS_FMA*`, `CFG_CONTROL`, `CFG_FWRD_ACD` | occurrence(s) whose identity entries == `linkedID` (a map, e.g. `{ID:351,SECTION:0}`, `{CAN_TX_ID:351,INT_ID_DEST:1}`) | **strict set-equality** — missing → FAIL, extra → FLAG; order-independent; per-member value check |
| `POSITIONAL` | `CFG_SECTION_OUT` (ACO) | i-th occurrence | **ordered** — at slot i the ADC's `(ID,SECTION)` must equal the baseline's slot-i pair (+`SLCT_TIMEOUT`); count/order/identity mismatch → FAIL |

**`POSITIONAL` is mandatory for ACO and only ACO:** pairs of `CFG_SECTION_OUT` blocks map *by position* to physical IO-EXB cards at runtime, so a correct-but-re-sequenced config would pass identity-matching yet drive the wrong cards. Hence `(ID,SECTION)` is a *checked value at the slot*, not a lookup key. No other block's order is physically meaningful (confirmed 2026-06-23).

**`BY_IDENTITY` is strict set-equality** (not subset): an ADC occurrence the baseline never listed — a stray / duplicated / leftover head, supervisor, or forwarding entry — is a defect and is flagged. The stock `MultipleBlockMultipleInputMatchRule` only does `actual ⊆ expected`, so the set-equality compare is a **new BE-06 rule**; BE-05 just emits every expected member as a row under the shared `(fileID, block)`.

**#3 — track-reconciliation error → verbose `message` (`ApiErrorResponse` stays flat).** `PHASE2_TRACK_RECONCILIATION_FAILED` must **name the offending tracks** in the message (which are NOT-FOUND in the FCT, which are EXTRA), accumulating all within the gate per §3.1. **Direction — no fail-fast:** a *later* story will add an **error array** to `ApiErrorResponse` listing every error found during preprocessing; until then VTF-335 keeps the flat shape (one verbose message per failing gate) and cross-gate accumulation lands with that later change.

**#2 — scalar/instanced split → enumerated in §6.1 (resolved).** Completeness (no unlisted entries in the instanced blocks) to be confirmed against real ADC block dumps during M5.

**DT** (`CFG_DATA_*`) — still **PARKED**, AE input.

### 6.1 Per-(block, entry) scalar vs instanced

**SCALAR** (expected from cqIR / tpf / project; every-occurrence `allMatch`):

| Block | Entries |
|---|---|
| `ID` | `ID` (RangeCheck ← IDENTIFICATION) |
| `CFG_BEHAV_TGGL` | `BEHAV_RESET`, `BEHAV_SIMUL` |
| `CFG_SECTION` | `COMM_FAIL`, `BEHAV_GE`, `CLR_TRACK`, `RESET_IN`, `RESET_OUT` |
| `CFG_AXCNT` | `BEHAV_INPUT1`, `BEHAV_INPUT2`, `TYPE_IN1/2/3`, `BEHAV_IOEXB` |
| `CFG_SECTION_OUT` | `CLR_OCC`, `TYPE_AUX1/2`, `AUX1_OUT`, `AUX2_OUT`, `AUX1_NO_NC`, `AUX2_NO_NC` (aux) |
| `CFG_OCC` | `OCC_DELAY`, `OCC_EXT` |
| `CFG_RESET` | `RESET_LD_TIME`, `RESET_OP_TIME` |
| `CFG_ZP` | `INTERVAL`, `SUPERVIS_COUNT`, `SYSTEM_COUNT`, `PARTIAL_COUNT`, `SUPERVIS_COUNT_LMT` |
| `CFG_TIMEOUT` | `TIMEOUT_VALUE` (array) |
| `CFG_PROJECT_AEB` / `CFG_PROJECT_COM` | `BLOCK_EXISTS` + `PROJECT_NUMBER` |
| `CFG_SUPERVIS_FMA1/2` | `RESET_TYPE`, `RESET_DELAY` (scalar half) |
| `CFG_SWITCH` | `SWITCH_GE`, `SWITCH_GSF`, `PRERESET_ACT_TIME` |
| `CFG_IP_SWITCH_TIME` | `IP_SWITCH_TIME` |
| `CFG_RSR_TYPE` | `RSR_TYPE` (also the §3.2 gate cross-check) |
| `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_TYPE_PRTCT` | tpf fields |

**INSTANCED** (per-entity from FCT / Control Table):

| Block | Entries | Identity / selector | `matchMode` |
|---|---|---|---|
| `CFG_ZP_FMA1/2` | `DIR_INV`, `SLCT_TIMEOUT` | `{ID}` = head DP (FMA = block-name suffix) | BY_IDENTITY |
| `CFG_SUPERVIS_FMA1/2` | `LOGIC_TYPE`, `SLCT_TIMEOUT` | `{ID, SECTION}` | BY_IDENTITY |
| `CFG_SECTION_OUT` | `ID` (=aco_fmaId), `SECTION`, `SLCT_TIMEOUT` | position ordinal; `(ID,SECTION)` checked per slot | POSITIONAL |
| `CFG_AXCNT` | `BEHAV_INPUT3` (derived 6/7) | `fileID` = DP (single) | SINGLE *(supersedes registry rule)* |
| `CFG_IP_SWITCH` | `IP_SWITCH` | `fileID` = COM (single) | SINGLE |
| `CFG_CONTROL` | `SLCT_TIMEOUT` | `{ID, SECTION}` ×2 | BY_IDENTITY |
| `CFG_FWRD_ACD` | `DEST_COM` (derived) | emitted `{CAN_TX_ID, DEST_COM}`; ADC actual `{CAN_TX_ID, INT_ID_DEST}` resolved socket→COM by BE-06 | BY_IDENTITY |

**Structural (no expectation):** `ID.ID` DuplicateCheck — uniqueness, no baseline value (currently inert; confirm separately). **Parked:** `CFG_DATA_SAFETY_LEVEL` / `CFG_DATA_OUT` (DT).

### 6.2 Carrier shape (M1, as-built)

```
Expectations (record)
 ├─ scalarExpectations    : Map<String, Map<String,Object>>   // block → entry → value (Phase-1 engine)
 └─ instancedExpectations : List<InstancedExpectation>

InstancedExpectation (record)            // factories: single() / byIdentity() / positional()
 ├─ fileId        : int                  // numeric ADC [IDENTIFICATION] ID (DP/COM id) — the join key
 ├─ block         : String
 ├─ matchMode     : MatchMode            // SINGLE | BY_IDENTITY | POSITIONAL
 ├─ linkedId      : Map<String,String>   // identity map; BY_IDENTITY only ({ID,SECTION}, {CAN_TX_ID,DEST_COM}); else empty
 ├─ position      : Integer              // POSITIONAL only (ACO slot); else null
 ├─ key           : String               // validated entry (DIR_INV, SLCT_TIMEOUT, ID, SECTION, …)
 ├─ expectedValue : String
 └─ defaultValue  : String               // null = mandatory match; non-null = optional (absent→PASS iff default==expected). Added M5 #5 for CFG_IP_SWITCH.
```

The three variants live as nullable selector fields on one record (not a sealed hierarchy) — simpler, and `MatchMode` already discriminates. Seam: `ExpectationsPreprocessor.preprocess(ValidationInputV2)` (gives the preprocessor `fctData` + `pdqData` + `tpfSections`). For POSITIONAL/ACO the `ID` and `SECTION` are emitted as `key` rows at the slot, so a re-sequenced config fails. Buckets are emission-only in BE-05; BE-06 consumes them.

## 7. Verification approach
Every milestone asserts the **emitted `Expectations`** for known FCT+PDQ fixtures (via `PdqFixtures`/`FctFixtures`). No end-to-end validate until BE-06. Keep the full suite green at each commit.
