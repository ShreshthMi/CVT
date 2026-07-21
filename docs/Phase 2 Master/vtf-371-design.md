# VTF-371 — post-gate baseline error handling (design)

Status: **DESIGN DRAFT** — decisions §7 pending. Drafted 2026-07-09 off VTF-370 (`0c41f2a`).
Inputs: exhaustive throw-site inventory + FE-contract analysis (multi-agent, audited), Package W alpha evidence
([vtf-370-series-alpha-fix-plan.md](vtf-370-series-alpha-fix-plan.md) §4).

## 1. Problem

After `BaselineGate` admits a request, **23 build-time** throw sites (expectation builders) and **6
evaluate-time** sites (`ForwardingDestinationResolver`) can still raise `BaselineInconsistentException`
→ `GlobalExceptionHandler` → HTTP 400 `PHASE2_BASELINE_INCONSISTENT` — **first throw aborts the whole
report** (alpha: one junction DP nuked a 723-result run) and hides every other inconsistency. The 5
gate sites (control-table missing ×1, duplicate track ×1, track reconciliation ×1, RSR ×2) are
admission control and **stay hard** — this story does not touch them (except an optional friendlier
message for the blank-duplicate-row case).

## 2. Site inventory (audited)

Full machine-readable inventory retained in the workflow output; summary by class:

| Class | Sites | Grain of safe degrade (from inventory) |
|---|---|---|
| `CountingHeadExpectationsBuilder` | 79, 108, 114, `parseId`:191 | per-head (108); DIR_INV-only at 114 (SLCT_TIMEOUT still computable); per-FMA (79); map sites → per-AEB with chain caveat |
| `SupervisorExpectationsBuilder` | 71, 85, 110, `parseId`:153 | per-operand (85 — genuinely gate-uncovered); per-track (71, 110) |
| `ControlExpectationsBuilder` | 121, 129, 144*, 164, `parseId`:323 | per-DP (121, 129); per-block (164); *144 = junction throw, replaced by real derivation in VTF-372 — until then a degrade site |
| `AcoExpectationsBuilder` | `parseId`:113 | **whole-host** only — skipping one slot would shift POSITIONAL positions and corrupt the host's comparison |
| `IpSwitchExpectationsBuilder` | `parseId`:52 | per-COM; zero cascade (cleanest site) |
| `ForwardingExpectationsBuilder` | 102, 124, 140, 144, `parseId`:199 | per-(track, head) at 124; track grain at 140; chain grain at 144 |
| `DataTransmissionExpectationsBuilder` | 48, 70, `parseId`:110 | row grain at 70 (independent rows); **wholesale** at 48 (size mismatch — never prefix-pair) |
| `ForwardingDestinationResolver` (evaluate) | 98, 104, 110, 116, 121, `parseSocket`:165 | per-member: return `(canTxId, destCom \| errorReason)` outcomes instead of throwing; 121 (NW1/NW2 disagree) is a genuine config defect the report *should* show |

Audit corrections folded in: `Control:144`'s 0-owner case unreachable; `parseSocket`:165 throws NFE only
(never NPE); `CountingHead:114` also fires on a present row with a null POSITION cell; the
"gate guarantees this" comments at `CountingHead:79` / `Supervisor:71` / `Forwarding:102` /
`Control:164` are **not provable** — see §3.1.

## 3. Cross-cutting hazards the design must handle

**3.1 Trim-asymmetry hole.** The gate reconciles PDQ↔FCT names after `trim()`, but the builders build
and probe their lookup maps with **raw** strings — a trailing space on either side passes the gate and
then throws post-gate. Fix at the root: normalize (trim) keys and probes in every builder map. This is
a correctness fix independent of the degrade policy and removes the practical trigger for the four
"defensive" sites.

**3.2 Eager map poisoning.** `parseId` runs while building `idByDpName`/`chainByDpId` over **all** FCT
AEBs before any per-track work — one malformed dpId aborts everything. Degrading map construction to
per-entry tolerance is required; caveat: a missing `chainByDpId` entry must **skip** the dependent
SLCT_TIMEOUT derivation (emitting the finding), never default the comparison (`Objects.equals(x, null)`
would silently yield `1`).

**3.3 UNEXPECTED_OCCURRENCE cascade.** BE-06 set-equality FAILs any actual occurrence with no matching
expectation. Every "skip the expectation, emit a finding" degrade therefore produces a *second*,
spurious `UNEXPECTED_OCCURRENCE` FAIL for the same entity — unless each skip registers a
**suppression key** `(fileId, block, identity | position)` that the evaluator consumes (suppressed
actuals are neither matched nor flagged). Suppressions are part of the degrade mechanism, not optional.

## 4. Carrier: how degraded findings reach the FE

