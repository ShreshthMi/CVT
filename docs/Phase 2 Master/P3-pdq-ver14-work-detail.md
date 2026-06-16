# P3 — PDQ Parser Alignment to the Frozen PDQ Ver14 Workbook

*Work package "P3" from the preprocessor-prerequisites list. Completed 2026-06-16. This document captures the source, the deltas, every decision (with rationale), and a summary of the codebase changes — independent of git history.*

---

## 1. Summary

The PDQ upload parser was written against an earlier PDQ workbook. P3 re-aligns it to the **frozen** PDQ template, **`FSTI_QSP_AE_PDQ_Ver14`** (held at `docs/samples/phase2/FSTI_QSP_AE_PDQ_Ver14(Not finalised).xlsx`). "Not finalised" refers only to two cosmetic items — the `Configuration Word` column in CQ-IR is not yet hidden, and the structural-protection lock is not yet applied; the **content is the single source of truth**.

The change is shipped as **one squashed commit on `VTF-332`** (the PDQ-parser branch), with `VTF-333` (FCT) and `VTF-334` (v2) rebased on top. Full test suite green (**166 tests**) on the `VTF-334` tip.

---

## 2. Source comparison — Ver14 vs the prior parser

The sheet names and the entire CQ-IR config-word set + value formats are **unchanged**. Four structural changes + two semantic changes were found:

| # | Change in Ver14 | Effect on the old parser |
|---|---|---|
| 1 | **`Sl. No. 1.08` is no longer System Redundancy** (now "DP placement") — the Single/Dual row is **gone** | Old `BLOCK_EXISTS` derivation (Single→true/Dual→false) lost its input |
| 2 | **AEB version (`1.09`) is now a board version `"GS07"`**, not `"GS06 and above"` | `gs06Plus = version.contains("GS06")` → false for GS07 (bug) |
| 3 | **Control table gained a `Logic type` column (H)** = the `OR`/`AND` operator; operands in col G are now comma-separated (no embedded `(or)`/`(and)`). Everything shifts right: `autoResetByTimer` H→**I**, empty boundary I→**J**, DP table J/K/L/M→**K/L/M/N** | `fadcAutoReset` parsed the operator from one cell; columns mis-mapped |
| 4 | **DT sheet carries a footer note** (`*Note : This sheet will be fillied by AE team` in `A17`) | Parser treated the note row as a data row → threw on the empty numeric cells |
| 5 | `RSR_TYPE` present in CQ-IR (row 44) | Was intentionally dropped from the response (contract v1.1) — **re-introduced** (decision below) |
| 6 | Project number provided differently | `BLOCK_EXISTS`/`PROJECT_NUMBER` re-sourced (decision below) |

CQ-IR value sanity: all 31 config words map to the parser's canonical `TEMPLATE`, and every value normalizes with the existing `pdq-mappings.properties` (`INTERVAL 80→2`, `TIMEOUT_VALUE "340 & 610"→[34,61,0…]`, `SWITCH_GE 2600→26`, `IDENTIFICATION "1 to 4095"→{min,max}`, etc.).

---

## 3. Decisions (with rationale)

1. **`BLOCK_EXISTS` / `PROJECT_NUMBER` now come from the CQ-IR `PROJECT_NUMBER` row (Sl. No. 44).** Since Ver14 removed the System Redundancy row, block existence is driven by that row's **Response** + **Remarks**:
   - Response leading token **`YES`** → `BLOCK_EXISTS = "true"`, and `PROJECT_NUMBER` = the **Remarks** column value (blank Remarks → `"0"`).
   - Response **`NO`** → `BLOCK_EXISTS = "false"`; the Remarks cell **must be empty**, else the PDQ is rejected (`PDQ_INVALID`).
   - Response neither YES/NO, or the row missing → `PDQ_INVALID`.
   - **`BLOCK_EXISTS` value format kept as `"true"`/`"false"`** (not `"1"`/`"0"`) — user decision, preserves the prior contract value.
   - The top-level `projectCode` field is unchanged (still the PDQ-sheet "Project Code" label) — only `PROJECT_NUMBER` reads Remarks. In the Ver14 sample both happen to be `"0"`.

2. **`gs06Plus` is now numeric.** Extract the `GSnn` number and test `>= 6` (handles `GS07`, `GS08`, …). Side effect: GS07 ⇒ the version-aware keys (`TYPE_AUX1/2`, `TYPE_IN1/2/3`, `SUPERVIS_COUNT_LMT`) are now correctly **emitted** (`versionDefault` "0").

3. **Control table — operator moved to the `Logic type` column.** `fadcAutoReset` now reads operands from col G (comma-split) and the operator from col H. `NA`/blank operands → `null`; a single operand with no operator → `{op:null, operands:[…]}`; a `Logic type` other than `OR`/`AND` → rejected (`UNSUPPORTED_LOGIC_TYPE`).

4. **DT footer note skipped.** Rows whose `DP NAME` / `SOURCE DP NAME` start with `*` are skipped, so Ver14's empty-DT-plus-note sheet correctly yields `dataTransmission: null`.

5. **`RSR_TYPE` re-introduced into the response** as block **`CFG_RSR_TYPE` → `RSR_TYPE`** (`"1: RSR 180"` → `"1"`), PDQ-sourced — reverses the contract-v1.1 drop. (Canonical block name confirmed in `FCVT_contract_changes.md` / `fcvt-codebase.md`.) `TYPE_PRTCT_CODE` stays a skipped meta row.

