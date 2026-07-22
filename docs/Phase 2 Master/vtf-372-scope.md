# VTF-372 — junction eChc semantics (as-built)

Status: **DONE** — branch `VTF-372` off `VTF-371` (`445dab4`), 1 commit, pushed. Suite **258 green**;
adversarial diff-review clean (owners-never-empty on the boundary path, no duplicate/half set, middle
& eChc=NO paths untouched, test covers the multi-owner regression). Plan:
[vtf-370-series-alpha-fix-plan.md](vtf-370-series-alpha-fix-plan.md) §5.

## What shipped

A same-sign **junction** counting head — a boundary DP shared by >1 main track on the same side
(the ABS `AD01A`/`AU09A` layout: one head is the `dpIn`/`dpOut` of two main tracks) — with
`eChc=YES` now **derives** its CHC expectations instead of being recorded as a baseline inconsistency
(the VTF-371 transitional skip).

`ControlExpectationsBuilder`, boundary + `eChc=YES` branch:
- emit **one `CFG_CONTROL` block per owning main track** (BY_IDENTITY, referencing each owning
  track's FCT FMA host by `(ID, SECTION)`, `SLCT_TIMEOUT` = same/different COM chain)
- plus `BEHAV_INPUT3 = "7"`, for any owner count ≥ 1.

The plain single-owner boundary case is subsumed (still exactly 1 block) — the `owners.size() != 1`
skip is gone. The middle-DP and `eChc=NO` paths are untouched. (User-confirmed decision 2026-07-06.)

## The payoff

**Package 2 (P0589, ABS) now validates end-to-end** — the junction DPs no longer abort the run:

| | before (VTF-371) | after (VTF-372) |
|---|---|---|
| pkg2 `/validate` | HTTP 400 (`AD01A`, `AU09A` junction) | **HTTP 200**, 729 results |
| junction `BEHAV_INPUT3` | not derived | `AD01A`(C0685), `AU09A`(C0671) → expect 7, **actual 7 → PASS** |
| pkg1 (P0708) regression | 951 / 272 | **951 / 272 — byte-identical** |
| suite | 258 | 258 |

## Newly-visible findings for domain review (NOT VTF-372 defects)

Because the pipeline now runs to completion on pkg2, three real config-vs-baseline results surface that
the first-throw abort previously hid. These are **data for the AE/domain team, not code bugs** — the
derivation faithfully implements the confirmed rule; the tool is flagging what it was built to flag:

- **O2 / junction `CFG_CONTROL` (C0685 `AD01A`, C0671 `AU09A`)**: each junction expects a `CFG_CONTROL`
  member (`ID=676`/`ID=657`, `SECTION=0`) that the ADC does not carry →
  `EXPECTED_OCCURRENCE_NOT_FOUND`. Either the ADCs lack the junction CHC blocks the contract mandates,
  or the junction should reference different tracks. Needs domain confirmation before any rule change.
- **S2 / C0684 (`AD07A`) `BEHAV_INPUT3` expect 6 / actual 7**: `AD07A` is a plain boundary head
  (`eChc=NO` per the PDQ → expect 6) whose ADC carries `7`. Unaffected by VTF-372 (not a junction).
  This is part of the eChc-scope / firmware-default question tracked for **VTF-373**, or a site finding.
- The large `BEHAV_INPUT3 = 6 → CONFIG_BLOCK_OR_PARAM_NOT_FOUND` FAIL cluster is the known N3 noise
  (BEHAV_INPUT3 emitted per counting-head DP but `CFG_AXCNT` exists only on IO-EXB files) → **VTF-373 M5**.

## Test

`ControlExpectationsBuilderTest.junctionEChcDpEmitsOneControlBlockPerOwningTrack` — a 2-owning-track
junction (one owner same-chain → `SLCT_TIMEOUT=0`, one cross-chain → `SLCT_TIMEOUT=1`) asserts 2
`CFG_CONTROL` + `BEHAV_INPUT3=7` and no recorded problem. The single-owner boundary + `eChc=YES` case
stays covered by `emitsMiddleAndBoundaryAndExcludesCombination`.
