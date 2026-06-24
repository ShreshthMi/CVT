# BE-05 / BE-06 ↔ remaining Phase 2 stories — reconciliation

> **Purpose:** establish, against the actual as-built code, how much of the cluster stories (VTF-337…345) BE-05 (emission) + BE-06 (consumption) already realize, what genuinely remains, and which as-built component realizes which story's intent (input for re-attributing git history to the stories).
>
> **Method:** a decomposed multi-agent pass (run `wf_a7e9ec3d-805`). 8 code-grounded **probes** each read one as-built area (the six BE-05 builders, the BE-06 evaluator + forwarding resolver, the scalar/default-rule path, and the verdict/response surface). Then per-story **reconcile → adversarial-verify** reasoned strictly over the probe outputs (no re-reading). Coverage below is the **verify-corrected** verdict (the adversarial pass downgraded several optimistic first-pass claims). **Caveat:** Phases 2–3 reasoned over probe summaries, not the files directly; exact git provenance (commit-level) is a separate follow-up turn.

## 1. Coverage matrix (verified)

| Story | Title | Coverage | Net |
|---|---|---|---|
| **BE-07** VTF-337 | Rule Registry + `ruleType` finalisation + `_expected` wiring | **PARTIAL** | results-row shape done; the two distinguishing deliverables (registry finalisation, `_expected` cell annotation) not done |
| **BE-08** VTF-338 | Cluster 1: CAN Segment | **PARTIAL** | Check B (forwarding) real; Check A named verdicts + ComAebMap ID lookup + physical/virtual classification absent |
| **BE-09** VTF-339 | Cluster 2: IOEXB ACO | **PARTIAL** | ID/SECTION positional done; rack-position, count-vs-ComAebMap, ≤16 bound absent |
| **BE-10** VTF-340 | Cluster 3: Track Section | **PARTIAL** | sub-check 1 done; 2 & 3 blocked on B7 station-layout; DT half absent |
| **BE-11** VTF-341 | Cluster 4a: CHC | **MOSTLY** | count + BEHAV_INPUT3 validated; literal "count←BEHAV_INPUT3" coupling not built |
| **BE-12** VTF-342 | Cluster 4b: External CHC | **MOSTLY** | per-sensor control check done; explicit ≤2-per-ADC bound only emergent |
| **BE-13** VTF-343 | Cluster 5: Supervisor | **PARTIAL** | instanced LOGIC_TYPE/SLCT_TIMEOUT done; RESET fields hit the M4 bug; dual-FMA consistency absent |
| **BE-14** VTF-344 | Cluster 6: Data Transmission | **NOT_DONE** | parked; only a non-validating display extractor |
| **BE-15** VTF-345 | PDF report (stretch) | **NOT_DONE** | not started |

## 2. Cross-cutting findings (these dominate the residual work)