6. **`RSR_TYPE` is dual-sourced on v2, with a preprocessor cross-check.** It now appears in both `pdqData` (`cqIrParameters.CFG_RSR_TYPE`) and the tpf optional key on v2. At preprocess time the two `RSR_TYPE` values **must match**, else validation stops with a baseline error (`HTTP 400`). This is now the **fourth** validate-time cross-correlation rule (design §4.1) — **design-only until BE-05 implements it**; the stub does not enforce it yet.

7. **Fixture strategy:** `pdq-phase2-sample.xlsx` was replaced in place with the raw Ver14 workbook (empty DT); `pdq-phase2-sample-dtio.xlsx` was rebuilt as Ver14 + a populated DT sheet (2 safety + 2 output rows). Keeping the filenames stable avoided rewiring `PdqFixtures` and all the tests.

8. **Landing strategy:** the whole change landed as **one squashed commit on `VTF-332`** (user's choice over per-branch splitting), with `VTF-333`/`VTF-334` rebased on top (conflict-free — no FCT/v2 file touches a PDQ-parser file).

---

## 4. Codebase changes (17 files)

**New**
- `service/pdq/ProjectBlockResolver.java` — scans CQ-IR for the `PROJECT_NUMBER` row by header labels (Configuration Word / Response / **Remarks**), computes `{blockExists, projectNumber}` per decision #1.
- `service/pdq/PdqVer14ParsingTest.java` — comprehensive end-to-end parse of the Ver14 fixture (header, version-aware keys, project block, control table incl. Logic-type operator, DP table cols K–N, DT null, `CFG_RSR_TYPE`).

**Main — modified**
- `PdqSheetHeaderParser.java` — numeric `gs06Plus` (`GS(\d+) >= 6`); System Redundancy read removed.
- `PdqHeader.java` — `blockExists` field dropped (record is now `projectCode, aebEquipmentVersion, gs06Plus`).
- `PdqParsingService.java` — wires `ProjectBlockResolver`; project blocks built from it.
- `PdqWorkbookContract.java` — `+cqirRemarksHeader()`, `−systemRedundancySlNo()`.
- `ControlTableParser.java` — `fadcAutoReset(operands, operator)` rewrite; `(or)`/`(and)` token logic + `splitOperator` removed; reads the new `logicType` column.
- `DataTransmissionParser.java` — skip `*`-prefixed footer rows.
- `CqIrSheetParser.java` — `TEMPLATE` gains `CFG_RSR_TYPE → RSR_TYPE` (after `CFG_IP_SWITCH_TIME`, before the project blocks).
- `exception/PdqInvalidReason.java` — `+UNSUPPORTED_LOGIC_TYPE`, `+PROJECT_BLOCK_INVALID`.
- `resources/pdq-workbook.properties` — `cqir.header.remarks`; control-table column map A–I / K–N (new `logicType=H`, `autoResetByTimer=I`, DP table K–N); System Redundancy Sl.No removed.

**Tests / fixtures — modified**
- `CqIrSheetParserTest.java` — Ver14 expected values (`OCC_EXT 0`, `INTERVAL 2`, `BEHAV_SIMUL 0`, `RESET_DELAY 10`, `AUX1_NO_NC 0`), `CFG_RSR_TYPE` assertion, updated block order.
- `PdqParsingServiceTest.java` — Ver14 header (`GS07`), project block, version-aware-included; constructor takes the new resolver.
- `controller/PdqUploadControllerTest.java` — `aebEquipmentVersion "GS07"`, `SUPERVIS_COUNT_LMT` now present.
- `cucumber/features/pdq/pdq-upload.feature` — Ver14 values.
- `fixtures/pdq-phase2-sample.xlsx` — replaced with raw Ver14 (empty DT).
- `fixtures/pdq-phase2-sample-dtio.xlsx` — Ver14 + populated DT.

---

## 5. Test & fixture migration

- The old-format fixtures could no longer parse under the Ver14 control-table layout, so Ver14 became the committed fixture. The committed `pdq-phase2-sample.xlsx` blob is byte-identical to the Ver14 source file.
- Migrated expected values in `CqIrSheetParserTest`, `PdqParsingServiceTest`, `PdqUploadControllerTest`, and the cucumber feature to the Ver14 data.
- Result: **166 tests, 0 failures** on `VTF-334` (Phase 1 + PDQ-Ver14 + FCT + v2 + cucumber together). The v2 tests consume the now-Ver14 PDQ fixture and still pass (they assert response structure, not DT-specific values).

---

## 6. Final state

| Branch | Tip | Notes |
|---|---|---|
| `VTF-332` | `42a1021` | PDQ-parser Ver14 commit (17 files) |
| `VTF-333` | `5073f41` | FCT, rebased clean on the new 332 |
| `VTF-334` | `d3516a6` | v2, rebased clean; 166 tests green |

All three pushed to `origin` and in sync. Contract-doc updates (separate) are catalogued in **`P3-contract-doc-updates.md`**.

---

## 7. Follow-ups (out of P3 scope)

- The **`RSR_TYPE` PDQ↔tpf cross-check** is **design-only** until the preprocessor (BE-05) is built; `StubExpectationsPreprocessor` does not enforce it.
- A few pre-Ver14 prose sections in the locked contracts were cleaned up (see `P3-contract-doc-updates.md`); the older `pdq-upload-contract.wiki` baseline was marked **superseded** rather than rewritten.
- `aco_fmaId` (P1) and the FCT-response change (P2) remain open prerequisites — see `preprocessor-prerequisites.md`.
