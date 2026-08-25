# VTF-337 (BE-07) — `_expected` detail-cell annotator — scope & as-built

> **Status:** DONE & pushed (`origin/VTF-337`, off the BE-06 tip `6c55ca7`), full suite **233 green**.
> Builds the data behind the Phase 2 Validation Output screen: the red-highlight + Expected/Actual
> tooltip on the detail tables, and click-through from a highlighted cell to its validation-result log
> entry. Wire contract: [`FCVT-v2-Validation-Response-Contract.md`](FCVT-v2-Validation-Response-Contract.md);
> backend implications: [`v2-validation-output-contract.md`](v2-validation-output-contract.md) §6.

## 1. Cost analysis → join architecture (the decision)

The hard part is the **verdict→detail-cell join**: a `ValidationResult` carries only
`(fileName, block, entryKey, expected, actual, status)`, while detail rows are display-mapped, deduped,
re-sorted projections keyed by *display* identity. Two architectures were weighed:

- **String-reverse-engineering** off the 7-field result — rejected: it makes the evaluator's internal
  `entryKey` formats (`SLCT_TIMEOUT[ID=1,SECTION=0]`, …) a load-bearing contract and cannot recover the
  counting-head ch/i_ch split for a MISSING item.
- **Hybrid (chosen):** the instanced evaluator (owned code) attaches a structured cell coordinate per
  result (`InstancedFinding`); the scalar bucket — produced by the *shared* Phase-1 engine, which must not
  change — is mapped by a small registry. The annotator owns the detail-table layout (column, array index,
  union padding, raw→display).

**ACO comment-dedup conflict:** the response contract said "drop the ACO comment-dedup", but
`IOEXBAcoExtractorService` feeds Phase-1's `generateSummary` too, so dropping it there would break the
locked *Phase-1 byte-unchanged* constraint. Resolved by **annotator-side reconstruction** — the shared
extractor is untouched; the annotator rebuilds the un-deduped block-order ACO view only where it needs
slot alignment.

> **Superseded by VTF-371 (2026-08-25).** Both halves of this reasoning turned out not to hold, and the
> dedup was removed from the shared extractor after all.
>
> The workaround did not survive the case it was chosen for. Rebuilding block *order* in the annotator
> cannot recover a *row* the extractor never created, so a positional finding on a collapsed slot had
> nowhere to land: it was painted onto the surviving row — marking a cell whose value is correct — and
> after that was fixed it simply had no cell at all.
>
> The *Phase-1 byte-unchanged* constraint was also found to be unenforced prose rather than a lock: no
> test exercises `POST /api/config/validate`, there are no golden or snapshot files under `src/test`,
> and CI runs plain `gradle test`. The requirements authority (`VTF-2.0.0-epic.md`) mandates only
> shape-compatibility and that no Phase-1 *rule* is dropped, renamed or reordered — and this change
> moves no rule, because `generateSummary` receives already-computed results and only builds the display
> array, leaving `validation_results[]` untouched.
>
> Measured cost of the dedup before removal, against the captured production response and its FCT:
> 59 ACO cards, 118 `CFG_SECTION_OUT` blocks, but only 113 rows — the 5 cards whose two outputs drive
> the same track section each lost one. See `v2-expectations-contract.md` §5.3 for what shipped.

Scope decision: **full coverage now** (all 8 tables, to the extent each has a faithful display cell).

## 2. As-built

| Piece | What |
|---|---|
| `ValidationResult.id` | response-scoped opaque `r0..rN`, assigned on the v2 path; omitted in Phase 1 (`@JsonInclude(NON_NULL)`) so that response stays byte-identical |
| `MismatchAnnotation` | the `_mismatches[]` element: `{field, index, kind, expected, actual, result_id}`, `kind ∈ {VALUE, UNEXPECTED, MISSING}` |
| `_mismatches` on the 8 detail DTOs | optional list (`@JsonInclude(NON_EMPTY)`); `Annotatable` interface gives the annotator one uniform add-helper |
| `InstancedFinding` | wraps each instanced result with its raw cell coordinate (block / `linkedId` / position / checked entry / raw expected+actual / member-expected). `InstancedExpectationEvaluator.evaluateAnnotated` returns these; `evaluate()` unwraps to the plain list and is byte-identical to BE-06 |
| `MismatchAnnotator` | the post-pass (runs after `generateSummary`, v2 only); mutates the summary in place |
| `ConfigValidationV2Service` | assigns ids over `scalar + instanced`, then calls the annotator |