1. **Verdict-vocabulary gap.** `ValidationStatus = {PASS, FAIL, INVALID}` and the sentinels are structural (`CONFIG_BLOCK_OR_PARAM_NOT_FOUND`, `CONFIG_BLOCK_NOT_FOUND`, `EXPECTED_OCCURRENCE_NOT_FOUND`, `UNEXPECTED_OCCURRENCE`, `RANGE_NOT_CONFIGURED`, project/dup sentinels). The cluster specs' **named verdicts — `ORPHANED`, `FILE NOT FOUND`, `INVALID SCOPE` (actual 2–7), `INVALID VALUE` — do not exist.** This blocks BE-08's Check A and is really BE-07's (registry/verdict-finalisation) job. Until it lands, every "validated" cluster value is a generic PASS/FAIL.
2. **M4 default-executor — a LIVE correctness bug, not just deferred scope.** Scalar entries the PDQ carries but `ValidationConfiguration.json` has **no rule** for (`CFG_PROJECT_COM`, `CFG_SWITCH`, `CFG_IP_SWITCH_TIME`, `CFG_SUPERVIS_FMA1/2` `RESET_TYPE`/`RESET_DELAY`) fall to `DefaultRuleExecutor` → a **mandatory** default `InputMatch` (`SkipComFile=true`). On a non-COM file where such an **optional** block is legitimately absent, this emits a **spurious FAIL** (`CONFIG_BLOCK_OR_PARAM_NOT_FOUND`). Confirmed via the scalar-m4gap probe. This taints BE-07 (scalar verdicts) and BE-13 (supervisor RESET). The spawned M4 follow-up should be treated as a **bug fix**, not just a registry addition.
3. **The engine checks "ADC matches the builder-derived expectation," not "the assignment is correct."** Consumption is set-membership + per-entry value-equality over preprocessor-derived expectations; the *semantic* derivation (which head/sensor/track, adjacency, physical/virtual) is decided at **build time in the BE-05 builders**. Consequence for the rejig: the cluster *intent* mostly lives in the BE-05 builders; BE-06 is the generic substrate. None of the sub-checks independently cross-validate the layout semantics the specs ultimately want.
4. **No range/bound facility for instanced checks.** `≤16` (BE-09), `≤2 per ADC` (BE-12), `POSITION ∈ [0,31]` (BE-14), and the `INVALID SCOPE` 2–7 band (BE-08) all need a bound/range mechanism the instanced engine lacks. `RANGE_NOT_CONFIGURED` exists but is not wired for these; cardinality is only *emergent* from set-equality (and for ACO it's inflated by the filler-duplicate, so it isn't even a true count).
5. **No per-ADC ORPHANED / referenced-id resolution.** `BaselineGate` reconciles **PDQ Control-Table tracks ↔ FCT track universe** (baseline internal consistency) and cross-checks RSR_TYPE — it is *not* the Cluster-1 "read each AEB ADC's `[IDENTIFICATION] ID`, look it up in the ComAebMap → ORPHANED / referenced-AEB-id → FILE NOT FOUND" check. (The first reconcile pass mis-credited the HTTP-400 gate as a partial FILE-NOT-FOUND; the adversarial pass corrected it — different referent.)
6. **Duplicate-identity blind spot.** `evaluateByIdentity` marks all occurrences sharing a `linkedId` as matched but validates only the **first**. Latent; bites only on degenerate duplicate `(ID[,SECTION])` ADC occurrences.

## 3. Per-story detail

