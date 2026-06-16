# Phase 2 Contract / Design Doc Updates — Frozen PDQ Ver14 (P3)

*Catalogue of the documentation changes made while landing P3 (PDQ parser → frozen Ver14). Records what changed in each doc and why. Two rounds: the RSR_TYPE / dual-source updates (committed and on `main`), and the pre-Ver14 prose cleanup (held for review at time of writing).*

---

## Why these docs changed

The locked PDQ/v2 contracts and the design doc described pre-Ver14 behaviour that the parser no longer follows. Three things drove the edits:

1. **`RSR_TYPE` reversal** — contract v1.1 dropped `RSR_TYPE` from `cqIrParameters` (tpf-sourced). Ver14 re-introduces it as PDQ-sourced (`CFG_RSR_TYPE`).
2. **`RSR_TYPE` dual-source + preprocessor cross-check** — RSR_TYPE now comes from **both** PDQ and tpf on v2; the two must match at preprocess time or validation stops with a baseline error. This is a new (fourth) cross-correlation rule.
3. **Pre-Ver14 prose** — System Redundancy row 1.08, `blockExistsForProjectCode`, the old control-table column layout / `fadcAutoReset` model, the old version-string format, and the obsolete bracket-scanning parse model were all still described as current.

---

## Round 1 — RSR_TYPE + dual-source (committed `f04a9a5`, fast-forwarded to `origin/main`)

| Document | What changed | Why |
|---|---|---|
| `Contracts/pdq-upload-contract-v1.1.wiki` | §4.2 now drops **only** `TYPE_PRTCT_CODE`; §6 warning updated; new **v1.4** revision note recording all Ver14 deltas | RSR_TYPE is no longer dropped |
| `Phase 2 Endpoint analysis/UploadPDQResponse.json` | Added the `CFG_RSR_TYPE: { "RSR_TYPE": "1" }` block | Sample must show the re-introduced key |
| `fcvt-phase2-design.md` | §6.4 "keys not emitted" (RSR_TYPE removed from the list) + Project-blocks paragraph (new `BLOCK_EXISTS`/`PROJECT_NUMBER` source); §4.1 **fourth cross-correlation rule** (RSR_TYPE PDQ↔tpf consistency); B14 open-item updated | Parser + preprocessor behaviour |
| `Contracts/validate-v2-request-contract.wiki` | §5 rewritten (RSR_TYPE dual-sourced + cross-check; only TYPE_PRTCT stays tpf-sole); the `CFG_RSR_TYPE` optional-key row annotated | v2 request now carries RSR_TYPE from both sources |
| `Phase 2 Endpoint analysis/fcvt-phase2-contracts-overview.md` | "No key has two sources" corrected (CFG_RSR_TYPE is dual-sourced + cross-checked); the cross-correlation list grew from three to **four** rules | Keep the overview consistent |

**Decision recorded:** RSR_TYPE source = **keep both** (PDQ *and* tpf), reconciled by the preprocessor cross-check rather than dropping one side.

---

## Round 2 — pre-Ver14 prose cleanup

| Document | What changed | Why |
|---|---|---|
| `Contracts/pdq-upload-contract-v1.1.wiki` | §3 table (`blockExistsForProjectCode` → `aebEquipmentVersion`); §4.1 (bracket-scanning → **Configuration-Word-column** model); §4.4 (Single/Dual row 1.08 → CQ-IR `PROJECT_NUMBER` row); §5 error wording (dropped "bracket"); §7 out-of-scope; v1.4 note "pending" flag removed | Sections still described pre-Ver14 / pre-v1.2 behaviour |
| `fcvt-phase2-design.md` | §6.5 control-table columns (**A–I / K–N**, col **J** boundary) + the `fadcAutoReset` model (operands col G + separate `Logic type` col H); §6.4 `controlTable` / `aebEquipmentVersion` lines; the sheet-anchors paragraph (row 1.08 removed, Remarks added); sample `aebEquipmentVersion` → `GS07`; open-item **A2** marked *Superseded (Ver14)* | Old column layout + operator-in-one-cell model + GS06/GS05-and-above/below version format |
| `Contracts/pdq-upload-contract.wiki` | **SUPERSEDED banner** added at the top, pointing to `pdq-upload-contract-v1.1.wiki` as authoritative; body left as a historical v1.0 snapshot | This older duplicate carried the same stale prose; retained for history, not rewritten |

**Note on residual mentions:** a few lines in the cleaned docs still *mention* the old terms — e.g. *"replaced the former Single/Dual row 1.08"*, *"`blockExistsForProjectCode` is removed — now lives in `CFG_PROJECT_AEB.BLOCK_EXISTS`"*, *"supersedes the bracket-scanning model"*. These are intentional **supersession notes**, not stale claims.

---

## Authoritative vs historical

- **Authoritative PDQ upload contract:** `pdq-upload-contract-v1.1.wiki` (revision history v1.0 → v1.1 → **v1.4**).
- **Historical baseline:** `pdq-upload-contract.wiki` (v1.0, marked superseded).
- **Response sample:** `UploadPDQResponse.json` (includes `CFG_RSR_TYPE`).
- **Design source of truth:** `fcvt-phase2-design.md` §6 (PDQ parsing) + §4.1 (preprocessor rules).

---

## Still-open doc items (tracked, not blocking)

- The `RSR_TYPE` cross-check (design §4.1 rule 4) is **specified, not implemented** — lands with BE-05.
- The version-aware-group source for the six `TYPE_*` / `SUPERVIS_COUNT_LMT` keys remains an AE open item flagged in the v2-request contract (unrelated to Ver14).