From the contract analysis (`FCVT-v2-Validation-Response-Contract.md`, `v2-validation-output-contract.md`):

- The row schema is a fixed 8-field `ValidationResult`; `status` is **closed** to `PASS|FAIL|INVALID`
  (§2:42). A 4th status value **breaks** the documented contract. A new top-level array is an
  undocumented shape change needing FE sign-off.
- The **documented extension point** is the §6 sentinel table: `expectedValue`/`actualValue` may carry
  a *state marker* instead of a literal — BE-08 already extended it (ORPHANED etc.).
- Results-only rows (no detail cell) are an accepted category (contract §4.1; annotator Javadoc).
- Appending rows to the aggregate list before the id loop in `ConfigValidationV2Service` gives them
  navigable `id`s for free.

**Chosen carrier (recommendation): Option A + C hybrid.**
- **A**: one `ValidationResult` per degraded inconsistency — `ruleType="BaselineConsistency"`,
  `status=INVALID`, `actualValue=BASELINE_INCONSISTENT` (new `ValidationConstants` sentinel),
  `expectedValue` = short human-readable statement of what failed to resolve, `blockName`/`entryKey`
  = affected block + identity (instanced `KEY[ID=..]` idiom where applicable). Results-only.
- **C**: when the inconsistency surfaces at evaluate time with a real ADC in hand
  (`ForwardingDestinationResolver`), the row is attributed to that **real file** and carried as an
  `InstancedFinding` so its cell can highlight (`isAnnotatable()` already admits INVALID).
- Builder-time rows have no ADC in hand → `fileName` = a fixed baseline label (§7 D3).
- Contract cost: one new §6 marker + a one-line relaxation of §2's `fileName` wording. No schema,
  status, or top-level change.

## 5. Mechanism

- `ExpectationsPreprocessor.preprocess` returns a **`PreprocessResult`**: the existing `Expectations`
  + `List<BaselineFinding>` + `Set<SuppressionKey>`. (Builders receive a collector instead of
  throwing; each site converts at its inventoried grain from §2.)
- `InstancedExpectationEvaluator` accepts the suppression set; suppressed actual occurrences are
  excluded from set-equality and from UNEXPECTED_OCCURRENCE.
- `ForwardingDestinationResolver.resolveActualForwards` returns per-member outcomes
  (`canTxId`, `destCom` | `errorReason`); the evaluator turns error members into INVALID findings on
  the home COM (Option C) and excludes them from set-equality both ways.
- `ConfigValidationV2Service` appends the finding rows before the id loop; `BaselineInconsistentException`
  remains only for the gate (and startup config errors).
- Findings are also `log.warn`ed with full detail (log remains the deep-diagnosis channel).
- Fatal-anyway cases: if degrade leaves **zero derivable expectations at all** (pathological baseline),
  the run still returns — a report of only INVALID rows is the honest output.

## 6. Implementation milestones

- **M1** — trim-normalization of builder map keys/probes (+ regression tests per §3.1). Independent,
  land first.
- **M2** — carrier plumbing: `PreprocessResult`, `BaselineFinding`→`ValidationResult` mapping, sentinel
  constant, service integration, contract-doc §6/§2 updates.
- **M3** — builder-by-builder conversion at the §2 grains (one commit per builder; fault-injected
  fixture tests per site; junction site `Control:144` converted transitionally, superseded by VTF-372).
- **M4** — resolver per-member outcomes + evaluator suppression consumption + finding/UNEXPECTED dedupe.
- **M5** — verification: suite green; fault-injected Package W variants (e.g. renamed DP in PDQ,
  non-numeric dpId in FCT, partial COM upload) each yield a complete report + targeted INVALID rows
  instead of a 400.

## 7. Decisions (pending)

- **D1 — degrade policy**: unconditional degrade (recommended: report always returns; log keeps
  detail) vs strict/lenient request flag (adds contract surface; deferred option B from the plan).
- **D2 — naming**: `ruleType="BaselineConsistency"`, sentinel `BASELINE_INCONSISTENT`, status
  `INVALID` (no new status value).
- **D3 — builder-time `fileName` label**: fixed `"FCT"` / `"PDQ"` per artifact (recommended — states
  *which* baseline is inconsistent; original upload filenames are not available at validate time) vs
  single `"BASELINE"` label.
- **D4 — NW2-mirror policy** (resolver sites 110/116): if NW1 resolves a COM but the NW2 mirror
  entry/IP is missing or unmatched — accept the NW1 resolution and emit the finding (lenient), or
  treat the member as unresolved (finding + excluded from matching; recommended, stricter).
- **D5 — FE coordination**: the new §6 marker row + `fileName` wording need the same FE ack as the
  BE-08 named verdicts (tracked open item in `v2-validation-output-contract.md:109`).
