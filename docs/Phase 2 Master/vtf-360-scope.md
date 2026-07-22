# VTF-360 — post-gate baseline error handling (as-built)

Status: **DONE** — branch `VTF-360` off `VTF-359` (`0c41f2a`), 4 commits `55b2b5c`→`445dab4`, pushed.
Suite 245 → **258 green**. Adversarial diff-review + a focused skip-grain correctness re-review both
returned clean (no half-emission / null-chain / trim-collision / positional-shift / dedup-collapse
defects). Design: [vtf-360-design.md](vtf-360-design.md).

## What shipped

Post-gate baseline inconsistencies no longer abort at the first problem. Builders and the forwarding
resolver **record every unresolvable reference and skip at a safe grain, continuing**; a single
end-check then rejects with ONE `PHASE2_BASELINE_INCONSISTENT` (HTTP 400) whose `message` enumerates
the complete numbered list (the `TrackReconciliationFailedException` precedent — no FE/contract change).
A clean baseline is unaffected.

## Commits

1. **`55b2b5c`** — infrastructure: `BaselineInconsistencies` (dedup accumulator, `throwIfAny` builds the
   numbered-list exception) + `BaselineIndex` (shared trim-normalized FCT/Control-Table lookups, built
   once per request, per-entry-tolerant id parsing). + 2 unit tests.
2. **`feca9d2`** — all 7 instanced builders + `DefaultExpectationsPreprocessor`: collect-and-skip at the
   audited grains, one end-check. Builder tests rewritten (throws → accumulated-item assertions) + a new
   `DefaultExpectationsPreprocessorTest` asserting 4 distinct defects enumerate in one message.
3. **`0e65831`** — `ForwardingDestinationResolver` per-member outcomes (unresolvable member recorded +
   excluded; **NW2-strict**, D4); `InstancedExpectationEvaluator` collector overload; `ConfigValidationV2Service`
   evaluation-phase end-check; `MismatchAnnotator` throwaway collector. `ForwardingValidationTest` updated.
4. **`445dab4`** — sort the annotator's new import (review nit).

## Skip grains (as implemented)

| Builder | Grain |
|---|---|
| CountingHead | per-FMA (track/id unresolved); per-head, both DIR_INV+SLCT_TIMEOUT together (head/position unresolved) |
| Supervisor | per-track (host/operator unresolved); per-operand, both LOGIC_TYPE+SLCT_TIMEOUT together |
| ACO | **whole host AEB** — staged, emitted only if every POSITIONAL slot resolves (no position shift) |
| Control | per-DP (CFG_CONTROL + BEHAV_INPUT3); per-block for an unresolvable adjacent track. Junction `eChc=YES on ≥2 mains` recorded + skipped (transitional — VTF-361 derives it) |
| IpSwitch | per-COM |
| Forwarding (builder) | per-FMA; per-(track, head) tuple |
| DataTransmission | **whole CFG_DATA_OUT slice** on sub-table size mismatch (never prefix-pair); per-row otherwise |
| ForwardingDestinationResolver (evaluate) | per-member; NW1/NW2 disagreement + NW2-missing both recorded |

## Root-cause hardening (bundled, independent of the model)

- **Trim asymmetry closed**: the gate reconciles trimmed names; `BaselineIndex` now trim-normalizes all
  keys and probes, so a trailing space no longer passes the gate then fails post-gate. The four
  "the gate guarantees this; defensive" throw sites are removed as a class.
- **Eager map poisoning closed**: `BaselineIndex` id parsing is per-entry tolerant — one malformed FCT
  `dpId` is recorded once and only its own entries are skipped, so every malformed id enumerates in one
  run (previously the first aborted the whole request before any track was processed).

## Verification (real + injected)

| Input | Result |
|---|---|
| pkg1 clean (P0708) | 200, **951 results / 272 FAIL — byte-identical to VTF-359** |
| pkg2 (P0589, ABS) | one 400: *"2 problems: 1) …DP 'AD01A'…; 2) …DP 'AU09A'…"* (both junctions, was first-throw of one) |
| fault-injected pkg1 (DP01→DP01X, DP20→DP20X) | one 400 enumerating all **4** derived problems |
| unit | `DefaultExpectationsPreprocessorTest` asserts 4 cross-builder defects in one message; per-builder tests assert record-and-continue |

## Follow-ups / notes

- `Control:144` junction skip is transitional — **VTF-361** replaces it with real per-owning-track
  derivation (the message will then disappear for AD01A/AU09A-style DPs).
- The `BaselineGate` 5 hard sites are unchanged (admission control stays a hard 400).
- Contract unchanged: still `{errorCode, message, timestamp}`; no new status/field.
