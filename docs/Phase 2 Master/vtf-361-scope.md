# VTF-361 — junction eChc semantics (as-built)

Status: **DONE** — branch `VTF-361` off `VTF-360` (`445dab4`), 1 commit, pushed. Suite **258 green**;
adversarial diff-review clean (owners-never-empty on the boundary path, no duplicate/half set, middle
& eChc=NO paths untouched, test covers the multi-owner regression). Plan:
[vtf-359-series-alpha-fix-plan.md](vtf-359-series-alpha-fix-plan.md) §5.

## What shipped

A same-sign **junction** counting head — a boundary DP shared by >1 main track on the same side
(the ABS `AD01A`/`AU09A` layout: one head is the `dpIn`/`dpOut` of two main tracks) — with
`eChc=YES` now **derives** its CHC expectations instead of being recorded as a baseline inconsistency
(the VTF-360 transitional skip).

`ControlExpectationsBuilder`, boundary + `eChc=YES` branch:
- emit **one `CFG_CONTROL` block per owning main track** (BY_IDENTITY, referencing each owning
  track's FCT FMA host by `(ID, SECTION)`, `SLCT_TIMEOUT` = same/different COM chain)
- plus `BEHAV_INPUT3 = "7"`, for any owner count ≥ 1.

The plain single-owner boundary case is subsumed (still exactly 1 block) — the `owners.size() != 1`
skip is gone. The middle-DP and `eChc=NO` paths are untouched. (User-confirmed decision 2026-07-06.)

## The payoff

**Package 2 (P0589, ABS) now validates end-to-end** — the junction DPs no longer abort the run:

| | before (VTF-360) | after (VTF-361) |
|---|---|---|
| pkg2 `/validate` | HTTP 400 (`AD01A`, `AU09A` junction) | **HTTP 200**, 729 results |
| junction `BEHAV_INPUT3` | not derived | `AD01A`(C0685), `AU09A`(C0671) → expect 7, **actual 7 → PASS** |
| pkg1 (P0708) regression | 951 / 272 | **951 / 272 — byte-identical** |
| suite | 258 | 258 |

## Newly-visible findings — investigated, decided as AE-register items (NOT VTF-361 defects)

Because the pipeline now runs to completion on pkg2, real config-vs-baseline results surface that the
first-throw abort previously hid. Both O2 and S2 were traced to ground truth (raw ADC blocks + FCT
hosts) and **decided 2026-07-21: leave as flagged findings for the AE/domain team — no code change**.
The derivation faithfully implements the confirmed rule; the tool is flagging what it was built to flag.

### O2 — junction `CFG_CONTROL` (C0685 `AD01A`, C0671 `AU09A`) → each one `EXPECTED_OCCURRENCE_NOT_FOUND`
Ground truth (raw ADCs + FCT FMA hosts):
- A **middle** head references the FMA host of *both* connected sections and the device carries both —
  e.g. `AU02A` (651): `CFG_CONTROL` → 651 (`199AXT1`, self-hosted) **and** 661 (`199XT1`) → both PASS.
- A **junction** head (two sections same direction) carries **only the section it hosts** — `AD01A`(685)
  → one block ID=685 (`C200XT`, self); `AU09A`(671) → one block ID=671 (`C1XT`, self). The other owning
  track (`A502AXT1` host 676 / `A505AXT1` host 657) has **no** block → the derivation's second expected
  member fails.
- Consistent 2-of-2: the real convention is "a junction owns exactly one section" — the "one per owning
  track" rule (decided 2026-07-06, before this device data) over-asks by one block.
- **Decision: keep flagging (leave as-is).** Treated as a real site finding to report, not a rule bug;
  the correct half still PASSes. No rule refinement in code. Revisit only if AE confirms otherwise.

### S2 — C0684 (`AD07A`) `BEHAV_INPUT3` expect 6 / actual 7
`AD07A` is the end-of-line sensor (`dpOut` of `2XT1`, a station boundary). PDQ `eChc=NO` → tool expects
6; the device is configured as a counting-head-control point (7, with a `CFG_CONTROL` block). A genuine
**PDQ-vs-device data disagreement** — most likely the PDQ `eChc` box was not ticked YES for this
end-of-line boundary. **Decision: leave as a flagged finding for AE** (they decide which document wins).
Not a tool bug; not a VTF-361 concern.

### (context) N3 noise
The large `BEHAV_INPUT3 = 6 → CONFIG_BLOCK_OR_PARAM_NOT_FOUND` FAIL cluster is the known scoping noise
(BEHAV_INPUT3 emitted per counting-head DP but `CFG_AXCNT` exists only on IO-EXB files) → **VTF-362 M5**.

## Test

`ControlExpectationsBuilderTest.junctionEChcDpEmitsOneControlBlockPerOwningTrack` — a 2-owning-track
junction (one owner same-chain → `SLCT_TIMEOUT=0`, one cross-chain → `SLCT_TIMEOUT=1`) asserts 2
`CFG_CONTROL` + `BEHAV_INPUT3=7` and no recorded problem. The single-owner boundary + `eChc=YES` case
stays covered by `emitsMiddleAndBoundaryAndExcludesCombination`.
