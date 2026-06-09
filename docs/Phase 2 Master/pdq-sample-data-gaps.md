# PDQ Sample Data Gaps — Parsing-critical items only

Items that affect parser correctness. Operational and cosmetic items have been excluded.

Source sample: PDQ.xlsx as supplied (May 2026).

---

**Status note (post-rebuild).** The PDQ workbook has since been rebuilt to make parsing robust. The CQ-IR parse model changed: row identification is now driven by a dedicated **Config Key** column (non-empty → parsed; empty → meta row, skipped), not by bracketed tokens in the question text. Response values were normalised to a `value: label` form with units moved into the question text, arrays separated by ` & `, and validation/password gating applied so the template structure stays stable. As a result, most items below — particularly the bracket-addition items in Section A — are now **Resolved**: the brackets are no longer the parse mechanism, so they no longer need adding. Items are annotated with their current status and kept in place (closed rather than deleted) for traceability. Genuinely open items remain marked **Open**.

---

# PART 1 — Items for AE

Changes to the PDQ workbook, format conventions to confirm, and information AE needs to provide.

## A. CQ-IR rows — originally bracket additions (now superseded by the Config Key column)

The original parser only extracted rows whose question text contained a bracketed all-caps token. That model is superseded: the rebuilt sheet uses a dedicated Config Key column as the sole parse-trigger, so the bracket-addition items below are obsolete.

**A1.** Row "Range of ID for AEB & COM" → `IDENTIFICATION`. **Resolved** — carried in the Config Key column; value `1 to 4095` (special-case `to`-split → `{min, max}`).

**A2.** Row "RESET_IN" → `RESET_IN`. **Resolved** — carried in the Config Key column; no bracket needed.

**A3.** Row "type-specific protection code" → was to append `(TYPE_PRTCT_CODE)`. **Resolved (obsolete)** — `TYPE_PRTCT_CODE` is now sourced from the tpf (`/api/upload/translate`) response, not PDQ. Its CQ-IR row is a meta row (empty Config Key) and is intentionally not parsed.

**A4.** Row "Transmission interval" → `INTERVAL`. **Resolved** — carried in the Config Key column; parsed via enum mapping (`10→0, 40→1, 80→2, 160→3`).

**A5.** Row "Selection time out" → `TIMEOUT_VALUE`. **Resolved** — carried in the Config Key column as `TIMEOUT_VALUE`, nested under the `CFG_TIMEOUT` block, step-divided and padded to 8.

## B. CQ-IR response format

**B1.** Row "Selection time out" response `340 & 610 Sec` — unit was wrong, should be `ms`. **Resolved** — units removed from all Response cells and moved into the question text; the value cell now holds the bare `340 & 610`. Unit-correctness is no longer a per-cell concern.

## C. PDQ sheet additions

**C1.** PDQ row 1.08 — "System Redundancy?" with response `Single` or `Dual`. **Resolved** — added to the rebuilt template. Drives `CFG_PROJECT_AEB.BLOCK_EXISTS` / `CFG_PROJECT_COM.BLOCK_EXISTS` (`Single` → `"true"`, `Dual` → `"false"`).

**C2.** Project Code field in PDQ sheet header is empty in the sample. **Open** — a populated sample is still needed for end-to-end testing. `projectCode` also feeds `PROJECT_NUMBER` in both project blocks.

**C3.** Confirm exact spelling/casing for the row 1.08 response. **Resolved** — fixed via the template's single-select validation list (`Single` / `Dual`), eliminating free-text casing drift and the overlap with row 1.01 (`Station - Centralised`).

**C4.** PDQ row 1.09 — "AEB Equipment Version?" (e.g. `GS05 and below`, `GS06 and above`). **Resolved (new)** — added to the rebuilt template. Governs the version-aware key group: omitted for GS05-and-below; property-file defaults for GS06-and-above. Resolves the previously-open version-aware group source.

## D. Control table — assumed format conventions (locked)

