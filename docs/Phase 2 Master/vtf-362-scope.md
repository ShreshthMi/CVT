# VTF-362 — scope + soften scalar rules + BEHAV_INPUT3 scoping (as-built)

Status: **DONE** — branch `VTF-362` off `VTF-361`, 3 commits (`bb05155` config, `498f2e8` M5,
`691af51` CFG_PROJECT_COM), pushed. Suite **259 green**.
Plan: [vtf-359-series-alpha-fix-plan.md](vtf-359-series-alpha-fix-plan.md) §6.
Pass 1 = rule scoping/softening; M5 = preprocessor BEHAV_INPUT3 scoping; final = CFG_PROJECT_COM to COM.
**Net: every spurious FAIL across both alpha packages is gone — all remaining FAILs are genuine findings.**

## What shipped

The ~28% spurious-FAIL noise had two root causes — **wrong audience** (COM/IO-EXB/supervisor-only
blocks broadcast to every file) and **absent-at-default** (a block commented out, running its firmware
default). Both are fixed with the existing rule machinery — no engine code, only registry entries:

- **Scoping**: `"ValidateOnlyInFilesWith": "TRACKSECTIONDETAILS"` on each target rule (the same field
  `CFG_SECTION`/`CFG_ZP` already use). The engine gates on `file.isTrackSectionDetails()` and
  **suppresses even the default rule** on non-AEB files, so these blocks produce no rows on COM files.
- **Default tolerance**: the `*OrBlockNotFound` rule types PASS an absent block iff the PDQ value equals
  the rule's `DefaultValue` (= firmware default). Set to the firmware defaults from the ADC template
  comments.

| Block / param | Rule type | DefaultValue | Change |
|---|---|---|---|
| `CFG_OCC` OCC_DELAY / OCC_EXT | `OptionalInputMatchOrBlockNotFound` | 0 / 26 | edit (+ marker) |
| `CFG_IP_SWITCH_TIME` IP_SWITCH_TIME | `OptionalInputMatchOrBlockNotFound` | 10 | new |
| `CFG_SWITCH` SWITCH_GE / SWITCH_GSF / PRERESET_ACT_TIME | `OptionalInputMatchOrBlockNotFound` | 26 / 0 / 180 | new |
| `CFG_SUPERVIS_FMA1` & `FMA2` RESET_TYPE / RESET_DELAY | `InputMatchOrBlockNotFound` | 3 / 30 | new |

`InputMatchOrBlockNotFound` (RESET) is strict when the block IS present, so a supervisor file with a
wrong RESET value still FAILs; absent (non-supervisor AEB) PASSes at the default. `CFG_PROJECT_COM`
excluded per decision.

## User decisions honoured

- **Shared registry accepted**: this edits the single registry Phase-1 also reads. The `Optional*`
  blocks are dormant without a payload (Phase-1 unaffected); the `InputMatchOrBlockNotFound` RESET
  entries add benign PASS rows to Phase-1 on AEB files. Suite (incl. Phase-1 tests) stays 258 green.
- **`OCC_EXT` left as a real finding**: its firmware default (26) ≠ the PDQ value (0), so it stays a
  FAIL — the tool correctly says "the device would run 26, the plan wants 0."

## Verified on real packages

| | before (VTF-361) | after |
|---|---|---|
| pkg1 P0708 | 951 / **272 FAIL** | 831 / **64 FAIL** |
| pkg2 P0589 | 729 / **199 FAIL** | 709 / **53 FAIL** |

Cleared (now PASS on AEB files, no rows on COM): `CFG_IP_SWITCH_TIME`, `CFG_SWITCH` ×3,
`CFG_SUPERVIS_FMA1/2` RESET ×4 — ~163 FAILs in pkg1. Supervisor files keep their strict RESET match
(38 + 30 PASS). All real findings preserved: `OCC_EXT` (14), `C1008`/`C1020` `CFG_CONTROL` (2), pkg2
junction/supervisor findings.

