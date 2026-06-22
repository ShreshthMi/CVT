# Preprocessor Lock-down — Working Draft

> ⚠️ **SUPERSEDED (2026-06-20) by [`v2-expectations-contract.md`](v2-expectations-contract.md).** Every "Decisions needed" item below is now resolved in that authoritative contract. Retained for history only; the contract wins on any conflict.

---

## 1. What the spec got right (verified against code)

- **Join key confirmed:** the literal match is FCT `EvaluatedFma.fmaName` ↔ PDQ `TrackSection.name`, both `String`. The worked example (`105A-AXT1`) shows them equal. *Caveat:* no design doc actually **asserts** these two name spaces always coincide — it's an upstream authoring convention we should confirm (see decisions).
- **RSR_TYPE plumbing confirmed (item 5):**
  - PDQ side = `pdqData.cqIrParameters["CFG_RSR_TYPE"]["RSR_TYPE"]` — always a normalized scalar `String` (e.g. `"1"`, already strip + left-of-colon).
  - tpf side = `userInput.tpfSections["CFG_RSR_TYPE"]["RSR_TYPE"]` — captured via `@JsonAnySetter`, present only when a `.tpf` was uploaded, stored as `Object` (may deserialize as `Integer` → needs `String.valueOf().trim()` coercion).
  - There is **no** RSR field anywhere in the FCT tree, so "userInput main tree" unambiguously means the tpf block.
  - Matches design §4.1: both present & differ → 400; only one present → use it.
- **Matching philosophy consistent:** iterate-PDQ-tracks / first-found / "can only prove NOT-FOUND, never MISMATCH" sharpens the existing literal-match doctrine (§6.5 "case/whitespace significant"). Good.
- **ACO cross-DP is real:** design §5.5 confirms an ACO's resolved FMA can reference an AEB other than the host — so `outputFmaXDpId` pointing elsewhere is supported by design.
- **Coupled-artifacts assumption holds:** FCT + PDQ are guaranteed present when the preprocessor runs.

---

## 2. The structural catch (the load-bearing finding)

The user's "expand the FCTResponse into a tree" is a **fourth** expectations shape (the docs already hold three conflicting ones: flat composite-key in §4.1 = the VTF-334 record; "mirror the 8 tables" in preprocessor-context §2; the "pivot" in preprocessor-prereqs §2). A **pure FCT tree rooted at the FMA has no home** for a lot of locked data:

- **COM-axis** — Ethernet/IP, `CFG_FWRD_ACD` forwarding (Check B), `CFG_IP_SWITCH` (derived from chain-level `redundantComPresent`). The worked example starts at a single `FctAeb` and drops the `chains[]`/`com` wrapper entirely.
- **DP-axis** — `DpTableRow` (rail position, eChc), `DataSafetyLevel`, `OutputDataTransmission` — all join on `FctAeb.dpName`, not `fmaName`.
- **Project-scope** — the ~16 `cqIrParameters` blocks + 3 of the 4 cross-correlation rules (project-block, dual-FMA, IP_SWITCH) have no per-track home.
- It also **silently biases the tabled A/B/A′ actual-source fork** (a tree obscures the `(file, block, instance, entry)` key that fork B needs).

### Recommended resolution — two layers (this is the key decision)

- **`tree`** = the user's expanded-FCT shape, **non-normative**, as the authoring/merge intermediate. To fix the orphans it gets **three tiers + a root**: `root` (project scalars) → `chain` (COM axis) → `dp` (`FctAeb`, DP axis) → `fma` (`EvaluatedFma`, the track-enriched node).
- **`flat`** = the **already-locked** `Expectation(fileName, block, instance, entryKey, expectedValue)` records — **the engine contract**. The preprocessor flattens `tree` into `flat`.

This keeps the merge ergonomics the user wants, leaves the VTF-334 seam types untouched, handles COM/AEB/DP/project uniformly, and stays **fork-neutral** (flat keys are resolvable by A, B, and A′ alike). If the team prefers, drop `tree` and emit only `flat` — the engine path is identical either way.

---

## 3. Two mechanical BLOCKERS (must change before BE-05 can run the spec)