### BE-07 (PARTIAL) — Registry + `_expected` wiring
- **Done:** `ValidationResult` is the 7-field Phase-1 shape; a zero-mismatch v2 response stays Phase-1 byte-compatible (true, but *vacuously* — only because nothing was added).
- **Not done:** the `<field>_expected` detail-cell annotation on mismatch — **0% present**; detail tables are display-only extractor output, architecturally decoupled from rule execution, so this needs a new **verdict→detail-cell join**. Registry not finalised (M4 deferred; dormant `CFG_AXCNT.BEHAV_INPUT3` kept; optional scalars mis-fail — finding #2).

### BE-08 (PARTIAL) — Cluster 1 CAN Segment
- **Done:** Check B forwarding — genuine list-membership (`ForwardingExpectationsBuilder` + `ForwardingDestinationResolver` socket→COM via NW1 `+32`/NW2 `+48` → present-COM `CFG_MY_IP`, set-equality). SLCT_TIMEOUT/ID *values* are emitted+consumed across all in-scope blocks.
- **Not done:** every Check-A **named verdict** (finding #1); per-ADC `[IDENTIFICATION] ID` → ComAebMap lookup → ORPHANED (finding #5); physical/virtual **segment-membership** classification (the engine uses a same/different-**chain** proxy, not segment membership); `INVALID SCOPE` 2–7 band; `CFG_DATA_OUT` ownership (no builder).

### BE-09 (PARTIAL) — Cluster 2 IOEXB ACO
- **Done:** ID + SECTION ownership on `CFG_SECTION_OUT` via POSITIONAL per-slot validation; re-sequenced config correctly FAILs (slot-order is load-bearing, tested).
- **Not done:** IoExb **rack position** (not emitted/checked; the POSITIONAL `position` is a slot ordinal, not a physical rack value — and the *source* field is unidentified); **count vs ComAebMap** (only incidental set-cardinality, and inflated by the filler-duplicate, so not a true count from the right input); **≤16** block bound (finding #4).

### BE-10 (PARTIAL) — Cluster 3 Track Section
- **Done:** sub-check 1 (CHC counting-head assignment) wired end-to-end (CountingHead + Control builders → evaluator), though "assignment correctness" is builder-derived (finding #3).
- **Partial:** sub-check 3 (supervisor instanced slice built; **layout dimension blocked**; RESET scalars hit finding #2); sub-check 4 ACO (built, but no rack-position/count; **DT half has no builder**).
- **Not done / blocked:** sub-check 2 (sensors-per-section) — **B7 station-layout** input unavailable; this is the dominant residual.

### BE-11 (MOSTLY) — Cluster 4a CHC
- **Done:** adjacency from the CCT Track table; `CFG_CONTROL` count enforced via set-equality cardinality; `BEHAV_INPUT3` via SINGLE equality; `>2 mains → 400`. **Not contaminated** by the M4 bug (instanced path).
- **Gap:** the literal **"derive count FROM BEHAV_INPUT3 (7→1, 6→2)"** causal model isn't how it works — count and BEHAV_INPUT3 are derived **independently** from the same adjacency/eChc topology, and value `"6"` is **overloaded** (middle-DP→2 blocks *and* boundary-eChc=NO→0 blocks). No coupled count↔flag cross-consistency verdict. *Interpretive call:* is the spec's causal phrasing a real requirement or descriptive shorthand for the as-built dual assertion?

### BE-12 (MOSTLY) — Cluster 4b External CHC
- **Done:** per-sensor-point control check on `CFG_CONTROL` (BY_IDENTITY {ID,SECTION} from CFG_ZP adjacency), BEHAV_INPUT3, `>2 mains → 400`.
- **Gap:** explicit **≤2-per-ADC** cardinality bound (only emergent from set-equality, finding #4); no systematic CHC-DP-in-FCT existence pre-check; thin SLCT_TIMEOUT diff-chain test coverage.

### BE-13 (PARTIAL) — Cluster 5 Supervisor
- **Done:** instanced per-`fadcAutoReset`-operand `LOGIC_TYPE` + `SLCT_TIMEOUT` (BY_IDENTITY {ID,SECTION} set-equality) — real composite-key path.
- **Partial:** `RESET_TYPE`/`RESET_DELAY` present in the scalar pipeline but **mis-validated** by the mandatory default rule (finding #2) → spurious FAIL on absent optional supervisor blocks.
- **Not done:** **dual-FMA (FMA1-vs-FMA2) RESET consistency** — not observable in any probe; the story attributes it to VTF-335 but it isn't built. *Confirm its true home (preprocessor vs this story) before scoping.* Also the duplicate-identity blind spot (finding #6).

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

**Implication:** BE-05's six builders + BE-06's evaluator already realize the per-block *value* validation of BE-08/09/11/12/13 and BE-10 sub-checks 1/3/4. The residuals are verdict vocabulary, bounds/ranges, station-layout, DT, dual-FMA consistency, and the `_expected` wiring — i.e. genuinely *new* work, not re-derivation.

## 5. Residual backlog + recommended sequence

1. **BE-07 first** — it unblocks everything: (a) add the named-verdict vocabulary (`ORPHANED`/`FILE_NOT_FOUND`/`INVALID_SCOPE`/`INVALID_VALUE`); (b) finalise the registry **and fix the M4 default-executor bug** (finding #2 — optional scalars must use `OptionalInputMatchOrBlockNotFound`, or change the default factory); (c) the `_expected` detail-cell join (verdict→cell), the hardest piece.
2. **Cluster residuals** on top of the existing builders/evaluator: BE-08 (ComAebMap ID lookup + ORPHANED/FILE-NOT-FOUND + segment classification + INVALID SCOPE band + `CFG_DATA_OUT`); BE-09 (rack position + count + ≤16); BE-12 (explicit ≤2); BE-13 (dual-FMA consistency + RESET optionality).
3. **BE-10 sub-checks 2 & 3** — escalate **B7 station-layout** input strategy (hard blocker).
4. **BE-14 DT** — unpark with AE (semantics) + build the range mechanism.
5. **BE-15 PDF** — stretch, last.

**Bug-fix backlog (independent of stories):** the M4 spurious-FAIL (finding #2) and the duplicate-identity validation gap (finding #6).
