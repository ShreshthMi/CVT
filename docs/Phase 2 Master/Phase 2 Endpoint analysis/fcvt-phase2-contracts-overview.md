# FCVT Phase 2 — Contracts Overview

Companion index to the Phase 2 frontend/backend contracts authored for VTF 2.0.0. The detailed contracts live as Confluence wiki-markup documents; this file is the map: what each one covers, the decisions that cut across them, and what remains open. Read this first, then the individual contract for depth.

This is distinct from `FCVT_contract_changes.md`, which logs Phase 1 contract changes (VTF-297, VTF-265) and is unrelated to the Phase 2 work below.

---

## The four contracts

| Contract | Endpoint | Jira | Wiki document |
|---|---|---|---|
| FCT upload | `POST /api/upload/fct` | VTF-328 | `fct-upload-contract.wiki` |
| PDQ upload | `POST /api/upload/pdq` | VTF-329 | `pdq-upload-contract` (v1.3, body-extended) |
| Validate v2 — request | `POST /api/config/v2/validate` | VTF-330 | `validate-v2-request-contract` (v1.1) |
| Validate v2 — response | `POST /api/config/v2/validate` | VTF-330 | `validate-v2-response-contract` |

The two validate contracts (request + response) are tracked under a single Jira story (VTF-330).

---

## 1. FCT upload — `POST /api/upload/fct`

Accepts a `.fct2` archive, parses `Project.xml` only, returns the `ComAebMap` shape. Two external error codes: `FCT_TAMPERED` and `FCT_INCOMPLETE_BASELINE` (the counting-head-output IoExb rejection maps to the latter). IDs serialized as strings. Limits: 10 MB file / 15 MB request / 20 MB threshold, 50:1 decompression cap, 50 MB uncompressed cap.

Key field for downstream: `redundantComPresent` (boolean, per COM chain) — consumed at validate-time preprocessing to derive `CFG_IP_SWITCH`.

## 2. PDQ upload — `POST /api/upload/pdq`

Accepts the `.xlsx` PDQ workbook, returns parsed JSON: `projectCode`, `aebEquipmentVersion`, block-grouped `cqIrParameters`, `controlTable`, and conditional `dataTransmission`.

The defining decision: **`cqIrParameters` is block-grouped**, mirroring the Phase 1 `userInput` structure — each key nests under its `CFG_*` block. Parsing is driven by a dedicated **Config Key column** in the CQ-IR sheet (non-empty → parsed; empty → meta row, skipped), replacing the earlier bracket-scanning model. The workbook was rebuilt to support this (Config Key column added, values normalised, validation/password gating applied).

Value rules: split Response on first `:` (keep left, trim both sides); arrays on ` & `; `IDENTIFICATION` split on `to` → `{min,max}`; per-field step-division; `INTERVAL` enum-mapped; `TIMEOUT_VALUE` nested under `CFG_TIMEOUT`, step-divided, padded to 8. Single external error code `PDQ_INVALID`.

Revision history: v1.0 initial → v1.1 dropped RSR_TYPE/TYPE_PRTCT_CODE (tpf-sourced) → v1.2 Config Key column model → v1.3 block-grouped shape + project blocks + version-aware group + numeric transforms. The v1.3 body was further extended in place (no version bump) to add CFG_SWITCH, the two CFG_SUPERVIS_FMA blocks, CFG_IP_SWITCH_TIME, and the FCT-derived CFG_IP_SWITCH exclusion note.

## 3. Validate v2 — request — `POST /api/config/v2/validate`

New endpoint; Phase 1 `/api/config/validate` untouched. On v2, `userInput` is upload-sourced only — Phase 1 form keys are not sent (UI frozen once both uploads succeed).

- **Mandatory:** `fctData` (verbatim FCT response), `pdqData` (verbatim PDQ response).
- **Optional:** `CFG_TROLLEY_SUPP`, `CFG_PARAM_TROLLEY_SUPP`, `CFG_RSR_TYPE`, `CFG_TYPE_PRTCT` — only if a tpf file was uploaded via `/api/upload/translate`.

Coupled-artifacts gate: both `fctData` and `pdqData` mandatory, else `HTTP 400 PHASE2_INPUTS_INCOMPLETE`. Endpoint selection: both uploaded → v2; neither → v1; only one → invalid state (UI prevents). tpf is independent of the gate.