### Per-block join (instanced)
| Block | Detail table → cell | Notes |
|---|---|---|
| `CFG_ZP_FMA1/2` | `track_section` `ch_*` / `i_ch_*` | array chosen by the head's actual DIR_INV (UNEXPECTED) or expected DIR_INV (MISSING); union-array MISSING pads the 3 parallel arrays; `SLCT_TIMEOUT` → `ch_slct_timeout` via the timeout transform |
| `CFG_SUPERVIS_FMA1/2` | `supervisor` `logic_type` / `time_out` | member index by `(ID,SECTION)` against `sup_by_ts_dp_id`+`sup_by_ts_fma` (this also disambiguates the FMA1/FMA2 rows, which share `dp_id`) |
| `CFG_CONTROL` | `chc` `_1`/`_2` slots | slot by `(ID,SECTION)`; fixed 2-slot layout (no union array) |
| `CFG_SECTION_OUT` (positional) | `ioexb_aco` row | row by reconstructed block-order comment; `SECTION`→`fma_1_2` (+1), `SLCT_TIMEOUT`→`time_out` |
| `CFG_AXCNT.BEHAV_INPUT3` (single) | `ioexb_behaviour.behav_input3` | |
| `CFG_FWRD_ACD` | `ethernet` `fwrd_acd_to_dp_*` | re-resolved by `ForwardingDestinationResolver` to align block order → array index; MISSING appends |
| scalar `CFG_SECTION_OUT` aux | `ioexb_aco` `clr_occ`/`type_aux*`/`aux*` | one result → per-row display compare |

### Raw→display transforms (mirror the extractors)
DP id→name (ID-block comment); `ValueMappingService.mapValue`; `SLCT_TIMEOUT`→`CFG_TIMEOUT[idx]`×10
(`ConfigExtractionUtil.extractTimeoutValue`); `SECTION`+1.

## 3. Results-only (no faithful cell — surfaces in `validation_results` only, by design)
- counting-head `DIR_INV` — the ch/i_ch split axis, not a displayed column;
- ACO `ID` — the `aco_fmaId` is not a displayed column;
- ACO / CHC `MISSING` where the table has no array slot to append to (CHC is fixed 2-slot; an ACO slot
  beyond the configured blocks has no row);
- scalar cross-rules with no detail column (project / RSR / switch / IP-switch-time);
- Data Transmission — no validation results yet (BE-14 parked).

## 4. Tests
- `MismatchAnnotatorTest` (8) reproduces the response-contract sample: counting-head UNEXPECTED+MISSING
  (union arrays), counting-head `SLCT_TIMEOUT` VALUE (timeout display), supervisor `LOGIC_TYPE` VALUE,
  scalar `BEHAV_INPUT3` VALUE, ACO positional VALUE, CHC slot UNEXPECTED, forwarding UNEXPECTED+MISSING
  (re-resolved by index), and a clean-run-adds-nothing case.
- v2 happy-path (`ConfigValidationV2Steps`, `ConfigValidationV2ControllerTest`) asserts every result
  carries a unique `id` and a reconciling baseline leaves rows unannotated.

## 5. Not in this story (carried)
- Registry finalisation / **M4** (the spurious-FAIL on absent optional scalar blocks) — still parked on
  confirmed firmware defaults (chip `task_d9e07982`).
- **Named verdicts** (`ORPHANED` / `FILE NOT FOUND` / `INVALID SCOPE` / `INVALID VALUE`) — BE-08; they
  live on the `validation_results` entry, not the cell tooltip.
- **FE confirmations — RESOLVED (2026-07-01):** (a) rendering a MISSING item as an empty array slot is a
  frontend concern — the backend correctly appends the empty slot + emits the MISSING `_mismatches` entry
  (with `expected` + `result_id`); no backend change. (b) named-verdict placement is deferred to the next
  few stories (BE-08+), on the result-log entry, not the cell tooltip.
