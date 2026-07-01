# BE-05 / BE-06 ↔ remaining Phase 2 stories — reconciliation

> **Purpose:** establish, against the actual as-built code, how much of the cluster stories (VTF-337…345) BE-05 (emission) + BE-06 (consumption) already realize, what genuinely remains, and which as-built component realizes which story's intent (input for re-attributing git history to the stories).
>
> **Method:** a decomposed multi-agent pass (run `wf_a7e9ec3d-805`). 8 code-grounded **probes** each read one as-built area (the six BE-05 builders, the BE-06 evaluator + forwarding resolver, the scalar/default-rule path, and the verdict/response surface). Then per-story **reconcile → adversarial-verify** reasoned strictly over the probe outputs (no re-reading). Coverage below is the **verify-corrected** verdict (the adversarial pass downgraded several optimistic first-pass claims). **Caveat:** Phases 2–3 reasoned over probe summaries, not the files directly; exact git provenance (commit-level) is a separate follow-up turn.

## 1. Coverage matrix (verified)

| Story | Title | Coverage | Net |
|---|---|---|---|
| **BE-07** VTF-337 | Rule Registry + `ruleType` finalisation + `_expected` wiring | **MOSTLY** (2026-06-29) | `_expected` cell annotation **DONE** (`MismatchAnnotator` + result `id` + `_mismatches`, `origin/VTF-337`); registry finalisation (M4) still parked |
| **BE-08** VTF-338 | Cluster 1: CAN Segment | **DONE** (2026-07-01) | Check A named verdicts (ORPHANED / FILE NOT FOUND / INVALID SCOPE / INVALID VALUE) + ComAebMap ID lookup + CFG_DATA_OUT ID/SLCT built (`origin/VTF-338`); Check B (forwarding) already real; classification is segment-based (chain == segment) |
| **BE-09** VTF-339 | Cluster 2: IOEXB ACO | **DONE** (2026-07-01) | ID/SECTION + card count + ≤16 covered by positional matching; rack position = block order (user-confirmed, no separate field) → subsumed by positional matching |
| **BE-10** VTF-340 | Cluster 3: Track Section | **MOSTLY** | sub-checks 1/2/3 = ADC-vs-baseline (existing builders); only ACO bounds (sub-4) + DT remain |
| **BE-11** VTF-341 | Cluster 4a: CHC | **MOSTLY** | count + BEHAV_INPUT3 validated; literal "count←BEHAV_INPUT3" coupling not built |
| **BE-12** VTF-342 | Cluster 4b: External CHC | **MOSTLY** | per-sensor control check done; ≤2-per-ADC covered by set-equality + the >2-mains gate |
| **BE-13** VTF-343 | Cluster 5: Supervisor | **MOSTLY** (2026-07-01) | instanced LOGIC_TYPE/SLCT_TIMEOUT done; dual-FMA consistency subsumed by per-FMA baseline validation (user-confirmed); only residual = RESET fields' M4 spurious-FAIL |
| **BE-14** VTF-344 | Cluster 6: Data Transmission | **NOT_DONE** | parked; only a non-validating display extractor |
| **BE-15** VTF-345 | PDF report (stretch) | **NOT_DONE** | not started |

## 2. Cross-cutting findings (these dominate the residual work)