Revision v1.1 marked the version-aware group resolved (sourced from `pdqData` per PDQ row 1.09) and recorded the three validate-time cross-correlation rules as out of scope on the preprocessor side.

## 4. Validate v2 — response — `POST /api/config/v2/validate`

Preserves the Phase 1 response shape — `validation_results[]` plus the eight detail tables (`dp_details`, `track_section_details`, `chc_details`, `supervisor_details`, `ioexb_behaviour_details`, `ioexb_aco_details`, `data_transmission_details`, `ethernet_details`).

The validation atom is the individual detail-table cell. The preprocessor builds an internal **Expectations JSON** mirroring the eight tables (not returned). Mismatches surface two ways, consistently:

- **Detail tables (data view):** a cell gains a sibling `<fieldname>_expected` key *only on mismatch*. No per-cell status flag — presence of `_expected` is the signal. Array cells are a single atom (element-by-element ordered match); on mismatch the full expected array is emitted, not a positional diff.
- **`validation_results[]` (verdict view):** one row per check, in the existing Phase 1 seven-field shape (`fileName`, `ruleType`, `blockName`, `entryKey`, `expectedValue`, `actualValue`, `status`). Phase 2 rows carry no extra fields — shape-identical to Phase 1. The Phase 2 `ruleType` set is finalised in the Rule Registry (placeholder until then).

A zero-mismatch v2 response is shape-compatible with a Phase 1 response.

---

## Cross-cutting decisions

These span more than one contract and are worth holding in one place.

- **Block-grouped `cqIrParameters`** mirrors Phase 1 `userInput` so the engine path is shared. Full block-to-key mapping in `fcvt-phase2-design.md` §6.4.
- **Source split for the userInput keys on v2:** PDQ supplies the CQ-IR-derived blocks; tpf supplies `CFG_TROLLEY_SUPP` / `CFG_PARAM_TROLLEY_SUPP` / `CFG_RSR_TYPE` / `CFG_TYPE_PRTCT`; the control table supplies `RESET_OUT` and `BEHAV_INPUT3` (derived at validate time); the FCT supplies the basis for `CFG_IP_SWITCH`. *Frozen Ver14:* `CFG_RSR_TYPE` is the one dual-sourced key — PDQ (`cqIrParameters`) **and** tpf — cross-checked at preprocess time (must match, else the validate is rejected); every other key is single-sourced.
- **Version-aware group** (`TYPE_IN1/2/3`, `TYPE_AUX1/2`, `SUPERVIS_COUNT_LMT`): keyed on PDQ row 1.09 (AEB Equipment Version) — omitted for GS05-and-below, property-file defaults for GS06-and-above.
- **Four validate-time cross-correlation rules** (specified `fcvt-phase2-design.md` §4.1; not in any single contract because they operate on the assembled payload):
  1. Project-block consistency — ADC `CFG_PROJECT_AEB` / `CFG_PROJECT_COM` both-absent-or-both-present, same value.
  2. Dual-FMA consistency — ADC `RESET_TYPE` / `RESET_DELAY` for FMA1 and FMA2 both checked against the single parsed source; divergence fails.
  3. `CFG_IP_SWITCH` derivation — added from FCT `redundantComPresent` at preprocessing; absent = disabled.
  4. `CFG_RSR_TYPE` consistency (Ver14) — when tpf is uploaded, the PDQ and tpf `RSR_TYPE` must match; mismatch stops validation with a baseline error.
- **Samples:** `UploadFCTResponse.json` and `UploadPDQResponse` (block-grouped) are the authoritative shape references attached to the upload contracts.

---

## Open items

- **Version-aware default values** for GS06+ — exact property-file values (resolution mechanism is settled; the values themselves are a backend MappingProperties concern).
- **Phase 2 `ruleType` vocabulary** — finalised in the Rule Registry as cluster rules are built (BE-05 onward).
- **Payload size** — inlining `fctData` + `pdqData` inflates the v2 request; the 50 MB cap should hold but backend should verify against a realistic full-size payload.
- **Frontend acknowledgement** — FE lead to confirm review of all four contracts in writing before v2 integration begins.

---

## Reading order

1. This overview.
2. `fcvt-phase2-design.md` — the canonical design doc (preprocessor, clusters, the cross-cutting rules in full).
3. The four wiki contracts, in the order above, for endpoint-level detail.
