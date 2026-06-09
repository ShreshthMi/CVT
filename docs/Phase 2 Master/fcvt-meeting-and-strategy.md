# FCVT — Meetings and Strategy

Dated log of Max syncs, Salai decisions, Vinod conversations, AE coordination, and the Jira/epic structure for FCVT — plus the career-strategy framing as it specifically connects to FCVT delivery. Companion to `fcvt-codebase.md`, `fcvt-phase1-history.md`, and `fcvt-phase2-design.md`.

**Ordering convention:** dated entries are listed reverse chronological (newest first). Each entry captures what happened at that meeting; the *substance* of any architectural decision lives in `fcvt-phase2-design.md` with a back-reference to the meeting that produced it.

Broader career strategy (Wabtec appraisal freeze, the engineer-vs-architect title gap, Vidhi.app as external portfolio signal) lives in memory, not here. This document only carries the strategy framing where FCVT delivery is the lever.

---

## Standing context

### VTF 2.0.0 epic state

The Phase 2 epic will be formalised as **VTF 2.0.0** in Jira on 2026-05-25 (immediately after this documentation migration completes). Formal delivery target: end of July 2026. Max's informal pressure target: end of June 2026 (see May 7 entry below for the conversation that produced the informal vs formal split).

Child story structure as designed during the May 2026 design freeze:

- **Frontend** — FE-01 (CFG_SWITCH UI, picking up Phase 1's pending CFG_SWITCH item), FE-02 (FCT2 + PDQ upload wiring), FE-03 (post-PDQ form freeze).
- **Backend infrastructure** — BE-01 / BE-02 (PDQ parser, split CQ-IR + ConfigControlTable), BE-03 (FCT2 parser), BE-04 (PDF report generation, stretch goal not on critical path).
- **Backend cluster work** — BE-05 through BE-11, one story per cluster. Exact cluster-to-BE mapping is subject to finalisation in the work-package breakdown to AE.

**Contract stories (created 2026-06).** The three frontend contract stories took concrete keys: **VTF-328** (FCT upload contract), **VTF-329** (PDQ upload contract), **VTF-330** (validate v2 contract, request + response). These keys displace the earlier informal build-story numbering — build stories that had been sketched against VTF-328 onward shift accordingly. See the 2026-06-02 entry below.

Cluster design depth lives in `fcvt-phase2-design.md` §§ 7–10.

### Open AE coordination items

Three actions from the May 7 Max sync, updated as of 2026-06-02:

1. **Formally share Phase 2 input files with Max** and close input with AE. The PDQ document has now been rebuilt and locked (Config Key parse model, block-grouped output); the remaining action is the *formal share* via Max, not further format work. The aim is to lock the cluster-by-cluster validation scope with each rule defined, as discrete work packages with timelines and explicit AE agreement. **Still open (share pending).**
2. **Finalise the cluster-by-cluster validation scope** as discrete work packages. Each cluster becomes a BE story with its own AE-agreed scope, blocks, keys, and timeline. This is the breakdown that will be presented to AE for sign-off. **Still open.**
3. **Finalise the PDQ Excel format** (Control Table + CQ-IR) and the Phase 2 validation report output format. **PDQ Excel format resolved** — workbook rebuilt and locked with validation/password gating (see 2026-06-02 entry). The validation report output format remains to be agreed.

### FCVT delivery as a career lever

FCVT — specifically clean Phase 2 delivery alongside Reset Panel — is the proof case being used to convert the verbal HR role-alignment promise into a written commitment via the MD of Frauscher India. As of the May 7 Max sync, delivery is on track for Max's informal end-June target, which keeps the lever active.

(The broader career frame — appraisal freeze, title-vs-operating-level tension, Vidhi.app as the external-visibility hedge, search-trigger mechanics — is held in memory, not here.)

---

## Dated entries

### 2026-06-02 — Phase 2 contracts drafted, PDQ workbook rebuilt

A working session that produced the Phase 2 frontend/backend contracts and rebuilt the PDQ workbook. Substance lives in `fcvt-phase2-design.md` and `fcvt-phase2-contracts-overview.md`; this entry records that the work happened and the decisions that bear on coordination.

**Contracts.** Four contracts drafted (FCT upload, PDQ upload, validate v2 request, validate v2 response) and the three contract stories created in Jira: VTF-328 (FCT), VTF-329 (PDQ), VTF-330 (validate v2). A companion index, `fcvt-phase2-contracts-overview.md`, was created to sit alongside the wiki contracts.

**PDQ workbook rebuild.** Under direct authority from Dinesh (Operations head), the PDQ template was rebuilt rather than waiting on AE to patch the sample. The CQ-IR sheet now uses a dedicated Config Key column as the sole parse-trigger (replacing bracket-scanning), with Response values normalised (`value: label`, units in the question text, ` & ` arrays) and validation/password gating applied. This self-driven rebuild closed most of the AE-side gaps that had been tracked in `pdq-sample-data-gaps.md`.

**Key decisions (detail in `fcvt-phase2-design.md`).**
- `cqIrParameters` is now block-grouped, mirroring the Phase 1 `userInput` structure (§3.2, §6.4).
- Per-field numeric step-division and the INTERVAL enum mapping locked (§6.3).
- Version-aware group resolved: sourced from PDQ row 1.09 (AEB Equipment Version) — omitted for GS05-and-below, property-file defaults for GS06-and-above. Closes the long-standing version-aware open item.
- PDQ / tpf source split: `RSR_TYPE` and `TYPE_PRTCT_CODE` dropped from PDQ, sourced from the tpf (`/api/upload/translate`) response.
- Three validate-time cross-correlation rules surfaced (project-block consistency, dual-FMA consistency, `CFG_IP_SWITCH` derivation from FCT `redundantComPresent`) — §4.1.

**Coordination bearing.** The PDQ-format action (standing-context AE item 3) is now resolved on the format side; the formal share via Max (item 1) remains the open coordination step.

---

### 2026-05-22 — Vinod design sync

The Phase 2 design was presented to Vinod. The brief consolidated:

- The three-endpoint plan (`/upload/fct` new, `/upload/pdq` new, `/validate` payload-extended).
- The coupled-artifacts locked position (with the failure-mode reasoning).
- The validate-time preprocessor architecture and the source-of-truth rule (ADC = targets only).
- The 6-cluster breakdown and the Cluster 1/2 ownership split on `CFG_SECTION_OUT`.
- Explicit out-of-scope items: COM file IP validation deferred, 3G subsumed, trackplan XMLs not parsed, counting-head-output IoExb mode rejected.
- Known open items (PDQ response shape, engine composite-key lookup mechanism, 8 root filenames + 2 folder names, Spring Boot 4.0.2 multipart defaults).

The substance of the design is in `fcvt-phase2-design.md`; the value of this entry is recording that Vinod has been briefed on it as the current architectural position.

---

### 2026-05-07 — Max weekly sync

**Topic.** Phase 2 progress recap, Ashutosh's frontend requirements, requirement-analysis status.

**What happened.** Walked Max through the Phase 2 design as it stood, including the coupled-artifacts decision (locked) and the cluster breakdown. Ashutosh's frontend requirements were covered alongside backend progress. Max gave an **informal soft target of end-June 2026**, against the formal target of end-July. The implication: delivery is expected to be drivable to end-June even if the formal commitment is a month later.

**Decisions / lock-ins (cross-referenced to `fcvt-phase2-design.md`).**

- Coupled-artifacts position confirmed as locked (design.md §2).
- Cluster structure stays at 6 clusters (design.md §6).
- The validate-time preprocessor architecture stands (design.md §4).

**Actions.**

1. Formally share Phase 2 input files with Max and close input with AE. PDQ document not yet shared.
2. Finalise Phase 2 validation scope cluster-by-cluster, with each rule defined, as discrete work packages with timelines and AE agreement.
3. Finalise the PDQ Excel format (Control Table + CQ-IR) and the Phase 2 validation report output format.

These three actions are tracked in the standing-context section above and remain open.

---

### 2026-04-02 — Phase 1 release meeting

Phase 1 of FCVT was released as **v1.0.0** at this meeting. Action items produced:

- Deploy and create the Jira release task.
- Send release email; create Coffee post (internal celebration).
- Participant list to be sourced from Salai and the Release channel.
- Close the Idea Hatchery post — FCVT graduates from idea-tracking.
- Update technical documentation for the rule engine and the duplicate-value registry.
- How-to-Use Guide approval from Shekhar and Max, then promote to the Frauscher Knowledge Base.

Deferred to backlog at this meeting:

- Remove the `OptionalInputMatch` rule type (still implemented but unconfigured — codebase.md §7).
- Add `DefaultValue` validation in `RuleConfig`. This deferred item subsequently landed as VTF-299 (see phase1-history.md §4).
- Update test cases.

The full Phase 1 narrative leading into this release is in `fcvt-phase1-history.md` §§ 1–7.

---

### 2026-03 — Career strategy session (FCVT-delivery framing only)

The session in March 2026 surfaced FCVT (and Reset Panel) as the **internal proof case** for converting the Wabtec verbal HR commitment into a written one via the MD of Frauscher India. The framing:

- Deliver Phase 2 cleanly. Same for Reset Panel.
- Use clean delivery as the basis for the MD conversation on written role alignment.

Vidhi.app's role in the strategy (as external portfolio signal) and the broader search-trigger mechanics are held in memory because they stand on their own outside FCVT delivery. The FCVT-delivery lever — what FCVT specifically buys in the internal conversation — is what's captured here.

---

### 2026-03-20 — Phase 2 requirements meeting

The substantive Phase 2 requirements meeting. Key threads:

**On the CAN Segment cluster (now Cluster 1 in the §6 design table).** Suresh's input: infer required structure from folder structure (missing links, forwarding, timeout info). The conversation opened the still-open question (Max): whether to **suppress** missing files silently or **highlight** them. Not resolved at the meeting.

**On input-dependent validation as a category.** Max's positions:

- Session management is needed (some way of tying user uploads into a coherent validation context).
- No new input template should be invented — Phase 2 reuses what AE already produces (FCT2 archive + PDQ workbook). This later became the locked input-stream choice in `fcvt-phase2-design.md` §1.
- **Verification ≠ validation.** The tool must not be misframed as doing verification. (This terminological distinction lines up with the Phase 1 rebuttal framing — FCVT is firmly in the verification camp as a designer-side preflight, not in the validation-of-baselines camp. See phase1-history.md §5.)

**Open Salai decision.** Between two approaches for how user-supplied input integrates with AE control tables:

- **(A)** Validate against an AE-supplied control table (the path that eventually became the PDQ ConfigControlTable approach).
- **(B)** UI row-by-row walkthrough per subsheet, logged in the Validation_Result Summary Excel with signature.

(A) is the path that Phase 2 has converged on; (B) remains as the fallback / manual-review framing for projects without proper PDQ — see `fcvt-phase2-design.md` §2 on the coupled-artifacts position.

**Follow-ups scheduled at this meeting:** Feedback meeting the following Monday + Requirements Meeting Part 2.

---

*End of meetings and strategy log. Append new dated entries above the existing ones (reverse chronological).*
