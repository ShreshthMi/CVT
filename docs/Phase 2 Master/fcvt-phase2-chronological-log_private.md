# FCVT Phase 2 — Chronological Work Log (Private)

Personal record of the Phase 2 effort, in the order work actually happened, including rework and dead-ends. Not for stakeholder circulation — this is the honest "how it really went" log, including the loops that the polished trackers omit.

---

## Roadmap and trackers

- Rebuilt the open-task list from scratch into ~31–32 items, organised into five threads: Contract-FE, Agreed-Output-AE, Documentation, Jira-Backlog, and a private PDQ thread (routed via Suresh).
- Produced two Confluence trackers: a private master tracker (all five threads) and a PO-facing subset (threads 1–4, internal mechanics abstracted).
- Decided threads 1 and 2 run in parallel; the PDQ thread stays private off the PO tracker.

## Contract-FE thread

- **FCT upload contract** — drafted; `POST /api/upload/fct`, ComAebMap, two error codes (`FCT_TAMPERED`, `FCT_INCOMPLETE_BASELINE`).
- **PDQ upload contract** — evolved across several revisions as decisions landed:
  - v1.0 initial.
  - v1.1 — dropped `RSR_TYPE` and `TYPE_PRTCT_CODE` from `cqIrParameters` (sourced from tpf on v2). Required regenerating the `UploadPDQResponse` sample to match.
  - v1.2 — replaced the bracket-scanning parse model with the Config Key column model; added colon-split + both-sides trim, ` & ` arrays, IDENTIFICATION `to` special case, CFG_TIMEOUT nesting + step-divide + pad-to-8; Remarks never read; PDQ rows 1.08/1.09.
  - v1.3 — restructured `cqIrParameters` to block-grouped (mirrors Phase 1 userInput); added CFG_PROJECT_AEB/COM project blocks, the version-aware group, and the full numeric-transform table.
  - v1.3 body further extended in place (no version bump) to add CFG_SWITCH, the two CFG_SUPERVIS_FMA blocks, CFG_IP_SWITCH_TIME, and the FCT-derived CFG_IP_SWITCH exclusion note.
- **Validate v2 request contract** — drafted, then revised:
  - Initial draft assumed userInput carried both upload responses plus form keys.
  - Reworked when the decision landed that v2 userInput is upload-sourced only (form frozen); then again to fold in the tpf-sourced keys and drop the overlapping keys.
  - v1.1 — marked the version-aware group resolved (PDQ row 1.09), fixed the overlap-rule tense, recorded the three validate-time rules as out-of-scope on the preprocessor side.
- **Validate v2 response contract** — drafted against the real Phase 1 result shape; locked the validation-atom model (detail-table cell), the conditional `_expected` sibling on mismatch (no per-cell status flag), array-cell full-expected-on-mismatch, and the seven-field `validation_results[]` rows.
- **Jira contract stories** — created as VTF-328 (FCT), VTF-329 (PDQ), VTF-330 (validate v2). This displaced the earlier informal build-story numbering.

## PDQ workbook investigation and rebuild

- Started by walking the actual CQ-IR sheet row by row against the parser rules; catalogued which rows parsed and which silently failed.
- Found the bracket-missing bugs (rows that should parse but lacked a bracket) and a meta-row-with-bracket bug.
- Investigated a suspected hidden-sheet derivation for validation-locked cells; confirmed it was just cell protection, not a hidden-sheet source. (Dead-end ruled out.)
- Surfaced the Reading-B problem: deviations recorded in Remarks rather than Response — a late, uncommunicated parser-breaking assumption.
- Decision point: rather than reverse-engineer AE's fragile format, got direct authority from Dinesh (Operations head) to rebuild the PDQ template for parsing ease.
- Worked through the design principles (Remarks never parsed, units in question text, one declared array separator, etc.), then landed on the **Config Key column** as the clean solution that dissolved most of the format-expressiveness problems at once.
- Evaluated value-format options (`:` separator vs derive-the-label inverted approach); chose normalized `value: label` with first-colon split.
- Settled the array separator (` & `), the IDENTIFICATION `to` special case, and the per-field step table (including correcting my own initial mistakes on PRERESET_ACT_TIME step and the INTERVAL middle value).
- **Rework / dead-ends on the build itself:** first attempted to rebuild the workbook with openpyxl — produced a corrupted file (openpyxl dropped drawings/VML and broke the data-validation/table objects). Second attempt via surgical XML edit-in-place — also corrupted (data-validation ranges and an embedded table object bound to the old layout). After two failed binary attempts, handed the finalized row-by-row build spec to the user, who built the sheet by hand in Excel and applied the validation locks + password protection.
- Investigated opening up TIMEOUT_VALUE per the OP head's concern; concluded the parser was never the constraint and the right lever was a per-project Excel validation list (template practice, no contract/parser change).

## Documentation regeneration

- Regenerated the internal Input Artefacts → PDQ Confluence page end-to-end for Phase 2 (Config Key model, block-grouped output, new blocks, transforms, open-items closed-not-deleted).
- Closed the PDQ Upload Analysis spike: rewrote the forward-looking spike ticket against the current template (mixed-format version framed as discarded), then produced a formal closure docx.

## Project-knowledge sync

- Assessed all project files against the session's decisions; identified five needing updates.
- Updated `fcvt-phase2-design.md` section by section (input model, coupled-artifacts endpoint split, block-grouped response, preprocessor cross-correlation rules, the whole §6 PDQ parse model, open-items closures).
- Rewrote `pdq-sample-data-gaps.md` with status annotations (closed-not-deleted), recast header.
- Updated `fcvt-meeting-and-strategy.md` (standing-context corrections + a dated entry).
- Created `fcvt-phase2-contracts-overview.md` as the companion index to the wiki contracts.
- Confirmed `UploadFCTResponse.json` and the Phase 1 files unchanged; user updated `UploadPDQResponse.json` from the regenerated sample.
- Validated the two v2 payload samples: response sample correct; request sample was the old Phase 1 shape, so regenerated it correctly (fctData + block-grouped pdqData + tpf keys).
- Confirmed `FCVT_handover.md` was intentionally retired in an earlier consolidation, not lost.

## Trackers

- Built a two-page Confluence tracker set for Max: a main progress tracker (four parts — Requirement Analysis, Planning/Architecture, Backend, Frontend — with status + where-we-are) and an interlinked detailed activity log sub-page (same four parts, activity breakdown).
- Created this private chronological log.

---

*Note to self: the rework loops above — the parse-model pivot (bracket-scanning → Config Key), the contract version churn, and especially the two corrupted-workbook attempts before the manual rebuild — are deliberately kept out of the Max-facing trackers but recorded here for an honest account of the effort.*