## M5 — BEHAV_INPUT3 scoping (commit `498f2e8`, preprocessor)

`BEHAV_INPUT3` (a `CFG_AXCNT` param) was emitted per counting-head DP by `ControlExpectationsBuilder`
(the instanced path — so it bypasses the registry marker), but `CFG_AXCNT` exists only on ACO-IO-EXB
files → `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` FAIL on every non-IO-EXB DP. Fix: `BaselineIndex` now records
which DP ids carry an ACO IO-EXB (`FctAeb.acoIoExbs` non-empty → `hasAcoIoExb(dpId)`), and
`ControlExpectationsBuilder.addBehavInput3` emits only for those files — matching the existing
`ACOIOEXBDETAILS` scope of the scalar `CFG_AXCNT` rules. `CFG_CONTROL` blocks (on the DP file itself)
are unaffected.

- pkg1 BEHAV_INPUT3: 22 FAIL → **2** (only C1008/C1020, exp 7 / act 6 — real, they ARE IO-EXB files).
- pkg2 BEHAV_INPUT3: 15 FAIL → **1** (C0684 / AD07A, exp 6 / act 7 — the S2 finding, kept).
- Total: pkg1 **272 → 64 → 44 FAIL**; pkg2 **199 → 53 → 39 FAIL**.
- Edge (not in samples): an `eChc=YES` DP with no IO-EXB now drops its `BEHAV_INPUT3=7` (the
  `CFG_CONTROL` check still emits). If such a config appears, consider a named "missing CHC hardware"
  finding (plan O4).

## CFG_PROJECT_COM — scope to COM files (commit `691af51`, config)

`CFG_PROJECT_COM` (the COM's project block) had no registry entry → default `InputMatch` broadcast it
to every file → 26/18 spurious FAILs on AEBs, and the COM (where it belongs) was never checked. Fix:
one entry mirroring `CFG_PROJECT_AEB` but pointed at COM files —
`{RuleType: ProjectBlockCheck, ValidateOnlyInFilesWith: COMDETAILS, SkipComFile: false}`.

- **`SkipComFile: false` is required** — `ValidationContext.shouldSkipForComFile()` defaults `skipCom`
  to `true` when the field is *unset*, so without it the COM file is skipped and the block goes
  silently unchecked (noise clears but no real check). With `false`, `ProjectBlockCheck` runs on the
  COM (block absent + PDQ `BLOCK_EXISTS: false` → PASS).
- Result: 26/18 AEB FAILs gone; COM now validated — pkg1 1 PASS (C1091), pkg2 2 PASS (C0691/C0692).

## Remaining FAILs — all genuine findings (zero noise left)

| Cluster | pkg1 / pkg2 | Nature |
|---|---|---|
| `CFG_OCC` OCC_EXT | 14 / 16 | real finding (device default 26 ≠ PDQ 0) |
| `CFG_CONTROL` | 2 / 2 | real (pkg1 C1008/C1020; pkg2 O2 junctions) |
| `CFG_AXCNT` BEHAV_INPUT3 | 2 / 1 | real (C1008/C1020; C0684 = S2) |
| `CFG_SUPERVIS_FMA1` members | — / 2 | pkg2 S3 findings |

Journey: pkg1 **272 → 64 → 44 → 18 FAIL**; pkg2 **199 → 53 → 39 → 21 FAIL** — every remaining FAIL is real.

## Notes / follow-ups

- **Firmware defaults** (10 / 26 / 0 / 180 / 3 / 30) are taken from ADC template comments; should be
  confirmed against the Frauscher firmware-default spec.
- **Scope is `trackSectionDetails==true`** (14 of 26 AEB files in pkg1) — consistent with existing
  `CFG_SECTION`/`CFG_ZP` scoping; non-track-section AEBs are intentionally not checked for these blocks.
- **Still open in the plan**: VTF-374 (/02 redundant-channel CHC coverage — diagnosed, deferred), VTF-375
  (hardening batch). Noise cleanup is complete.