1. **The seam can't see the tpf data.** `ExpectationsPreprocessor.preprocess(ComAebMap fctData, PdqUploadResponse pdqData)` is never handed `userInput.tpfSections`, so item-1 ("receives FCT, PDQ, **and** TPF") and item-5 (RSR_TYPE cross-check) are *literally impossible* as wired. **Fix:** widen to `preprocess(ValidationInputV2 userInput)` (future-proofs the other 3 tpf blocks); update `StubExpectationsPreprocessor` + `ConfigValidationV2Service`.
2. **The error code is wrong-semantics.** `PHASE2_INPUTS_INCOMPLETE` means *missing artifact* — not *inconsistent baseline*. The new gates (track NOT-FOUND/EXTRA, RSR_TYPE mismatch, missing Control Table) need a distinct code (e.g. `PHASE2_BASELINE_INCONSISTENT` / `PHASE2_TRACK_RECONCILIATION_FAILED`) carrying the offending track lists + the two RSR values. `ApiErrorResponse` is flat today (`{errorCode, message, timestamp}`), so either encode lists in `message` or add a structured `details` field.

---

## 4. Scope question

The user's spec covers **track-merge + RSR_TYPE gate**. The docs put more in BE-05's scope:
- The **other 3 cross-correlation rules** (project-block, dual-FMA, `CFG_IP_SWITCH`) — design B14, all "specified, not implemented."
- **Check B forwarding** (`CFG_FWRD_ACD` virtual-ref identification + channel grouping) — open-questions Q2, called "the larger half of BE-05."
- **DT / CHC / supervisor** cluster expectations (Clusters 4/5/6; Cluster 5 keys still TBD = B9 open).

These are **placed** in the draft contract (given a home in `tree`/`flat`) but not specified. **Decision:** is BE-05 just the track-merge + RSR gate (this spec), or the full preprocessor?

---

## Part A — Gating algorithm (draft)

Runs **after** the existing coupled-artifacts gate (which guarantees `fctData`/`pdqData` non-null). Order:

```
GATE(userInput):                       # assumes widened seam → reads fctData, pdqData, tpfSections
  STEP 1  Control-Table presence guard         -> 400 PHASE2_CONTROL_TABLE_MISSING
                                                  (controlTable null OR trackSections null/empty)
  STEP 2  Build FCT track universe              = flatten EvaluatedFma.fmaName over all chains/aebs
                                                  (ACO outputFmaXName EXCLUDED)
  STEP 3  Forward pass (NOT-FOUND):             for each PDQ TrackSection.name, first-found in the
                                                  universe; consume ONE element (multiset); else NOT-FOUND
  STEP 4  Reverse pass (EXTRA):                 every unconsumed FCT track -> EXTRA
  STEP 5  if NOT-FOUND ∪ EXTRA non-empty        -> 400 PHASE2_TRACK_RECONCILIATION_FAILED
                                                  (accumulate BOTH lists; no short-circuit)
  STEP 6  RSR_TYPE dual-source cross-check      -> 400 PHASE2_BASELINE_INCONSISTENT
  # pass -> build tree/flat
```

**Match rule:** literal, **case-sensitive**, defensive leading/trailing `strip()` on both sides, internal whitespace **not** collapsed (so it surfaces as NOT-FOUND, per spec). Never fuzzy-match. NOT-FOUND message should name the ambiguity: *"track 'X' not found among FCT track sections (check typo, casing, or stray whitespace)."*

**Multiset:** track matches per list-element (not per name) → a duplicated FCT FMA surfaces as EXTRA; a duplicated PDQ track name → reject as malformed baseline.

**RSR_TYPE (step 6):**
```
pdq = blankToNull(String.valueOf(pdqData.cqIrParameters.CFG_RSR_TYPE.RSR_TYPE).trim())
tpf = blankToNull(String.valueOf(tpfSections.CFG_RSR_TYPE.RSR_TYPE).trim())   # null if no .tpf
if pdq != null && tpf != null && !pdq.equals(tpf): -> 400 PHASE2_BASELINE_INCONSISTENT
elif exactly one present: use it
else (both absent): PDQ-side absence => malformed-baseline 400 (PDQ is mandatory)
```
The agreed single value feeds `tree.root.rsrType` for downstream ADC validation of `CFG_RSR_TYPE.RSR_TYPE`.

**Of the 4 §4.1 rules, only `CFG_RSR_TYPE` belongs in the gate** (pure input-vs-input). Project-block + dual-FMA are engine-time **ADC-vs-expected** checks; `CFG_IP_SWITCH` is a merge-time *derivation*, not a gate.

---

## Part B — Expectations JSON contract (draft)

`tree` (non-normative intermediate) → flattens to `flat` (normative engine contract = existing `Expectations { List<Expectation> }` + a sibling `membershipSets[]` for Check-B lists).

