# VTF-359 series — alpha-feedback fix plan (execution planning doc)

Status: **PLANNING** (no implementation started). Drafted 2026-07-09.
Basis: empirical run of the two Package W alpha bundles against the VTF-338 tip (`b1b9edc`) on 2026-07-06,
plus the multi-agent code review of the full Phase 2 body (VTF-331..338) run the same week.

---

## 1. Evidence base

| Artifact | Where |
|---|---|
| Alpha inputs | `Downloads/Package W.zip` — Pkg1 `P0708_BDRI` (Centralised, 1 COM + 26 AEB), Pkg2 `P0589_LSC-46` (ABS, 2 chains, 2× PRIMARY/SECONDARY COM pairs, 18 AEB) |
| Server log of the FCT rejection | `Downloads/PackageW-server-log-2026-07-06.log` (key lines: `FCT_INCOMPLETE_BASELINE [MULTI_COM_NO_REDUNDANCY]`, `AD01A PHASE2_BASELINE_INCONSISTENT`) |
| Full Phase 2 diff | `Downloads/develop-phase1-to-vtf338.diff` (59b5cb5..b1b9edc, 118 files, +9075/−36) |
| Verification harness | §8 of this doc (re-runnable curl/python driver) |

Empirical result at `b1b9edc`:

| | Pkg 1 | Pkg 2 |
|---|---|---|
| PDQ upload | 200 | 200 |
| FCT upload | 200 | **400 `MULTI_COM_NO_REDUNDANCY`** |
| v2 validate | 951 results, **272 FAIL (28.6 %)** | **400 `AD01A not on exactly one main track`** (after B1 patched); 723 results / 197 FAIL (27.2 %) after both blockers patched |

Verified-fine on real data (no work needed): PDQ Ver14 parse of both workbooks; DT sheets legitimately
empty ("filled by AE team") → `dataTransmission=null` handled; counting heads / ACO positional / all 18
middle-DP CHC expectations PASS; combination-track exclusion; track reconciliation; no false ORPHANED.

Standing REAL findings (kept, per decision): Pkg1 `C1008`/`C1020` (the only `eChc=YES` DPs) genuinely
lack all E-CHC config (`BEHAV_INPUT3=6`, zero `CFG_CONTROL`, station-wide) — the boundary-eChc rule is
correct and these FAILs stand.

---

## 2. Git & delivery strategy

- **Numbering**: VTF-359 onwards. Provisional mapping below; renumber in Jira if it assigns differently.
- **Branch chain**: linear, matching Phase 2 practice — `VTF-359` branches off `b1b9edc` (VTF-338 tip);
  each subsequent story branches off the previous story's tip. Push each `origin/VTF-3xx`.
- **Commits**: `VTF-3xx: <imperative summary>` (matches `VTF-338 BE-08: …` style), small logical commits.
  No co-author trailers.
- **Naming**: domain concepts only (never story numbers in identifiers); `V2` not `Phase2`; acronyms
  Title-case (`Com`/`Cqir`/`Pdq`).
- **Tests**: suite is 243 green at b1b9edc — every story keeps it green and adds coverage. Fixtures are
  committed under `src/test/resources/fixtures` and loaded via `FctFixtures`/`PdqFixtures`; never
  reference `Downloads/` or `docs/samples/` from tests. Package W itself is real project data — it stays
  a **local verification harness** (§8), not a committed fixture; story-sized synthetic fixtures replicate
  the shapes it exposed.
- **Docs cadence**: as-built `vtf-3xx-scope.md` per story in `docs/Phase 2 Master/` on develop,
  committed + pushed (`git push origin develop:main`).
- **External sync**: after the round completes, sync to `testV2` via the established
  format-patch → `From:` rewrite (`shreshth.mishra@frauscher.com`) → `git am` process; verify by tree hash.

Order of execution: **370 → 371 → 372 → 373 → 374 → 375.** Soft dependency only: 371's design is still
under investigation (user); if not decided in time it slides later without blocking 372–375. 372's
*verification on Pkg2* requires 370 (FCT must parse) — its code does not.

---

## 3. VTF-359 — FCT redundancy vocabulary: accept PRIMARY/SECONDARY (Story 1)

