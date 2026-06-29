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
| Backend | **IN PROGRESS** | Core v2 pipeline built and green end-to-end on a feature branch (both parsers, the v2 endpoint + gate, the preprocessor, and the consuming engine). Cluster polish + the UI mismatch-annotation wiring remain. |
| Frontend | **IN PROGRESS** | Well underway. Output console unblocked — the backend response contract + sample are delivered and shared. |

## Requirement Analysis — DONE

The Phase 2 problem is fully scoped. Project-specific validation cross-correlates the delivered `.ADC` files against the design baseline (FCT2 archive + PDQ workbook). The coupled-artifacts rule (validate requires both FCT and PDQ, or neither) is locked, and the validation clusters are identified.

## Planning / Architecture — DONE

The design and the frontend/backend contracts are complete and locked:

- Phase 2 design documented end-to-end (input model, preprocessor, clusters, validate flow).
- Four endpoint contracts finalised and published: FCT upload, PDQ upload, validate v2 request, validate v2 response.
- The PDQ parse model is locked and the PDQ workbook rebuilt to support robust parsing.

## Backend — IN PROGRESS (core engine complete; cluster polish + UI wiring remain)

The full v2 pipeline is built and passing end-to-end (on an **unmerged feature branch**, not yet on `main`): both baseline parsers, the v2 endpoint with the coupled-artifacts gate, the validate-time preprocessor that *derives* the expected baseline from FCT + PDQ, and the consuming engine that validates the ADC files against it. A code-grounded reconciliation then showed that the per-block value validation for most clusters is **already realized** by the preprocessor + engine; what genuinely remains is the named-verdict vocabulary, the per-cell mismatch annotation that drives the UI highlight/tooltip, and the parked items.

| Story | Scope | Status |
|---|---|---|
| BE-01 (VTF-331) | PDQ CQ-IR parser | **DONE** |
| BE-02 (VTF-332) | PDQ Control Table + Data Transmission parser | **DONE** |
| VTF-350 | Parser alignment to the locked PDQ | **DONE** |
| BE-03 (VTF-333) | Baseline FCT parser → ComAebMap | **DONE** |
| BE-04 (VTF-334) | v2 validate endpoint + coupled-artifacts gate | **DONE** |
| BE-05 (VTF-335) | Validate-time preprocessor (derives + gates the baseline; emits scalar + instanced expectations) | **DONE** |
| BE-06 (VTF-336) | Consuming engine (validates ADC files against the expectations) | **DONE** |
| v2 response contract | per-cell mismatch annotation + cell→log navigation; shared with frontend | **DONE** |
| BE-07 (VTF-337) | Rule registry finalisation + the `_expected` detail-cell annotation (the data behind the UI highlights/tooltips) | **NEXT** — the `_expected` annotator is the key build; registry edits (M4) parked on confirmed defaults |
| BE-08–BE-13 (clusters 1, 2, 4a, 4b, 5) | per-cluster validation | **largely realized** by BE-05/06; residuals = named verdicts + a few cluster-specific checks (see reconciliation doc) |
| BE-14 (cluster 6) | Data Transmission | **NOT STARTED** — parked pending AE sign-off on the rules |
| BE-15 (VTF-345) | PDF report | **NOT STARTED** (stretch) |

Reference docs: cluster coverage is detailed in *be05-06-reconciliation.md*; the response shape in *FCVT-v2-Validation-Response-Contract.md*.

## Frontend — IN PROGRESS

Frontend is the most advanced workstream, with items at four different stages.

| Item | Status |
|---|---|
| CFG_SWITCH block update | **DONE** |
| Post-PDQ form freeze | **DONE** |
| FCT / PDQ upload wiring | **IN PROGRESS** — unblocked now that contracts are locked and shared |
| Validation output console — Phase 2 scope | **IN PROGRESS** — backend response contract + sample JSON delivered and shared (red highlight, expected/actual tooltip, click-to-log navigation) |
| Prototype (requested by Max) | **NOT STARTED** |

---

*For the full breakdown of activities behind each part, see* ***FCVT Phase 2 — Detailed Activity Log***.
