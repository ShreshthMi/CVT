# VTF-359 — Accept PRIMARY/SECONDARY as a redundant COM pair (as-built)

Status: **DONE** — `origin/VTF-359` (`0c41f2a`, off `b1b9edc`/VTF-338). Suite 245 green (243 + 2 new).
Plan reference: [vtf-359-series-alpha-fix-plan.md](vtf-359-series-alpha-fix-plan.md) §3.

## Problem

`FctProjectXmlParser.resolveCom` recognised a 2-COM CAN segment only as a `MASTER`+`SLAVE`
`ComMode` pair. Baseline FCTs also label redundant pairs **`PRIMARY`+`SECONDARY`** (user-verified
against baseline FCT; observed in the real alpha bundle P0589_LSC-46, both segments). Result: the
entire FCT rejected — HTTP 400 `FCT_INCOMPLETE_BASELINE` / log `MULTI_COM_NO_REDUNDANCY`.

## Change (all in `service/fct/`)

- `FctProjectXmlParser.resolveCom`: 2-COM branch now tries `pairLead(coms, "MASTER", "SLAVE")` then
  `pairLead(coms, "PRIMARY", "SECONDARY")` — **within-vocabulary** pairing (a mixed pair such as
  MASTER+SECONDARY still rejects). The chain's COM is the lead (MASTER / PRIMARY), `redundant=true`.
- Rejection diagnostics now embed the observed COM names + ComModes in the log detail
  (`…pair: COM-AdC-1(M)=PRIMARY, COM-AdC-1(R)=PRIMARY`), matching the file's convention for other
  reasons; the external body is unchanged (locked 2-code contract).
- Javadoc + `FctInvalidReason.MULTI_COM_NO_REDUNDANCY` comment updated to name both vocabularies.

## Tests

- `collapsesPrimarySecondaryRedundancy` — same committed fixture, ComModes substituted in-memory
  (MASTER→PRIMARY, SLAVE→SECONDARY). No new binary fixture committed (deviation from plan §3, which
  suggested one modeled on Pkg2: substitution exercises the identical parse path without committing
  customer-derived data).
- `rejectsMixedRedundancyPair` — MASTER+SECONDARY → `MULTI_COM_NO_REDUNDANCY`.
- Both collapse tests (old MASTER/SLAVE + new) now **pin lead-COM selection**
  (`com().comName() == "COM-AdC(M)"`) — an argument-swap regression previously stayed green
  (found by the pre-commit adversarial review).

## Verification against real FCTs (local harness)

| File | Before | After |
|---|---|---|
| Pkg2 `P0589_LSC-46.fct2` (2× PRIMARY/SECONDARY) | 400 | **200** — 2 chains (10+8 AEBs), both `redundant=true`, chain COMs = `COM-AdC-1(M)`, `COM-AdC-2(M)` |
| Pkg1 `P0708_BDRI 1.fct2` (single NORMAL COM) | 200 | 200 (unchanged) |
| `P0513_Arakkonam` (12 segments, all NORMAL) | 200 | 200 — 12 chains, no false pairing of its same-named COM-AdC-1/-2 pairs |

Pkg2 `/validate` now proceeds to the next planned blocker (`AD01A` junction-eChc gate) — exactly the
plan's post-370 checkpoint. Next: **VTF-360** (post-gate baseline error handling, design pending) or
**VTF-361** (junction eChc semantics) per the plan's ordering note.