The parser operates under the following assumptions. These are final and unchanged by the rebuild.

**D1.** Comma is the only delimiter for multi-DP cells. Sample row S.No=3 has `DP4,DP5`. Parser splits on `,`. No other delimiters are recognised. **Locked.**

**D2.** "Reset Type" column values are from a fixed catalog. Parser validates against the catalog; values outside it are rejected. **Open** — full catalog enumeration still pending from AE; carried as a Control table validation list.

**D3.** Sub-table boundary is the empty column I, separating track-section sub-table (cols A–H) and DP sub-table (cols J–M). Column I will never contain data. **Locked.**

**D4.** "POSITION" values in the DP sub-table are restricted to `ABOVE THE RAIL` and `BELOW THE RAIL`. No other values are accepted. **Locked.**

## E. Data Transmission Inputs sheet

**E1.** Sheet is completely empty in the sample (headers only). **Open** — a populated example covering all column shapes and edge cases is still needed.

**E2.** Absence contract: sheet absent → no DT for project (valid); sheet present + empty → reject as malformed. **Resolved (locked)** — contract confirmed; documented in `fcvt-phase2-design.md` §6.6.

---

# PART 2 — Items for FCVT Backend

MappingProperties file updates and parser implementation decisions.

## I. MappingProperties additions

**I1.** `IP_SWITCH_TIME` — step `1` (s). **Resolved** — step locked; carried in `CFG_IP_SWITCH_TIME` block.

**I2.** `RESET_DELAY` — step `1` (s). **Resolved** — step locked; carried in both `CFG_SUPERVIS_FMA1` and `CFG_SUPERVIS_FMA2`.

**I3.** `SWITCH_GE` — step `100` (ms). **Resolved** — step locked; carried in `CFG_SWITCH`.

**I4.** `SWITCH_GSF` — step `100` (ms). **Resolved** — step locked; carried in `CFG_SWITCH`.

**I5.** `PRERESET_ACT_TIME` — step `10` (s). **Resolved** — step locked; carried in `CFG_SWITCH`.

**I6.** `IDENTIFICATION.min=1`, `.max=4095`, `.step=1`. **Resolved** — locked.

**I7.** `CFG_TIMEOUT` resolution against MappingProperties. **Resolved** — `TIMEOUT_VALUE` always nests under the `CFG_TIMEOUT` block (hardcoded special case); step `10` (ms), padded to 8.

**I8.** Format-pattern entry for the protection code key. **Resolved (obsolete)** — `TYPE_PRTCT_CODE` is now tpf-sourced, not PDQ; no PDQ-side hex pattern needed.

**I9.** `NMBR_OUT` entry — valid type and range. **Open** — still to be added (DT-sheet `outputDataTransmission` field).

**I10.** Verify whether the current MappingProperties file is complete or partial against the full set of CQ-IR keys expected in production. **Open** — completeness verification still outstanding.

## J. Parser implementation decisions

**J1.** Control table "FAdC - FAdC Auto reset" column structured values. **Resolved** — parsed into a structured logic tree (`op` = OR/AND plus operands list), single operator per cell, up to 8 operands; mixed-operator cells rejected.

**J2.** Control table E-CHC and "Auto reset by timer circuit" columns (`YES`/`NO`). **Resolved** — converted to boolean `true`/`false` in the response JSON.

**J3.** DT sheet `SAFE_OUT_FDBCK_QUAD` value representation. **Resolved** — numeric `0`/`1` in the response JSON (not boolean).

---

# Open items summary

The following remain genuinely open after the rebuild:

- **C2** — populated Project Code in a sample PDQ, for end-to-end testing.
- **D2** — full Reset Type catalog enumeration from AE.
- **E1** — populated Data Transmission Inputs sample.
- **I9** — `NMBR_OUT` MappingProperties entry.
- **I10** — MappingProperties completeness verification against the full production CQ-IR key set.

Everything else is Resolved as a result of the workbook rebuild and the parse-model change.
