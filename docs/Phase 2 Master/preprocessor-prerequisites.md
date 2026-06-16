# Preprocessor Prerequisites (BE-05 / VTF-335)

*Captured 2026-06-16. Basis: a code + docs investigation across the FCT, PDQ, preprocessor, and SummaryService subsystems (against the `VTF-334` tip), plus the decisions taken since. This is the "settle these before you plan the preprocessor" checklist.*

---

## 1. Context

The **`ExpectationsPreprocessor`** (BE-05, branch `VTF-335`) is a validate-time, in-memory, stateless service. On each `POST /api/config/v2/validate` it is built fresh from:

- **`fctData`** — the FCT `ComAebMap` (from `POST /api/upload/fct`), and
- **`pdqData`** — the parsed PDQ response (from `POST /api/upload/pdq`),

and emits an **expectations JSON** that the engine uses to look up expected key/value pairs and compare them against the **SummaryService** output (the parsed `.ADC` files under test).

**Source-of-truth rule:** ADC files are validation *targets* only. Every expectation is derived from the FCT + PDQ baseline — never from the artifact being validated.

**Current state of the seam:** present on `VTF-334` as the `ExpectationsPreprocessor` interface + `StubExpectationsPreprocessor` (returns an empty `Expectations`). The real cross-correlation logic is BE-05 and is **not yet implemented**.

---

## 2. The design pivot — expectations no longer mirror SummaryService

The earlier design had the expectations JSON **mirror SummaryService's 8 detail tables**. **This is reversed.** The expectations JSON will **not** replicate the SummaryService structure.

Instead:

- Each expectation carries **identifying keys** (e.g. `dp_id`, an FMA id, `ts_name`, `com`, `position`/`source_dp_id`) plus the field + expected value.
- A **`SummaryLookupService`** (engine side, BE-06) takes `(Expectation + ValidationSummary)`, finds the matching summary row by those identifiers, and returns the actual value to compare.

This decouples expectations from any SummaryService refactor (new fields, split arrays, re-nesting). Note: the existing `VTF-334` `Expectation` record is already a **flat** `(fileName, block, instance, entryKey, expectedValue)` — closer to this new direction than the old "mirror the tables" idea. The design docs that still say "mirror the 8 tables" (`fcvt-phase2-design.md §2/§4.1`, `fcvt-phase2-preprocessor-context.md §2`) need updating to match this decision.

---

## 3. Gating prerequisites (the real blockers)

| # | Prerequisite | Owner | Status |
|---|---|---|---|
| **P1** | **`aco_fmaId` on SummaryService `aco_ioexb`** | you (source) → code | OPEN |
| **P2** | **FCT response (`ComAebMap`) modifications** | you (specify) | OPEN |
| **P3** | **Frozen PDQ document → PDQ parser update** | done | ✅ **RESOLVED** |
| **P4** | **Final Expectations contract (the pivot)** | you (decide) → docs + code | OPEN |

### P1 — `aco_fmaId` on SummaryService `aco_ioexb`
- Add `@JsonProperty("aco_fmaId")` to `model/IOEXBAcoDetail` and populate it in `service/extractors/IOEXBAcoExtractorService` (emit **raw**, no `ValueMappingService` mapping).
- `aco_ioexb` is extracted from **PDQ `CFG_SECTION_OUT`** blocks; today `aco_fma1` = the block comment (e.g. `"202BT"`), `fma_1_2` = `SECTION + 1` (`"1"`/`"2"`). **No numeric FMA id is read today.**
- **OPEN DECISION:** the exact source + semantics of `aco_fmaId`. The example value `"10"` matches **neither** `fma_1_2` (1/2) **nor** the FCT `AcoIoExb.outputFma1Id` (0/1) — so the source (PDQ-config? ADC? FCT-resolved id?) must be specified.
- Likely the **lookup join key** for ACO rows under the non-mirroring design, and probably the resolution of the long-standing ACO card-pairing / `aco_fma2` blocker (`preprocessor-context §6.6`).

### P2 — FCT response (`ComAebMap`) modifications
- Anticipated but not yet specified. The preprocessor consumes `AcoIoExb.outputFma1Id/2Id`, `EvaluatedFma.fmaId`, `FctAeb.dpId`, `FctCom.comId`, `redundantComPresent`.
- **OPEN:** specify the field add/remove/rename so the deserialization + FMA-resolution impact can be assessed. (If the change surfaces an FMA id, it ties into P1.)

