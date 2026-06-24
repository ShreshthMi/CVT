# VTF-336 (BE-06) — Validate-time consuming engine: as-built

> **Status:** consumption engine COMPLETE 2026-06-24. Branch `VTF-336` off `682df1c` (the VTF-335-M5 / BE-05 tip), pushed to `origin/VTF-336`, full suite green (225). Authoritative semantics live in [`v2-expectations-contract.md`](v2-expectations-contract.md) §6 / §5.6; this is the implementation record.

## 1. What this story is
Fill the `ConfigValidationV2Service.validate` seam (the `results = List.of()` TODO) so the v2 path evaluates parsed ADC files against the `Expectations` buckets BE-05 emits, producing real `ValidationResult`s, then assembles the summary via `SummaryService.generateSummary`.

## 2. Scope
**In:** scalar consumption, instanced consumption (SINGLE / BY_IDENTITY set-equality / POSITIONAL), `CFG_FWRD_ACD` socket→COM/IP resolution (§5.6), tests.
**Out / deferred:** the M4 shared-registry edits — deferred again to a follow-up (firmware `DefaultValue`s unconfirmed; real ADCs contradict the old note). DT (`CFG_DATA_*`) still parked.

## 3. Design decisions (confirmed at kickoff)
- **Occurrence selection = Option B** (raw `ConfigBlock` entries), not the SummaryService extractors. The extractors are lossy for validation: ACO dedups by header comment (drops the filler the POSITIONAL check must catch) and all extractors display-map values via `ValueMappingService`. Option B reads raw `ID`/`SECTION`/… entries, matching the scalar engine.
- **BY_IDENTITY set-equality is a dedicated evaluator**, not a registry `RuleType` — the `ValidationKey=(block,entry)` + single-flattened-payload dispatch has no per-occurrence identity. The stock `MultipleBlockMultipleInputMatchRule` is `actual ⊆ expected` (subset-only), so set-equality is new.
- **Extra occurrence → FAIL + `UNEXPECTED_OCCURRENCE` sentinel.** `ValidationStatus` is `{PASS, FAIL, INVALID}` — no `FLAG` — so the contract's "extra → FLAG" is surfaced as a FAIL carrying a distinct actual sentinel (no change to the shared status enum / UI / Excel contract).
- **`CFG_AXCNT.BEHAV_INPUT3` supersede needs no registry removal** — the rule short-circuits on absent payload and v2 never supplies its scalar value, so it is dormant in v2 while the instanced path owns the derived 6/7.

## 4. As-built (commits on `VTF-336`)
| Commit | Deliverable |
|---|---|
| `16bbd9f` | Scalar + instanced wired into `ConfigValidationV2Service`. **Wrong-premise fix:** §6's "feed the scalar map straight in, no new code" failed — `DefaultPayloadValidator` enforces `UIInputRequired` + payload types (rejected the PDQ's `String` `BLOCK_EXISTS`). Added `PayloadValidator.resolve()` (resolution without UI-input enforcement) + `ConfigValidationService.validateParsedFiles(files, map, ResolvedPayloadContext)` overload; Phase-1 `validate()` byte-unchanged. New `InstancedExpectationEvaluator` (SINGLE / BY_IDENTITY / POSITIONAL). v2 happy-path asserts real results. |
| `f486365` | `CFG_FWRD_ACD` §5.6 — `ForwardingDestinationResolver` (socket→dest IP via header `socket+32`/`socket+48` → present COM's `CFG_MY_IP_NWx`; both networks must agree, else 400 `PHASE2_BASELINE_INCONSISTENT`); evaluator set-equality over resolved members. C0391 layout re-verified. |
| `6c55ca7` | Parser→evaluator integration test (real ADC text via `CfgParserUtil` → evaluator) closing the hand-built-`ParsedConfigFile` gap. |

## 5. Key files
`service/ConfigValidationV2Service` (seam), `validation/instanced/InstancedExpectationEvaluator` + `ForwardingDestinationResolver` (new), `validation/payload/PayloadValidator` + `DefaultPayloadValidator` (resolve), `service/ConfigValidationService` (overload), `constants/ValidationConstants` (`EXPECTED_OCCURRENCE_NOT_FOUND`, `UNEXPECTED_OCCURRENCE`). Tests: `InstancedExpectationEvaluatorTest`, `ForwardingValidationTest`, `ParsedAdcInstancedEvaluatorTest`; updated `ConfigValidationV2Steps` + `v2-validate.feature`.

## 6. Verification
Full suite green at every commit (215 → 223 → 225). Instanced correctness via crafted unit tests (all three modes + set-equality missing/extra + the four §5.6 400s + parser integration). The v2 end-to-end happy-path produces real scalar results (the inline ADC has no COM/instanced blocks matching the fixture ids, so instanced is covered by the unit tests).

## 7. Follow-ups
- **M4 registry edits** (spawned task) — add the rules once firmware `DefaultValue`s are confirmed.
- **DT** (`CFG_DATA_*`) — still parked (AE).
- Optional: an end-to-end instanced fixture (crafted ADCs matching `fct-phase2-aco` ids) for a full-stack instanced demonstration.
