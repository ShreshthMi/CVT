# VTF-371 — post-gate baseline error handling (design — LOCKED)

Status: **DESIGN LOCKED** 2026-07-09 (decisions §6). Ready for implementation off VTF-370 (`0c41f2a`).
Inputs: exhaustive audited throw-site inventory + FE-contract analysis, Package W alpha evidence
([vtf-370-series-alpha-fix-plan.md](vtf-370-series-alpha-fix-plan.md) §4).

## 1. Problem

After `BaselineGate` admits a request, **23 build-time** throw sites (expectation builders) and **6
evaluate-time** sites (`ForwardingDestinationResolver`) raise `BaselineInconsistentException` on the
FIRST unresolvable reference → HTTP 400 → the whole report is lost and every *other* inconsistency
stays hidden (alpha: one junction DP killed a 723-result run; fixing problems one failed run at a
time). The 5 gate sites (control-table missing, duplicate track, track reconciliation, RSR ×2) are
admission control and stay hard, unchanged.

## 2. Decided model: accumulate, then reject with the complete list

**User decision (2026-07-09): the run is still rejected when the baseline is inconsistent — no partial
report — but the rejection enumerates EVERY problem found, not just the first.**

- Builders and the resolver stop first-throwing. Each site **records the inconsistency into a
  request-scoped collector and skips at its safe grain, continuing** — so one run discovers all
  problems of its phase.
- End of preprocessing: collector non-empty → one `BaselineInconsistentException` carrying the full
  formatted list → HTTP 400 `PHASE2_BASELINE_INCONSISTENT`. Collector empty → proceed to evaluation.
- Evaluate-time (`ForwardingDestinationResolver`) failures accumulate the same way within evaluation:
  the resolver returns per-member outcomes; unresolved members are collected, and if any exist the
  run rejects with the complete resolver-side list. (Two-phase discovery is accepted: a run whose
  preprocessing is dirty rejects before evaluation; the next clean-preprocessing run enumerates all
  resolver-side problems at once.)
- **Payload (decided): the list rides in the existing `message` field** as a numbered list — the
  `TrackReconciliationFailedException` precedent. `{errorCode, message, timestamp}` unchanged; zero
  FE/contract change. Each item names: the source context (PDQ / FCT / ADC file for resolver items),
  the affected block/track/DP or forwarding member, and the unresolvable reference with its raw value.
- **NW2 policy (decided): stricter option** — if NW1 resolves a destination COM but the NW2 mirror
  entry/IP is missing or unmatched, the member is *unresolved* and recorded as a listed problem (NW2
  is a mandatory mirror; its absence is a real defect).
- Not touched: `BaselineGate` (5 hard sites), the BE-08 named-verdict vocabulary (ADC findings, not
  baseline errors), Phase 1 path.

Rejected alternatives, recorded for history: (a) degrade-to-INVALID-result-rows (report always
returns) — rejected in favor of a clean "baseline must be consistent before validation" stance; the
row-carrier design (sentinel `BASELINE_INCONSISTENT`, `ruleType=BaselineConsistency`, builder-time
`fileName` labels) is preserved in git history should the stance soften later; (b) strict/lenient
request flag — no second behavior mode.

## 3. Per-site skip grains (audited inventory)

The collector conversion uses each site's safe continue-grain so later sites still run and report:

| Class | Sites | Skip grain on failure |
|---|---|---|
| `CountingHeadExpectationsBuilder` | 79, 108, 114, `parseId`:191 | per-FMA (79); per-head (108); per-head with DIR_INV/SLCT_TIMEOUT both skipped (114 — never default a chain comparison); map sites per-AEB entry |
| `SupervisorExpectationsBuilder` | 71, 85, 110, `parseId`:153 | per-track (71, 110); per-operand (85); map sites per-AEB entry |
| `ControlExpectationsBuilder` | 121, 129, 144*, 164, `parseId`:323 | per-DP (121, 129, 144); per-block (164). *144 (junction eChc) is transitional — VTF-372 replaces it with real derivation |
| `AcoExpectationsBuilder` | `parseId`:113 | whole host AEB (POSITIONAL slots must not shift) |
| `IpSwitchExpectationsBuilder` | `parseId`:52 | per-COM |
| `ForwardingExpectationsBuilder` | 102, 124, 140, 144, `parseId`:199 | per-(track, head) at 124; per-track at 140; per-chain at 144; map sites per-AEB entry |
| `DataTransmissionExpectationsBuilder` | 48, 70, `parseId`:110 | whole CFG_DATA_OUT slice at 48 (size mismatch — never prefix-pair); per-row at 70; map sites per-AEB entry |
| `ForwardingDestinationResolver` | 98, 104, 110, 116, 121, `parseSocket`:165 | per-member outcomes (`canTxId`, `destCom` \| problem); all six conditions collect, incl. NW1/NW2 disagreement (121) |

Audit notes folded in: `Control:144` 0-owner case unreachable; `parseSocket:165` throws NFE only;
`CountingHead:114` also fires on a present DP row with a null POSITION cell.

## 4. Root-cause hardening included (independent of the model)

- **Trim-asymmetry hole**: the gate reconciles trimmed names but builders key/probe raw strings — a
  trailing space passes the gate then fails post-gate. Fix: trim-normalize keys and probes in every
  builder map (`trackByName`, `fmaByName`, `idByDpName`, `positionByDpName`, DT maps). Closes the
  practical trigger for the four "defensive; the gate guarantees a match" sites (whose comments are
  not currently true).
- **Eager map poisoning**: `parseId` inside `idByDpName`/`chainByDpId` construction aborts on the
  first malformed AEB dpId. Per-entry tolerance (collect + skip entry) so ALL malformed ids are
  enumerated in one run.

## 5. Implementation milestones

- **M1** — trim-normalization of builder maps + regression tests (land first; independent).
- **M2** — collector plumbing: `BaselineInconsistencyCollector` (request-scoped, passed through the
  preprocessor seam), formatted-list exception assembly, `DefaultExpectationsPreprocessor` end-check,
  per-entry-tolerant map builders.
- **M3** — builder-by-builder conversion at §3 grains (one commit per builder; fault-injected fixture
  tests asserting: run continues, ALL injected problems appear in one message, message names the
  reference + raw value).
- **M4** — resolver per-member outcomes + evaluation-phase collection (incl. NW1/NW2 disagreement and
  the decided NW2-strict policy).
- **M5** — verification: suite green; fault-injected Package W variants (renamed DP, trailing-space
  track name, non-numeric dpId, partial COM upload) each produce ONE 400 enumerating every injected
  problem; clean packages unchanged.

## 6. Decision log (all locked 2026-07-09)

| Id | Decision |
|---|---|
| D1 | **Reject-with-complete-list** (accumulate-then-400); no partial report; no strict/lenient flag |
| D2 | Moot under D1 (no report rows). Historical row-carrier proposal preserved in git history |
| D3 | Moot under D1; the per-item source context (PDQ/FCT/ADC file) lives in each message list item |
| D4 | NW2 mirror missing/unmatched ⇒ member **unresolved**, recorded as a listed problem |
| D5 | Payload = numbered list in the existing `message` field (TrackReconciliation precedent); no contract change, no FE coordination needed |