1. **Verdict-vocabulary gap.** `ValidationStatus = {PASS, FAIL, INVALID}` and the sentinels are structural (`CONFIG_BLOCK_OR_PARAM_NOT_FOUND`, `CONFIG_BLOCK_NOT_FOUND`, `EXPECTED_OCCURRENCE_NOT_FOUND`, `UNEXPECTED_OCCURRENCE`, `RANGE_NOT_CONFIGURED`, project/dup sentinels). The cluster specs' **named verdicts — `ORPHANED`, `FILE NOT FOUND`, `INVALID SCOPE` (actual 2–7), `INVALID VALUE` — do not exist.** This blocks BE-08's Check A and is really BE-07's (registry/verdict-finalisation) job. Until it lands, every "validated" cluster value is a generic PASS/FAIL.
2. **M4 default-executor — a LIVE correctness bug, not just deferred scope.** Scalar entries the PDQ carries but `ValidationConfiguration.json` has **no rule** for (`CFG_PROJECT_COM`, `CFG_SWITCH`, `CFG_IP_SWITCH_TIME`, `CFG_SUPERVIS_FMA1/2` `RESET_TYPE`/`RESET_DELAY`) fall to `DefaultRuleExecutor` → a **mandatory** default `InputMatch` (`SkipComFile=true`). On a non-COM file where such an **optional** block is legitimately absent, this emits a **spurious FAIL** (`CONFIG_BLOCK_OR_PARAM_NOT_FOUND`). Confirmed via the scalar-m4gap probe. This taints BE-07 (scalar verdicts) and BE-13 (supervisor RESET). The spawned M4 follow-up should be treated as a **bug fix**, not just a registry addition.
3. **The engine checks "ADC matches the builder-derived expectation," not "the assignment is correct."** Consumption is set-membership + per-entry value-equality over preprocessor-derived expectations; the *semantic* derivation (which head/sensor/track, adjacency, physical/virtual) is decided at **build time in the BE-05 builders**. Consequence for the rejig: the cluster *intent* mostly lives in the BE-05 builders; BE-06 is the generic substrate. None of the sub-checks independently cross-validate the layout semantics the specs ultimately want.
4. **Count bounds are subsumed by set-equality — not a separate gap (revised 2026-06-24).** The `≤16` (BE-09 ACO) and `≤2 per ADC` (BE-12 CHC) limits do **not** need a new range facility: POSITIONAL and BY_IDENTITY both flag *extra* occurrences, so an ADC can never exceed the baseline's count; and an *absolute* cap is only breached if the **baseline itself** exceeds it — baseline-self-validation, **out of scope** (the same principle that dropped B7; CHC additionally has the `>2 mains → 400` build gate, so its baseline can never expect >2). One edge: on BY_IDENTITY blocks a *same-identity* duplicate can slip the count via the duplicate-identity blind spot (finding #6) — consciously deferred (tampering-only). What genuinely remains is **not** a count bound: the `POSITION ∈ [0,31]` **domain** check (BE-14/DT, parked) and the `INVALID SCOPE` 2–7 **band** (BE-08), a verdict-vocabulary item (finding #1).
5. **No per-ADC ORPHANED / referenced-id resolution.** `BaselineGate` reconciles **PDQ Control-Table tracks ↔ FCT track universe** (baseline internal consistency) and cross-checks RSR_TYPE — it is *not* the Cluster-1 "read each AEB ADC's `[IDENTIFICATION] ID`, look it up in the ComAebMap → ORPHANED / referenced-AEB-id → FILE NOT FOUND" check. (The first reconcile pass mis-credited the HTTP-400 gate as a partial FILE-NOT-FOUND; the adversarial pass corrected it — different referent.)
6. **Duplicate-identity blind spot — consciously deferred (2026-06-24).** `evaluateByIdentity` marks all occurrences sharing a `linkedId` as matched but validates only the **first**, so the "extra" sweep does **not** flag a duplicate occurrence carrying an *expected* identity. This can only arise from a **hand-edited ADC**, and FCVT accepts only technically-correct ADC files — so it is a **non-issue for valid inputs** and is intentionally not fixed now. **Recorded so it is not forgotten:** the day FCVT must defend against tampered ADCs, fix `evaluateByIdentity` to flag an identity whose matched-occurrence count exceeds its expected count. (That same fix is what would make strict set-equality fully enforce the `≤16`/`≤2` count bounds on BY_IDENTITY blocks — see finding #4.)

## 3. Per-story detail

### BE-07 (MOSTLY — `_expected` DONE 2026-06-29) — Registry + `_expected` wiring
- **Done:** `ValidationResult` gains a response-scoped opaque `id` (omitted in Phase 1 via `NON_NULL`); each detail row gains an optional `_mismatches[]` (`NON_EMPTY`). A new `MismatchAnnotator` post-pass performs the **verdict→detail-cell join**: the instanced evaluator now also emits an `InstancedFinding` carrying each result's raw cell coordinate (block / identity / position / checked-entry), so the annotator maps a failing result onto its cell without re-parsing result strings. Coverage: counting heads → `track_section` ch/i_ch arrays with union-array MISSING padding; supervisor → `logic_type`/`time_out` by (ID,SECTION) member index; CHC → `chc` `_1`/`_2` slots; ACO positional → `ioexb_aco` row by reconstructed block-order comment (shared extractor untouched — the chosen "annotator-side reconstruction" for the ACO comment-dedup); BEHAV_INPUT3 single → `ioexb_behaviour`; forwarding → `ethernet` arrays re-resolved by index; scalar `CFG_SECTION_OUT` aux → `ioexb_aco` per-row compare. Per-field raw→display mirrors each extractor (DP id→name, `ValueMappingService`, `SLCT_TIMEOUT`→`CFG_TIMEOUT`×10, `SECTION`+1). Suite 233 green (`origin/VTF-337`).
- **Results-only (no faithful cell, by design):** counting-head `DIR_INV` (the ch/i_ch split axis, not a column); ACO `ID` (the `aco_fmaId` is not a displayed column); ACO/CHC `MISSING` where the table has no array slot to append to; scalar cross-rules with no detail column (project / RSR / switch). DT has no validation results yet (BE-14).
- **Not done:** registry finalisation (M4 deferred; dormant `CFG_AXCNT.BEHAV_INPUT3` kept; optional scalars mis-fail — finding #2). Named verdicts are BE-08 (finding #1).

### BE-08 (DONE — 2026-07-01) — Cluster 1 CAN Segment
- **Done:** Check B forwarding (BE-06 — `ForwardingExpectationsBuilder` + `ForwardingDestinationResolver` socket→COM via NW1 `+32`/NW2 `+48` → present-COM `CFG_MY_IP`, set-equality). Check A named verdicts (`CanSegmentValidator`, design §8.1): **ORPHANED** (per-AEB-ADC `[IDENTIFICATION] ID` vs the FCT AEB set — finding #5), **FILE NOT FOUND** (an unexpected reference to an unknown AEB id), **INVALID SCOPE** (SLCT_TIMEOUT actual 2–7) / **INVALID VALUE** (else) — carried as sentinels in `expected`/`actual`, status FAIL/**INVALID** (finding #1 closed). `CFG_DATA_OUT` ID/SLCT_TIMEOUT via a new `DataTransmissionExpectationsBuilder` (PDQ DT sub-tables paired by row → BY_IDENTITY on the receiving AEB, SLCT by segment membership).
- **Classification (finding #3):** the same/different-**chain** proxy *is* segment membership — `ComAebMap` collapses each CAN segment to one chain, so no separate classification was needed.
- **Residual (documented, minor):** FILE NOT FOUND is applied only to BY_IDENTITY blocks whose identity has an `ID` (not ACO's positional `aco_fmaId` nor forwarding's `CAN_TX_ID`); DT MISSING is results-only (the scalar-row DT table has no array slot). Registry finalisation / M4 still parked. See *vtf-338-scope.md*.

### BE-09 (DONE — 2026-07-01) — Cluster 2 IOEXB ACO
- **Done:** ID + SECTION ownership on `CFG_SECTION_OUT` via POSITIONAL per-slot validation; re-sequenced config correctly FAILs (slot-order is load-bearing, tested).
- **Covered (revised 2026-06-24):** **card count** and the **≤16 bound** are enforced by POSITIONAL matching — the expected sequence is the FCT-derived 2×cards, so an ADC with the wrong number of `CFG_SECTION_OUT` blocks fails (missing slot or `UNEXPECTED_OCCURRENCE`); >16 can only arise if the FCT itself specifies >8 cards (baseline-self-validation, out of scope — finding #4).
- **Rack position — RESOLVED (user-confirmed 2026-07-01): the physical slot order == the `CFG_SECTION_OUT` block order, and there is no separate rack-slot field.** So "IoExb in the wrong rack position" is exactly what the POSITIONAL per-slot matching already catches (the i-th block validated against the i-th expected card). No separate check to build → **Cluster 2 complete.**

### BE-10 (MOSTLY) — Cluster 3 Track Section
> **"Station layout" dropped (2026-06-24):** the baseline (PDQ + FCT) is the **sole source of truth**, so all four sub-checks are **ADC-vs-baseline** — no external input. "Correct sensors per track section" = ADC vs Control Table `dpIn`/`dpOut`; "supervisory track config" = ADC vs `fadcAutoReset`. (Validating the baseline against physical reality would need an external source, but that is out of scope.)
- **Done:** sub-check 1 (CHC counting-head assignment) — CountingHead + Control builders → evaluator. Sub-check 2 (sensors-per-section) is the **same baseline data** (Control Table `dpIn`/`dpOut`) the counting-head engine already validates. Sub-check 3 (supervisory) — `SupervisorExpectationsBuilder` over `fadcAutoReset`, consumed by set-equality.
- **Partial:** sub-check 3's scalar RESET fields hit finding #2 (M4); sub-check 4 ACO (built, but no rack-position / no count bound — see BE-09; **DT half has no builder**).
- **Caveat (finding #3):** "assignment correctness" is builder-derived — the evaluator checks the ADC matches the baseline-derived expectation, which is exactly the intended ADC-vs-baseline validation.

### BE-11 (MOSTLY) — Cluster 4a CHC
- **Done:** adjacency from the CCT Track table; `CFG_CONTROL` count enforced via set-equality cardinality; `BEHAV_INPUT3` via SINGLE equality; `>2 mains → 400`. **Not contaminated** by the M4 bug (instanced path).
- **Gap:** the literal **"derive count FROM BEHAV_INPUT3 (7→1, 6→2)"** causal model isn't how it works — count and BEHAV_INPUT3 are derived **independently** from the same adjacency/eChc topology, and value `"6"` is **overloaded** (middle-DP→2 blocks *and* boundary-eChc=NO→0 blocks). No coupled count↔flag cross-consistency verdict. *Interpretive call:* is the spec's causal phrasing a real requirement or descriptive shorthand for the as-built dual assertion?

### BE-12 (MOSTLY) — Cluster 4b External CHC
- **Done:** per-sensor-point control check on `CFG_CONTROL` (BY_IDENTITY {ID,SECTION} from CFG_ZP adjacency), BEHAV_INPUT3, `>2 mains → 400`.
- **≤2-per-ADC bound: covered (revised 2026-06-24)** — set-equality flags extras and the `>2 mains → 400` build gate stops the baseline ever expecting >2, so the limit holds without a range facility (finding #4); the only escape is a same-identity duplicate via the duplicate-identity blind spot (finding #6, tampering-only, consciously deferred).
- **Gap:** no systematic CHC-DP-in-FCT existence pre-check; thin SLCT_TIMEOUT diff-chain test coverage.

### BE-13 (PARTIAL) — Cluster 5 Supervisor
- **Done:** instanced per-`fadcAutoReset`-operand `LOGIC_TYPE` + `SLCT_TIMEOUT` (BY_IDENTITY {ID,SECTION} set-equality) — real composite-key path.
- **Partial:** `RESET_TYPE`/`RESET_DELAY` present in the scalar pipeline but **mis-validated** by the mandatory default rule (finding #2) → spurious FAIL on absent optional supervisor blocks.
- **Dual-FMA (FMA1-vs-FMA2) RESET consistency — SUBSUMED (user-confirmed 2026-07-01), no separate check needed.** FMA1/FMA2 are the two *different* track sections one board evaluates; each FMA's supervisor RESET is validated against its baseline expectation, so any real reset misconfiguration already surfaces as a per-FMA mismatch. A dedicated "force FMA1 == FMA2" cross-check would be redundant (and wrong if the two sections are allowed different resets). **The only genuine residual is the M4 spurious-FAIL** on absent optional RESET blocks (finding #2) — fixing M4 makes the reset validation sound. Duplicate-identity blind spot (finding #6) stays consciously deferred.

### BE-14 (NOT_DONE) — Cluster 6 Data Transmission
- Entirely parked: no `CFG_DATA_OUT` / `CFG_DATA_SAFETY_LEVELS` builder or evaluator, no `SAFE_OUT_FDBCK_QUAD ∈ {0,1}`, no `POSITION ∈ [0,31]`. Only a **display-only** `dataTransmissionDetails` extractor (no `ValidationResult` rows). Needs: a `DataTransmissionExpectationsBuilder` + engine wiring + the range mechanism (finding #4) + DT semantics frozen with AE. PDQ DT parsing already exists (BE-01/02); only the validation half is missing.

### BE-15 (NOT_DONE) — PDF report (stretch, off critical path).

## 4. Code → story provenance map (for the git-history rejig)

The as-built code that realizes cluster *intent* — to re-attribute the BE-05/BE-06 commits to the right stories:

| As-built component | Realizes (story / role) |
|---|---|
| `CountingHeadExpectationsBuilder` + evaluator BY_IDENTITY | BE-08 (ID/SLCT_TIMEOUT on CFG_ZP_FMA), BE-10 sub-check 1 |
| `SupervisorExpectationsBuilder` + evaluator BY_IDENTITY | BE-13 (instanced half), BE-08 (ID/SLCT on CFG_SUPERVIS_FMA), BE-10 sub-check 3 (slice) |
| `AcoExpectationsBuilder` + `evaluatePositional` | BE-09 (ID/SECTION), BE-08 (ID/SLCT on CFG_SECTION_OUT), BE-10 sub-check 4 (ACO) |
| `ControlExpectationsBuilder` + evaluator | BE-11 (count/BEHAV_INPUT3), BE-12 (external CHC), BE-10 sub-check 1 (CHC), BE-08 (CFG_CONTROL ID/SLCT) |
| `ForwardingExpectationsBuilder` + `ForwardingDestinationResolver` + `evaluateForwarding` | BE-08 Check B (the most cleanly story-aligned piece) |
| `IpSwitchExpectationsBuilder` | BE-05 §5.5 cross-rule (COM infra; not a cluster) |
| `InstancedExpectationEvaluator` (whole) | **BE-06** — the composite-key lookup extension itself; the substrate all clusters consume |
| `ScalarExpectationsBuilder` + lenient `PayloadValidator.resolve` + `validateParsedFiles` overload | BE-07 scalar verdict wiring / cross-cutting (carries the finding-#2 bug) |
| `ValidationResult` / `ValidationStatus` / `SummaryService` | BE-07 (results-row shape done; `_expected` NOT done) |

**Implication:** BE-05's six builders + BE-06's evaluator already realize the per-block *value* validation of BE-08/09/11/12/13 and BE-10 sub-checks 1/3/4. The residuals are verdict vocabulary, bounds/ranges, DT, dual-FMA consistency, and the `_expected` wiring — i.e. genuinely *new* work, not re-derivation.

## 5. Residual backlog + recommended sequence

1. **BE-07 first** — it unblocks everything: (a) add the named-verdict vocabulary (`ORPHANED`/`FILE_NOT_FOUND`/`INVALID_SCOPE`/`INVALID_VALUE`); (b) finalise the registry **and fix the M4 default-executor bug** (finding #2 — optional scalars must use `OptionalInputMatchOrBlockNotFound`, or change the default factory); (c) the `_expected` detail-cell join (verdict→cell), the hardest piece.
2. **Cluster residuals** on top of the existing builders/evaluator: BE-08 (ComAebMap ID lookup + ORPHANED/FILE-NOT-FOUND + segment classification + INVALID SCOPE band + `CFG_DATA_OUT`); BE-09 (rack position, only if required); BE-13 (dual-FMA consistency + RESET optionality). *(BE-12 `≤2` and BE-09 count/`≤16` are already covered by set-equality — finding #4.)*
3. **BE-10** — sub-checks 1/2/3 already covered (ADC-vs-baseline); finish sub-check 4's ACO bounds (with BE-09) and the DT half (with BE-14).
4. **BE-14 DT** — unpark with AE (semantics) + build the range mechanism.
5. **BE-15 PDF** — stretch, last.

**Bug-fix backlog (independent of stories):** the M4 spurious-FAIL (finding #2) and the duplicate-identity validation gap (finding #6).

## 6. Decisions (locked 2026-06-24)

- **D1 — M4 spurious-FAIL (finding #2): DEFERRED (conscious).** Not fixed now (not even the quick guard). v2 therefore currently over-reports FAILs for absent *optional* scalar blocks (`CFG_SWITCH`, `CFG_SUPERVIS_FMA*` RESET, `CFG_IP_SWITCH_TIME`, `CFG_PROJECT_COM`). Tracked by the existing M4 follow-up chip; land the proper registry rules (with confirmed firmware defaults) there.
- **D2 — Named verdicts (finding #1): PHASED to BE-08.** Keep `PASS`/`FAIL` + structural sentinels for now. Introduce `ORPHANED` / `FILE NOT FOUND` / `INVALID SCOPE` / `INVALID VALUE` when **Cluster 1 (BE-08)** is built, where they are actually consumed. BE-07 does **not** add them up front.
- **D3 — `_expected` detail-cell annotation (BE-07): DONE (2026-06-29).** The cost analysis chose a **hybrid join**: the instanced evaluator (owned code) attaches a structured cell coordinate per result (`InstancedFinding`), while the scalar bucket is mapped by a small registry — so the annotator never re-parses result strings. The ACO comment-dedup conflict was resolved by **annotator-side reconstruction** (the shared `IOEXBAcoExtractorService` is untouched, keeping Phase 1 byte-identical). Built on `VTF-337` (`MismatchAnnotator`); see *vtf-337-scope.md* for the as-built coverage + the results-only gaps. Full coverage delivered (all 8 tables to the extent each has a faithful display cell).
- **D4 — CHC count ↔ BEHAV_INPUT3 (BE-11): independent checking ACCEPTED.** The spec's "derive count from BEHAV_INPUT3" phrasing is treated as descriptive; the as-built dual baseline-assertion catches the same defects. The literal coupled cross-consistency verdict is **not** required. BE-11 is functionally complete on this point.

**Consequence:** BE-07's two real deliverables are both parked (registry → D1/M4; `_expected` → D3 cost analysis), and named verdicts moved to BE-08 (D2). So the next *active* backend work is **Cluster 1 (BE-08)**, not BE-07.