### `tree` spine — DP26 / ID 855 realized

```jsonc
{
  "tree": {
    "root": {
      "rsrType": { "value": "1", "source": "PDQ_CQIR", "tpfPresent": false },  // gate result
      "cfgProjectNumber": "12345",                  // PDQ projectCode-derived (project-block rule; PARKED)
      "cqIrScalars": { /* ~16 project-wide blocks: IDENTIFICATION{min,max}, CFG_SECTION,
                          CFG_TIMEOUT{TIMEOUT_VALUE:[...]}, CFG_SUPERVIS_FMA1/2, ... verbatim */ }
    },
    "chains": [{
      "com": { "comId": "...", "comName": "..." },
      "redundantComPresent": true,
      "cfgIpSwitch": { "IP_SWITCH": "1" },          // derived iff redundantComPresent (PARKED on scope)
      "ethernet": { /* COM-file IP — reserved slot, content out of scope per §11 */ },
      "forwarding": [ /* CFG_FWRD_ACD channel sets — Check B (PARKED) */ ],
      "dps": [{
        "dpId": "855", "dpName": "DP26",            // dpName = JOIN KEY for DP-scoped PDQ
        "dtIoExbCount": 2,                          // count-check candidate (PARKED)
        "dpTable": { "serialNo": "...", "railPosition": "ABOVE THE RAIL", "eChc": false },
        "dataSafetyLevel": { "safetyLevelIn": "...", "safetyLevelOut": "...", "safeOutFdbckQuad": "..." },
        "outputDataTransmission": [{ "sourceDpName": "DPxx", "nmbrOut": "...", "dtPosition": "12" }],
        "fmas": [
          { "fmaName": "105A-AXT1", "fmaId": "0", "dpId": "855",
            "track": { "serialNo": "1", "dpIn": ["DP26"], "dpOut": ["DP27"],
                       "resetType": "RESTRICTED RESET WITH LV", "trackType": "MAIN",
                       "fadcAutoReset": { "op": "OR", "operands": ["105A-AXT1","SUP1-AXT1"] },
                       "autoResetByTimer": false } },
          { "fmaName": "105B-AXT2", "fmaId": "1", "dpId": "855",
            "track": { "serialNo": "2", "dpIn": ["DP27"], "dpOut": ["DP28"],
                       "resetType": "RESTRICTED PREPARATORY RESET WITHOUT LV", "trackType": "MAIN",
                       "fadcAutoReset": { "op": "OR", "operands": ["2AXT2","SUP1-AXT1"] },
                       "autoResetByTimer": false } }
        ],
        "acoCards": [
          { "cardIndex": 0, "label": "ACO",
            "output1": { "fmaName": "105A-AXT1", "fmaId": "0", "dpId": "855" },
            "output2": { "fmaName": "105B-AXT2", "fmaId": "1", "dpId": "855" }, "crossDp": false },
          { "cardIndex": 1, "label": "ACO",
            "output1": { "fmaName": "106-AXT", "fmaId": "0", "dpId": null },
            "output2": null, "crossDp": true }      // foreign-DP output (P1/D4 aco_fmaId; PARKED)
        ]
      }]
    }]
  },
  "flat": {
    "entries": [ { "fileName": "...", "block": "...", "instance": 0, "entryKey": "...", "expectedValue": "..." } ],
    "membershipSets": [ { "fileName": "<homeCom>.cfg", "block": "CFG_FWRD_ACD", "channel": "<id>", "expectedValues": ["..."] } ]
  }
}
```

### Provenance (expectation field → source → ADC actual → flat key)

ADC-actual stated at `(block, entry)` granularity so it stays **fork-neutral**. `<inst>` = ADC block-instance ordinal; **0- vs 1-based unresolved (Q4)**.

