# FCVT Phase 2 — Progress Tracker

> **Epic:** VTF 2.0.0
> **Formal target:** end of July 2026
> **Audience:** Product / stakeholders
> **Detailed activity log:** see *FCVT Phase 2 — Detailed Activity Log*

## Progress at a glance

| Part | Status | Where we are |
|---|---|---|
| Requirement Analysis | **DONE** | Complete. Input model, coupled-artifacts rule, and cluster scope defined. |
| Planning / Architecture | **DONE** | Complete. Phase 2 design, endpoint contracts, and the PDQ parse model are locked and documented. |
| Backend | **IN PROGRESS** | Early stage. Jira backlog construction is the current priority; build work sequences off the backlog. |
| Frontend | **IN PROGRESS** | Well underway. Two items done, one in progress, one in approval, one not started. |

## Requirement Analysis — DONE

The Phase 2 problem is fully scoped. Project-specific validation cross-correlates the delivered `.ADC` files against the design baseline (FCT2 archive + PDQ workbook). The coupled-artifacts rule (validate requires both FCT and PDQ, or neither) is locked, and the validation clusters are identified.

## Planning / Architecture — DONE

The design and the frontend/backend contracts are complete and locked:

- Phase 2 design documented end-to-end (input model, preprocessor, clusters, validate flow).
- Four endpoint contracts finalised and published: FCT upload, PDQ upload, validate v2 request, validate v2 response.
- The PDQ parse model is locked and the PDQ workbook rebuilt to support robust parsing.

## Backend — IN PROGRESS (early)

Backend build has not yet started in earnest. The current priority is constructing the Jira backlog; the build stories sequence off it.

| Item | Status |
|---|---|
| Jira backlog construction (work packages, story breakdown) | **IN PROGRESS — priority** |
| Expectations JSON (internal preprocessor artefact) design | PLANNED |
| Rule Registry / Phase 2 rule-type vocabulary | PLANNED |
| A6 — cluster work-package breakdown | PLANNED |
| Parser + endpoint + cluster build stories | PLANNED (follow the backlog) |

## Frontend — IN PROGRESS

Frontend is the most advanced workstream, with items at four different stages.

| Item | Status |
|---|---|
| CFG_SWITCH block update | **DONE** |
| Post-PDQ form freeze | **DONE** |
| FCT / PDQ upload wiring | **IN PROGRESS** — unblocked now that contracts are locked and shared |
| Validation output console — Phase 2 scope | **IN APPROVAL** — design prepared |
| Prototype (requested by Max) | **NOT STARTED** |

---

*For the full breakdown of activities behind each part, see* ***FCVT Phase 2 — Detailed Activity Log***.