**Problem.** `FctProjectXmlParser.resolveCom` (lines 184-191) recognises a 2-COM CAN segment only as a
`MASTER`+`SLAVE` `ComMode` pair. Real baseline FCTs also use **`PRIMARY`+`SECONDARY`** as a valid
alternative pair (user-verified against baseline FCT 2026-07-08). Pkg2's both segments are
PRIMARY+SECONDARY → both throw `MULTI_COM_NO_REDUNDANCY` → whole FCT rejected with the (by-design
generic) external body `{"errorCode":"FCT_INCOMPLETE_BASELINE","message":"FCT archive is invalid"}`.

**Change** (`service/fct/FctProjectXmlParser.java`):
- 2-COM branch pairs **within-vocabulary**: (`MASTER`+`SLAVE`) or (`PRIMARY`+`SECONDARY`); a mixed pair
  (e.g. MASTER+SECONDARY) still throws `MULTI_COM_NO_REDUNDANCY`.
- The chain's COM = the MASTER / PRIMARY respectively; `redundant=true`.
- Update Javadoc + `FctInvalidReason.MULTI_COM_NO_REDUNDANCY` comment and detail message to name both pairs.

**Tests.**
- New committed fixture `fct-phase2-redundant-primary.fct2` modeled on Pkg2's real structure
  (2 Bp-connected segments, PRIMARY+SECONDARY pair each) + `FctFixtures` accessor.
- Parser unit tests: PRIMARY/SECONDARY collapse (chain COM = PRIMARY, `redundantComPresent=true`),
  mixed-pair rejection, existing MASTER/SLAVE fixture unchanged.
- Regression: run the third real FCT (`P0513_Arakkonam`, all-NORMAL, 10 COMs) through the parser —
  confirms multi-segment NORMAL topology still resolves (its COM-AdC-1/-2 pairs must land in separate
  CAN segments; if they share one, that is a new real-world case to surface, not silently accept).

**Acceptance.** Pkg2 FCT upload → 200 with 2 chains (10 + 8 AEBs); suite green. (Pre-verified with a
throwaway patch on 2026-07-06.)  Size: **S**.

---

## 4. VTF-360 — post-gate baseline error handling (own story; design pending)

**Problem (empirically confirmed).** Baseline inconsistencies discovered *after* the `BaselineGate`
(inside expectation builders and `ForwardingDestinationResolver` during `/validate`) throw
`BaselineInconsistentException` → HTTP 400 → **one topology oddity aborts the entire report**
(Pkg2: single DP `AD01A` killed a 723-result run). First-throw also hides every *other* inconsistency.

**Decision pending** (user investigating). Options to evaluate:
- **A — degrade to findings**: hard rejection stays only in `BaselineGate` (pre-validation); builder /
  resolver oddities become named per-result rows (e.g. status `INVALID`, sentinel expected/actual, in
  the spirit of the BE-08 vocabulary) and the report always returns.