| tree field | source | ADC actual (block.entry) | flat key |
|---|---|---|---|
| `fma.track.trackType` | PDQ TrackSection.trackType | CFG_SECTION track-type (entry TBD) | (aebFile, CFG_SECTION, inst, TBD) |
| `fma.track.resetType` | PDQ TrackSection.resetType | CFG_RESET/section reset (TBD) | (aebFile, CFG_RESET, inst, TBD) |
| `fma.track.autoResetByTimer` | PDQ TrackSection.autoResetByTimer | auto-reset-timer (TBD) | (aebFile, TBD, inst, TBD) |
| `fma.track.fadcAutoReset` | PDQ TrackSection.fadcAutoReset | **no scalar ADC home** | metadata / Check-B candidate (PARKED) |
| `fma.track.dpIn/dpOut` | PDQ TrackSection.dpIn/dpOut | **no scalar ADC home** | metadata only; §8 segment logic |
| `dp.dataSafetyLevel.*` | PDQ DataSafetyLevel | CFG_DATA_SAFETY_LEVEL.{SAFETY_LEVEL_IN/OUT, SAFE_OUT_FDBCK_QUAD} | (aebFile, CFG_DATA_SAFETY_LEVEL, i, …) |
| `dp.outputDataTransmission.{nmbrOut,dtPosition}` | PDQ OutputDataTransmission | CFG_DATA_OUT.{NMBR_OUT, POSITION} | (aebFile, CFG_DATA_OUT, i, …) |
| `dp.dpTable.railPosition` | PDQ DpTableRow.position | DP position (TBD) | (aebFile, TBD, inst, TBD) |
| `dp.dpTable.eChc` | PDQ DpTableRow.eChc | E-CHC derivation (Cluster 4) | PARKED |
| `dp.acoCards[].output1.fmaName` | FCT AcoIoExb.outputFma1Name | CFG_SECTION_OUT comment (aco_fma1) | (aebFile, CFG_SECTION_OUT, i, comment) |
| `dp.acoCards[].output2.fmaName` | FCT AcoIoExb.outputFma2Name | **no aco_fma2 field today** | PARKED |
| `dp.acoCards[].output*.dpId` (cross-DP) | FCT outputFmaXDpId | new raw `aco_fmaId` (P1/D4) | PARKED — aco_fmaId semantics OPEN |
| `chain.cfgIpSwitch.IP_SWITCH` | FCT Chain.redundantComPresent | CFG_IP_SWITCH.IP_SWITCH | (comFile, CFG_IP_SWITCH, 0, IP_SWITCH) |
| `chain.forwarding[]` | derived virtual-refs + COM grouping | CFG_FWRD_ACD set | flat.membershipSets (PARKED) |
| `root.rsrType.value` | gate result | CFG_RSR_TYPE.RSR_TYPE | (aebFile, CFG_RSR_TYPE, 0, RSR_TYPE) |
| `root.cfgProjectNumber` | PDQ projectCode-derived | CFG_PROJECT_AEB/COM.PROJECT_NUMBER | (…, CFG_PROJECT_*, 0, PROJECT_NUMBER) PARKED |
| `root.cqIrScalars.<BLK>.<ENTRY>` | PDQ cqIrParameters.<BLK>.<ENTRY> | same in ADC | (aebFile, <BLK>, 0, <ENTRY>) |

### Naming collision fixed
`DpTableRow.position` (ABOVE/BELOW THE RAIL) vs `OutputDataTransmission.position` (0–31) → `railPosition` vs `dtPosition`.

---

## Decisions needed (gating the lock)

**Architecture (highest leverage):**
1. **Shape lock (P4/Q1):** adopt two-layer `tree`-intermediate → flat `Expectation` contract? *(recommended)*
2. **Scope:** is BE-05 = track-merge + RSR gate (this spec), or the full preprocessor (+3 cross-rules, Check-B forwarding, DT/CHC/supervisor)?

**Mechanical blockers (need a yes):**
3. Widen seam to `preprocess(ValidationInputV2)`.
4. New error code(s) for baseline inconsistency (not `PHASE2_INPUTS_INCOMPLETE`).

**Recommended defaults (object if wrong):**
5. Track gate: name-only global; ACO names excluded from both passes; case-sensitive after trim; multiset on FCT side; duplicate PDQ name = 400; accumulate NOT-FOUND + EXTRA together; empty/null Control Table = 400.
6. RSR_TYPE: present-but-blank ≡ absent; `String.valueOf().trim()` coercion; both-absent → PDQ-side absence = 400.

**Still genuinely open (need you / AE):**
7. Join-key equivalence — is `TrackSection.name == EvaluatedFma.fmaName` a guaranteed upstream convention?
8. `aco_fmaId` semantics (P1/D4) — sample `"10"` matches neither `fma_1_2` nor `outputFma1Id`; cross-DP ACO actual unresolvable until defined.
9. Q4 instance indexing (0- vs 1-based) — load-bearing for fork B and multi-instance blocks.
10. DT join (Cluster 6) — PDQ's two name-keyed sub-tables → engine's index-paired/id-keyed ADC rows.