### P3 — Frozen PDQ document → PDQ parser update ✅ RESOLVED
- Done in this work package — see **`P3-pdq-ver14-work-detail.md`**. The PDQ parser now reads the frozen **Ver14** workbook (numeric `GS07` version, `BLOCK_EXISTS`/`PROJECT_NUMBER` from the CQ-IR `PROJECT_NUMBER` row, control-table Logic-type column, DT note-skip, `RSR_TYPE` → `CFG_RSR_TYPE`). Shipped on `VTF-332/333/334`.

### P4 — Final Expectations contract (the pivot)
- Because expectations look up by key/value **without** mirroring SummaryService, decide the `Expectation` shape: keep flat `(file, block, instance, entry, value)` or re-key to `(detail-table, row-identifiers, field, value)`; design the `SummaryLookupService` `(Expectation + ValidationSummary) → row + actual`.
- **Doc sync:** update the design docs that still say "mirror the 8 tables".

---

## 4. Pre-existing open questions (carried from `fcvt-phase2-open-questions.md`)

The pivot partly reshapes these; settle them as part of BE-05 planning.

- **Q1 (primary):** Expectations keying / how the "file" is identified — per-entity by `dpId`/`comId` vs scoped + expand in BE-06. Entwined with **P4**.
- **Q2 (critical sub-spec):** Check B forwarding (`CFG_FWRD_ACD`) algorithm — **in BE-05 scope** (decided 2026-06-08): virtual-reference identification, channel-grouping key, the expected forwarding-entry set per channel, socket numbering, and how list-membership expectations coexist with scalar expectations.
- **Q4 (minor):** instance indexing — 0-based or 1-based — lock the convention and apply consistently.
- **Q5:** Project-block consistency check location — engine-time (ADC vs expected) vs preprocessor-time hard error if the PDQ itself is inconsistent.
- **Q3 (BE-06, shapes BE-05 output):** engine lookup mechanism — JSONPath-in-rule-config vs named Java lookups. Pick the direction so the Expectations structure anticipates it.
- **ACO card-pairing** (`preprocessor-context §6.6`): does the ADC encode card grouping (Phase-1 extractor fix) or only the FCT (`acoIoExbs[]`)? User was leaning toward the extractor fix (2026-06-09). Tied to P1 / `aco_fma2`.

---

## 5. SummaryService reference — the 8 detail tables

`ValidationSummary` top-level `@JsonProperty` arrays + the row identifier each exposes (the basis for the non-mirroring lookup):

| Detail table | Per-row identifying key(s) |
|---|---|
| `dp_details` | `dp_can_id` |
| `track_section_details` | `ts_name` + `fma_1_2` |
| `chc_details` | `dp_id` |
| `supervisor_details` | `sup_name` + `dp_id` |
| `ioexb_behaviour_details` | `dp_id` |
| `ioexb_aco_details` | `dp_id` + `fma_1_2` (+ upcoming `aco_fmaId`) |
| `data_transmission_details` | `dp_id` + `position` / `source_dp_id` |
| `ethernet_details` | `com` |

(`validation_results[]` is the post-validation output, not an input to the lookup.)

---

## 6. Cross-correlation rules the preprocessor must apply (design §4.1)

Four consistency rules span files/blocks (specified, **not yet implemented**):

1. **Project-block consistency** — ADC `CFG_PROJECT_AEB`/`CFG_PROJECT_COM` both-absent-or-both-present, same value (the PDQ-derived `PROJECT_NUMBER`).
2. **Dual-FMA consistency** — `RESET_TYPE`/`RESET_DELAY` written identically into `CFG_SUPERVIS_FMA1` and `FMA2`; both ADC values checked against the single source.
3. **`CFG_IP_SWITCH` derivation** — from FCT `redundantComPresent` (`true` → `IP_SWITCH:"1"`; else omitted).
4. **`CFG_RSR_TYPE` consistency (Ver14)** — `RSR_TYPE` is dual-sourced (PDQ `cqIrParameters.CFG_RSR_TYPE` **and** the tpf optional key on v2). The preprocessor must cross-check the two; if both are present and differ, stop validation and emit a baseline error (`HTTP 400`). See `P3-pdq-ver14-work-detail.md`.

---

## 7. Bottom line

**P1–P4 are the gates;** three need your input/decision, and **P3 is now done**. P5–P7-style open questions (Q1–Q5 + ACO card-pairing) are pre-existing and partly reshaped by the pivot. Cleanest unblock order: P2/P1 (settle FCT + `aco_fmaId`) → P4 (Expectations contract) → BE-05 implementation.
