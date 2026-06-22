# FCVT Phase 2 — Open Questions (Preprocessor / Validate Engine)

> ⚠️ **SUPERSEDED (2026-06-20).** Q1–Q5, the cross-correlation-rule questions, and B8/B9/B14 are **resolved** in [`v2-expectations-contract.md`](v2-expectations-contract.md). Only DT (Cluster 6 / B10) remains parked pending AE. Retained for history.

> Working doc for decisions to resolve before/while implementing **BE-05 (VTF-335 preprocessor)** and the
> adjacent **BE-06 (VTF-336 engine composite-key lookup)**. Created 2026-06-08.
> Status tags: **OPEN** (needs a decision) / **DECIDED** / **MINOR**.
>
> Context: BE-04 (v2 endpoint + coupled-artifacts gate) is done & pushed. The `ExpectationsPreprocessor`
> seam exists with a stub returning `Expectations.empty()`. BE-05 fills that seam. Implementation is
> **paused** pending Q1 (and the Q2 sub-spec).

---

## Decided / locked so far

- **BE-05 is emission-only.** The preprocessor turns FCT (`ComAebMap`) + PDQ (`PdqUploadResponse`) into a
  `List<Expectation>`. The engine that *consumes* them (composite-key lookup → PASS/FAIL + the
  `_expected` detail-cell annotations) is **BE-06 (VTF-336)**. No engine changes in BE-05.
- **Source-of-truth:** the preprocessor never reads ADC. All expected values come from FCT + PDQ only
  (ADC files are validation *targets*).
- **The 3 cross-correlation rules** (specs from design §4.1):
  1. **Project-block** — emit `CFG_PROJECT_AEB.PROJECT_NUMBER` + `CFG_PROJECT_COM.PROJECT_NUMBER` from
     PDQ `cqIrParameters` (single source ⇒ AEB/COM stay consistent).
  2. **Dual-FMA** — emit identical `RESET_TYPE` / `RESET_DELAY` into both `CFG_SUPERVIS_FMA1` and
     `CFG_SUPERVIS_FMA2` from one CQ-IR value.
  3. **CFG_IP_SWITCH** — for chains with `redundantComPresent == true`, emit
     `CFG_IP_SWITCH.IP_SWITCH = "1"`; otherwise omit. (Distinct from the PDQ key `CFG_IP_SWITCH_TIME`.)
- **Check B forwarding (CFG_FWRD_ACD channels): IN BE-05 scope** — user decision, 2026-06-08. See **Q2**;
  its detailed algorithm is the blocker.
- **Naming:** no `Phase2` in identifiers (use `V2`); the `PHASE2_INPUTS_INCOMPLETE` error code is a kept
  contract value.

---

## Q1 — Expectations keying / how the "file" is identified   **[OPEN — primary blocker]**

The design keys expectations by `(file, block, instance, entry)`, but the preprocessor never reads ADC,
so it cannot know ADC filenames. Each ADC file's `[IDENTIFICATION] ID` equals an AEB `dpId` (AEB files)
or a COM `comId` (COM files) in the `ComAebMap` — that ID is the natural join key.

**Options**
- **A — Per-entity by ADC id.** Enumerate `ComAebMap`; emit one `Expectation` per AEB (keyed by `dpId`)
  and per COM (keyed by `comId`). The first key field holds the `[IDENTIFICATION] ID` the engine joins on
  at lookup time. Concrete; matches the per-`(file,…)` shape; emits repeated values (one row per entity).
- **B — Scoped expectations.** Emit compact rows tagged with a scope (AEB-wide / COM-wide /
  redundant-chain) and let BE-06 expand to per-file at lookup. Thinner BE-05; pushes expansion into BE-06.

**Implications.** Shapes the `Expectation` record (today
`Expectation(fileName, block, instance, entryKey, expectedValue)`) and the BE-05↔BE-06 contract.
If A, consider renaming `fileName` → `fileId` (it holds the `[IDENTIFICATION] ID`, not a literal filename),
and decide whether the join key is the numeric ID (`dpId`/`comId`) or the dp/com **name**.

**To resolve:** A vs B, and the exact join key.

---

## Q2 — Check B forwarding (CFG_FWRD_ACD) is now in BE-05, but its spec lives in Cluster 1 (BE-08)   **[decision made; sub-spec OPEN]**

You chose to build the Check B forwarding-expectation grouping in BE-05. To implement it I need the spec
the story descriptions currently place under **Cluster 1 (VTF-338 / BE-08)**:

- How **virtual references** are identified (ComAebMap segment membership + PDQ Control Table + DT inputs).
- The **channel grouping key** — `(homeCom, consumingCom)` or `(homeCom, channel)`? — and how the channel
  index is derived.
- The **expected `CFG_FWRD_ACD` entry set** per channel: socket numbering (`socket = header − 32`),
  the NW1-only assumption (design §8.3 #9), and what value each forwarding entry carries (AEB ID?).
- How these **list-membership** expectations coexist with the scalar ones in the same `Expectations`
  structure (Q1's keying applies here too: keyed by homeCom file/id + channel).

**To resolve:** confirm the channel-grouping algorithm + the `CFG_FWRD_ACD` expected-set computation, or
point me to the Confluence/spec section. This is now the larger half of BE-05.

---

## Q3 — B2: engine lookup mechanism (JSONPath vs named Java lookups)   **[OPEN — BE-06, but shapes the output]**

Design **B2** is unresolved: the engine will resolve `(file, block, instance, entry)` either via JSONPath
in rule config or via named Java lookups. BE-05 only emits a `List<Expectation>`, so it is **not blocked**
— but the structure should anticipate BE-06's consumption. Resolve before BE-06 starts.

---

## Q4 — `instance` indexing   **[MINOR]**

Is `instance` 0-based (matches `blockIndex` in `parsedConfigFiles`) or 1-based? For BE-05's 3 scalar rules
the blocks are single-instance, so it's trivial there; it matters for multi-instance blocks in Cluster 1.
Pick a convention now and apply it consistently.

---

## Q5 — Project-block consistency: where does "the check fails" surface?   **[OPEN]**

AC: *"divergent `CFG_PROJECT_AEB` / `CFG_PROJECT_COM` → the check fails."* Since both expectations come from
one PDQ project value, AEB/COM consistency is inherent. Is the failure:
- (a) an **ADC-vs-expected** mismatch evaluated at engine time (BE-06), or
- (b) a **preprocessor-time hard error** if the PDQ itself carries divergent AEB/COM project numbers, or
  inconsistent block presence (one present, one absent)?

Decide where this check lives (and how a preprocessor-time baseline error would be surfaced — exception vs
a validation-result row).

---

## Related (pre-existing design blockers — for awareness, not BE-05-specific)

- **B7** station-layout input (Cluster 3), **B8** CHC keys (Cluster 4a/4b), **B9** Supervisor keys
  (Cluster 5), **B10** DT keys (Cluster 6), **B14** cross-correlation rule implementation. Tracked in the
  design doc; listed here so they're not forgotten when the cluster stories start.