- **B — strict/lenient flag**: request-level mode; strict for acceptance runs, degrade for field/alpha use.
- **C — keep 400 but accumulate**: collect *all* post-gate inconsistencies and return them in one
  error payload (mirrors the gate's accumulate-then-report reconciliation), instead of first-throw.

**Scope once decided.** Inventory + centralise the policy over all post-gate throw sites:
`ControlExpectationsBuilder` (3 sites), `CountingHeadExpectationsBuilder`, `SupervisorExpectationsBuilder`,
`AcoExpectationsBuilder`, `DataTransmissionExpectationsBuilder` (length guard),
`ForwardingDestinationResolver` (§3.3). Optional (FE-contract change — coordinate first): enrich the
FCT/PDQ upload error body with the reason (alpha testers currently see only "FCT archive is invalid";
the true reason is log-only by the locked BE-03 contract).

Acceptance & tests: defined with the decision. Size: **M** (A or C), **M/L** (B).

---

## 5. VTF-361 — junction eChc semantics (Story 2)

**Problem.** ABS topology has **same-sign junction DPs**: `AD01A` = `dpIn` of two main tracks
(`C200XT`, `A502AXT1`); `AU09A` = `dpOut` of two (`A505AXT1`, `C1XT`). With `eChc=YES` they fail
`ControlExpectationsBuilder`'s "boundary must own exactly one main track" check (line 143) → today the
whole run 400s (see VTF-360).

**Decided semantics (2026-07-06):** a same-sign junction DP with `eChc=YES` expects
**one `CFG_CONTROL` reference per owning main track** (AD01A/AU09A → 2 references each, identity
`(ID, SECTION)` of each owning track's FMA host, `SLCT_TIMEOUT` same/diff-chain as usual) **plus
`BEHAV_INPUT3 = 7`**.

**Change** (`service/preprocessor/ControlExpectationsBuilder.java`): in the boundary/eChc branch, replace
the `owners.size() != 1` throw with emission of one `addControlBlock(...)` per owning track; keep
`BEHAV_INPUT3="7"`. Same-sign eChc=NO stays as-is (no CHC, `BEHAV_INPUT3=6`). The middle-DP path is
untouched (field-validated: 18/18 PASS on Pkg1).

**Tests.** Builder unit tests for: 2-owner same-sign eChc=YES (both signs), eChc=NO junction, and the
existing single-owner boundary. Integration expectation-shape test with an ABS-like control table.

**Verification on Pkg2** (needs 370): validate returns 200 without any investigative patch; inspect the
new `CFG_CONTROL` member results for AD01A/AU09A hosts and re-examine open item **S2**
(`C0684` `BEHAV_INPUT3` exp=6/act=7 — likely a junction *B-side* head (`AD01B`/`AU09B`) where the site
configured 7; decide from the real result whether the B-side rule needs refinement or it is a site
finding). Size: **S/M**.

---

## 6. VTF-362 — spurious-FAIL elimination (Story 3; the big one)

~97 % of the alpha FAIL noise, identical shape in both packages. Milestones are independently
verifiable against Package W.

**M1 — block→file-type ownership matrix (analysis, in-story).** Before coding: grep matrix over Package W
ADCs (+ MIO/Surabhi samples) per noise block — which file types (COM vs AEB vs IO-EXB-carrier) ever carry
`CFG_IP_SWITCH_TIME`, `CFG_PROJECT_COM`, `CFG_SWITCH`, `CFG_OCC` live or commented, and with what
firmware-default comment values. Output: a table in the scope doc driving M2/M3 per block. (Known so far:
AEBs carry `CFG_OCC`/`CFG_SWITCH` commented with defaults equal to the expected values (26/0/180);
the COM carries `CFG_PROJECT_COM` commented with default 0 = expected.)

**M2 — COM-only scalar scoping.** Blocks the matrix shows are COM-only stop being broadcast to every
file: move them from the flat scalar bucket (`ScalarExpectationsBuilder`) to instanced `SINGLE`
expectations targeted at each chain's COM `fileId` (the FCT provides it). Eliminates the
AEB-side `CONFIG_BLOCK_OR_PARAM_NOT_FOUND` wall (26/18 × several blocks).

**M3 — firmware-default absence tolerance (the deferred M4 registry, revived).** Absent block/param
whose expected value equals the firmware default → PASS (rule `OptionalInputMatchOrBlockNotFound`
semantics; instanced `SINGLE` already supports `defaultValue` — reuse that seam). Registry populated
from the M1 matrix + ADC inline comments; per-entry, versioned in config not code.
Covers: `CFG_OCC.OCC_EXT`, `CFG_SWITCH.*` on AEBs, `CFG_PROJECT_COM.PROJECT_NUMBER=0` on the COM, and
any M2-scoped block that is commented-out on its own file type.

**M4 — supervisor `RESET_TYPE`/`RESET_DELAY` re-modeling.** Today: global CQ-IR scalars broadcast to all
files (92/72 FAILs). Change (`SupervisorExpectationsBuilder` + `ScalarExpectationsBuilder`): attach them
only to supervisor-hosting files — as `SINGLE` instanced expectations emitted per supervisor host
(same derivation that already places the `CFG_SUPERVIS_FMAx` member expectations).

**M5 — `BEHAV_INPUT3` scoping to IO-EXB carriers.** Today emitted per counting-head DP; `CFG_AXCNT`
exists only on IO-EXB-equipped files (exactly the 6/26 with live `CFG_SECTION_OUT` in Pkg1). Gate the
`InstancedExpectation.single(fileId, AXCNT, BEHAV_INPUT3, …)` emissions in `ControlExpectationsBuilder`
on the FCT's IO-EXB knowledge (`FctAeb.acoIoExbs` non-empty or `dtIoExbCount > 0`). Decision to record
in the scope doc: for eChc=YES DPs *without* IO-EXB, does the expectation disappear or become a named
finding? (Recommend: named finding — eChc mandates CHC hardware.)

**Acceptance.** Suite green; Package W verification (§8) shows **Pkg1 272 → 4 FAILs** (the standing
C1008/C1020 real findings) and **Pkg2 197 → ≈3** (S2/S3 items pending their own verdicts). No PASS that
was previously FAIL may be a *false* pass — each milestone's scope doc lists the exact clusters it clears.
Size: **L** (5 milestones, but each small and independently landable).

---

## 7. VTF-374 — /02 redundant-channel CHC coverage (Story 4)

**Problem (agent-verified).** Pkg1 files `C1022/23/27/29` (redundant `/02` channel heads) each carry
**2 real `CFG_CONTROL` blocks** pairing the `/02` section with the `/01` section across the physical
head (e.g. `C1023` = {(1009,0) `103AXT`, (1023,0) `201XT2`}). The builder's name-level adjacency treats
`DP09/01` and `DP09/02` as unrelated DPs → classifies `/02` DPs as eChc=NO boundaries → derives 0
expectations → the evaluator selects nothing for that (file, block) → **8 real blocks completely
unvalidated** (not even `UNEXPECTED_OCCURRENCE`: set-equality never engages when a (file, block) group
has zero expectations — systemic blind spot worth a general guard).

**Change** (`ControlExpectationsBuilder`): group channel-suffixed DP names (`<name>/<NN>`) by physical
head; derive the cross-channel `CFG_CONTROL` pairing for the `/02` (and general `/NN`) channels per the
real-site convention observed in Pkg1 (each channel references its own section + the sibling channel's
section). Exact rule to be confirmed against all 4 files' block contents during implementation; record
in scope doc.
Also consider (same story or fold into 371): a generic evaluator guard emitting a named result when a
file has occurrences of an in-scope instanced block for which zero expectations were derived.

**Tests.** Builder tests with `/01`–`/02` control-table shapes; integration test asserting the 8 Pkg1
blocks produce results. Verification: Pkg1 gains new PASS rows (or real findings) for C1022/23/27/29 —
count of CFG_CONTROL results rises from 38.  Size: **M**.

---

## 8. VTF-375 — hardening batch (Story 5; ultra-review carryovers)

Confirmed by the multi-agent review of VTF-331..338 (all still present at `b1b9edc`):

1. **Locale-pinned Excel formatting** — all 5 PDQ parser classes (`PdqSheetHeaderParser`,
   `CqIrSheetParser`, `ControlTableParser`, `DataTransmissionParser`, `ProjectBlockResolver`) construct
   `new DataFormatter()` with the JVM default locale; on comma-decimal locales (de_DE, fr_FR …) the
   numeric Sl.No cell `1.09` renders `"1,09"` ≠ contract key `"1.09"` → every valid PDQ rejected
   (`SHEET_MISSING`→`PDQ_INVALID`). Fix: `new DataFormatter(Locale.US)` (or ROOT) everywhere; add a
   locale-forced parser test.
2. **`OutputFma1` NPE → 500** — `FctProjectXmlParser.toAcoIoExb` line 228 dereferences `one[0..2]`
   unguarded (sibling `two` IS guarded). Malformed/tampered ACO block → NPE → catch-all 500 instead of
   `FCT_TAMPERED` 400. Fix: symmetric guard or throw `XML_PARSE_FAILED`.
3. **DT sub-table pairing by physical row** — `DataTransmissionParser` skips blank/footer rows
   independently per column; `DataTransmissionExpectationsBuilder` pairs by post-compaction index behind
   a size-equality guard → asymmetric blanks silently mispair receiving/source DPs. Fix: carry the sheet
   row index through parsing and pair on it (or emit linked rows). (DT sheets are empty in current alpha
   packages — fix before a filled-DT package arrives.)
4. **Annotator cell-join fixes** (three, from the review): forwarding UNEXPECTED index desync when a
   `CFG_FWRD_ACD` block has empty `CAN_TX_ID` (align on `blockIndex`, not filtered-array position);
   supervisor MISSING lands on the FMA1 row (disambiguate by `(fileId, block)`); ACO row located by
   non-unique comment (key by `(dpId, position)`).
5. **Catch-all logging** — `GlobalExceptionHandler`'s `Exception.class` handler logs `"Unexpected error"`
   with **no exception class or stack**; real 500s are undiagnosable (observed live during the alpha
   run). Fix: log the throwable.
6. Optional/cheap (include if the batch stays small): `ControlTableParser` `break`-on-blank-row →
   `continue` consistency; `RangeCheckRule` guards (payload-branch null cast + non-numeric actual →
   currently NFE→500, pre-existing landmine #9); POSITIONAL extra-detection `slots.size()` →
   `max(position)+1`.

Each item: focused unit test reproducing the failure first. Size: **M** (items are independent; can land
as separate commits in one story).

---

## 9. Verification harness (re-runnable)

Local-only; Package W is real project data and is never committed. Backend runs from the code worktree
(`D:/Frauscher/FCVT-VTF334`, port 7443, `./gradlew.bat bootRun -x test`).

Driver (per package: PDQ upload → FCT upload → ADC parse → v2 validate; results as JSON):

```bash
#!/bin/bash
# run-package.sh <label> <pdq.xlsx> <fct.fct2> <adc-dir-root>
LABEL="$1"; PDQ="$2"; FCT="$3"; ADCROOT="$4"; BASE="http://localhost:7443"
OUT="./results/$LABEL"; mkdir -p "$OUT"
curl -s -o "$OUT/pdq.json"    -w "PDQ %{http_code}\n" -F "file=@$PDQ" "$BASE/api/upload/pdq"
curl -s -o "$OUT/fct.json"    -w "FCT %{http_code}\n" -F "file=@$FCT" "$BASE/api/upload/fct"
ARGS=(); while IFS= read -r -d '' f; do ARGS+=(-F "files=@$f"); done \
  < <(find "$ADCROOT" -name "*.ADC" -print0 | sort -z)
curl -s -o "$OUT/parsed.json" -w "ADC %{http_code}\n" "${ARGS[@]}" "$BASE/api/upload/adcfiles"
python - "$OUT" <<'PY'
import json,sys; o=sys.argv[1]
body={"parsedConfigFiles":json.load(open(o+"/parsed.json")),
      "userInput":{"fctData":json.load(open(o+"/fct.json")),"pdqData":json.load(open(o+"/pdq.json"))}}
json.dump(body,open(o+"/validate-request.json","w"))
PY
curl -s -o "$OUT/summary.json" -w "VALIDATE %{http_code}\n" -H "Content-Type: application/json" \
  --data-binary "@$OUT/validate-request.json" "$BASE/api/config/v2/validate"
```

FAIL-cluster analysis: group `summary.json` → `validation_results` by
`(blockName, entryKey, expectedValue, actualValue, status)` and compare against the expectation table:

| Checkpoint | Pkg1 expected | Pkg2 expected |
|---|---|---|
| at `b1b9edc` (baseline) | 272 FAIL / 951 | FCT 400 |
| after VTF-359 | unchanged | FCT 200; validate 400 (`AD01A`) |
| after VTF-361 | unchanged | validate 200; ~197 FAIL |
| after VTF-362 | **4 FAIL** (C1008/C1020 real findings) | **≈3 FAIL** (S2 `C0684`, S3 `C0671`/`C0685` pending verdicts) |
| after VTF-374 | + new CFG_CONTROL results for C1022/23/27/29 | n/a (no `/NN` channels) |

## 10. Open items register

| Id | Item | Owner/state |
|---|---|---|
| O1 | VTF-360 design (A degrade / B flag / C accumulate) | user investigating |
| O2 | S2: Pkg2 `C0684` BEHAV_INPUT3 exp=6/act=7 — junction B-side convention | verify after VTF-361 |
| O3 | S3: Pkg2 `C0671`/`C0685` missing `CFG_SUPERVIS_FMA1` members (ID=657/676, from `C1XT`/`C200XT` autoReset) — genuine vs supervisor-modeling edge | un-root-caused |
| O4 | M5 policy: eChc=YES DP without IO-EXB → drop expectation or named finding | decide in VTF-362 |
| O5 | Upload-error opacity ("FCT archive is invalid", reason log-only) — FE contract change? | fold into VTF-360 scope decision |
| O6 | Filled Data-Transmission alpha package needed to exercise CFG_DATA_OUT end-to-end | request from AE team |
