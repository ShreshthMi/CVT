# FCVT Phase 2 — Detailed Activity Log

> **Epic:** VTF 2.0.0
> **Parent / summary view:** *FCVT Phase 2 — Progress Tracker*
> **Purpose:** the breakdown of activities behind each part of the progress tracker.

This page substantiates the four parts on the *FCVT Phase 2 — Progress Tracker*. Each section lists the activities completed or in motion for that part.

## 1. Requirement Analysis — DONE

- Defined the Phase 2 problem: project-specific validation that cross-correlates delivered `.ADC` files against the design baseline (FCT2 archive + PDQ workbook), beyond Phase 1's single-file rule checks.
- Established the three Phase 2 input streams: FCT2 archive, PDQ workbook, and the existing parsed ADC files.
- Locked the coupled-artifacts rule: the v2 validate path requires both FCT and PDQ; with neither, the Phase 1 path is used; only one uploaded is not a valid state.
- Identified the validation clusters (CAN Segment, Track Section, CHC / External CHC, Supervisor, Data Transmission) and their input dependencies.
- Confirmed the carry-over of Phase 1 architectural commitments: stateless design, strict exact-string matching, declarative rules, BDD as the contract.

## 2. Planning / Architecture — DONE

### Design

- Authored the end-to-end Phase 2 design: input model, validate-time preprocessor, Expectations JSON concept, cluster responsibilities, and the v1/v2 endpoint split.
- Defined the validate-time cross-correlation rules: project-block consistency, dual-FMA consistency, and CFG_IP_SWITCH derivation from the FCT.

### Endpoint contracts (all locked and published)

- **FCT upload** — `POST /api/upload/fct`; returns ComAebMap; error codes `FCT_TAMPERED`, `FCT_INCOMPLETE_BASELINE`. (Jira: VTF-328)
- **PDQ upload** — `POST /api/upload/pdq`; block-grouped `cqIrParameters`, Config Key parse model, single error code `PDQ_INVALID`. Finalised at v1.3. (Jira: VTF-329)
- **Validate v2 — request** — `POST /api/config/v2/validate`; upload-sourced `userInput` (fctData, pdqData, optional tpf keys); coupled-artifacts gate. (Jira: VTF-330)
- **Validate v2 — response** — preserves the Phase 1 result shape; detail-table cells gain a sibling `_expected` key on mismatch; `validation_results[]` carries the per-check verdict. (Jira: VTF-330)

### PDQ parse model and workbook

- Locked the CQ-IR parse model: a dedicated Config Key column as the sole parse-trigger; Response values normalised to bare / `value: label` / ` & `-array / `to`-range forms; per-field step division; INTERVAL enum mapping; TIMEOUT_VALUE nested under CFG_TIMEOUT and padded to 8.
- Defined the block-grouped output shape mirroring the Phase 1 `userInput` structure.
- Resolved the version-aware group source via PDQ row 1.09 (AEB Equipment Version) and the project blocks via row 1.08.
- Rebuilt the PDQ workbook to support the parse model, with validation/password gating to keep the template structure stable.

### Supporting documentation

- Regenerated the internal Input Artefacts → PDQ documentation page for Phase 2.
- Updated the canonical design doc, the PDQ data-gaps tracker, and the meeting/strategy log; created a Phase 2 contracts overview.
- Closed the PDQ Upload Analysis spike with a formal analysis/closure document.

## 3. Backend — IN PROGRESS

Backend build has not started in earnest; the priority is constructing the Jira backlog, off which the build stories sequence.

- **Jira backlog construction** — *IN PROGRESS*. Defining the Phase 2 work packages and story breakdown; this gates the build sequence. The three contract stories (VTF-328 / 329 / 330) are created.
- **Expectations JSON** — *PLANNED*. The internal preprocessor artefact that mirrors the eight detail tables and holds expected values per validation atom.
- **Rule Registry / Phase 2 rule-type vocabulary** — *PLANNED*. Finalises the Phase 2 `ruleType` set as cluster rules are built.
- **A6 — cluster work-package breakdown** — *PLANNED*. Cluster-by-cluster scope as discrete, AE-agreed work packages.
- **Build stories** — *PLANNED*. PDQ parser, FCT parser, cluster validations, error handling; sequenced off the backlog.

## 4. Frontend — IN PROGRESS

The most advanced workstream, with items at four stages.

- **CFG_SWITCH block update** — *DONE*.
- **Post-PDQ form freeze** — *DONE*. Once both uploads succeed, the input form is frozen and values are sourced from the parsed uploads.
- **FCT / PDQ upload wiring** — *IN PROGRESS*. Unblocked now that the upload and validate contracts are locked and shared.
- **Validation output console — Phase 2 scope** — *IN APPROVAL*. Design prepared to accommodate the Phase 2 result shape (detail-table mismatch annotations + verdict rows); awaiting approval.
- **Prototype (requested by Max)** — *NOT STARTED*.

---

*Summary view:* ***FCVT Phase 2 — Progress Tracker***.
