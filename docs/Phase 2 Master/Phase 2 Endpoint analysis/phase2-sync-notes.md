═══════════════════════════════════════════════════════════════════
PHASE 2 VALIDATION — SYNC NOTES
═══════════════════════════════════════════════════════════════════

THREE ENDPOINTS

1. /upload/fct (new)
   - In:  .fct2 archive (multipart, ≤10MB)
   - Out: ComAebMap JSON
          chains[] — each with:
            com: { comId, comName }
            redundantComPresent: bool
            aebs[] — each with:
              aebId, aebName
              evaluatedFmas[]    (FMA name, fmaId, parent AEB id per FMA)
              acoIoExbs[]        (resolved OutputFma refs; can reference
                                  FMAs in different AEBs than IoExb's host)
              dtIoExbCount       (DT mode count only; inner DT data from PDQ)
   - Parses Project.xml only. Trackplan XMLs out of scope.
   - Fail-fast on first invalid condition. Two external error codes:
       FCT_TAMPERED              — manual edit detected during parse
       FCT_INCOMPLETE_BASELINE   — pre-export incomplete baseline FCT

2. /upload/pdq (new)
   - In:  .xlsx workbook (200KB–2MB typical, ≤10MB cap)
   - Out: parsed PDQ JSON with three top-level sections:
       projectCode                   — from PDQ sheet header
       blockExistsForProjectCode     — boolean, derived from PDQ row 1.08
                                       (Single→true, Dual→false)
       cqIrParameters                — flat map of ADC config keys → values
                                       (numeric, except PROTECTION_CODE
                                        which stays as hex string)
       controlTable                  — two sub-tables:
                                         trackSections[] + dpTable[]
       dataTransmission              — null if DT sheet absent; otherwise
                                       { dataSafetyLevels[], outputDataTransmission[] }
   - In-scope sheets: PDQ, CQ-IR, Control table, Data transmission Inputs.
     First three mandatory. DT sheet conditionally required (absent = no DT,
     present-but-empty = reject).
   - Parser respects sheet/row/column visibility — hidden content out of scope.
   - Fail-fast on first invalid condition. One external error code:
       PDQ_INVALID                   — all parse/validation failures collapse here
   - Internal enum carries diagnostic detail for logs (sheet missing,
     bracket missing, range invalid, lookup failed, etc.) — not surfaced
     externally.

3. /validate (existing, payload extended)
   - userValidationInputCriteria array now also carries:
       - FCT upload response (ComAebMap)
       - PDQ upload response (parsed PDQ)
   - Plus existing parsedConfigFiles (ADCs from /upload/adcfiles)
   - UI form freezes read-only once both PDQ + FCT uploaded.
     (UI form is the manual fallback when uploads aren't used.)


COUPLED ARTIFACTS — LOCKED ASSERTION

   FCT and PDQ are jointly necessary for Phase 2 validation.
   /validate cannot be triggered unless BOTH are uploaded, OR NEITHER
   (UI fallback path).

   Reason:
   Cluster 1 Check A on FCT alone passes SLCT_TIMEOUT consistency against
   an unverified ref AEB. Ref AEB correctness needs PDQ's CCT.
   A passing rule against a wrong ref AEB hides real config errors —
   worse than no check. Same logic applies to all FCT-touching clusters.

   For projects without properly formatted PDQ: Phase 2 validation is
   not available for that project. Manual review remains the path.
   Partial-input support would significantly increase dev cost and time
   without increasing validation value.


PDQ PARSING — KEY DECISIONS LOCKED

   CQ-IR row identification:
     - Parser extracts rows where question text contains bracketed
       all-caps token (e.g. "(OCC_EXT)" — token after the parenthesis
       is the ADC config key).
     - Rows without bracketed token are skipped as meta questions.
     - Question wording is descriptive context for AE; parser only
       cares about the bracket.

   CQ-IR response normalization:
     - Numeric prefix extraction: "7: Disable - For SAT" → 7.
       Verbose descriptions preserved in source sheet for AE
       readability; stripped at parse time.
     - Unit conversion via MappingProperties step:
       "2600ms" with OCC_EXT.step=100ms → 26 in PDQ JSON.
     - Non-integer step → reject (PDQ_VALUE_NOT_DIVISIBLE_BY_STEP).
     - Enumerated lookup failure → reject
       (MAPPING_PROPERTIES_LOOKUP_FAILED).

   Special-case CQ-IR rows:
     - "Range of ID for AEB & COM (IDENTIFICATION)" — parsed as range
       { min, max }, must match Phase 1 RangeCheck userInput shape.
     - "Selection Time out (CFG_TIMEOUT)" — multi-value, split on "&",
       produces ascending-sorted integer array (e.g. "340 & 610 ms" → [34, 61]).
     - "type-specific protection code" — hex value, kept as string with
       0x prefix; PROTECTION_CODE bracket required from AE.

   Control table:
     - Two side-by-side sub-tables (track sections cols A–H, DP table
       cols J–M, empty col I as boundary).
     - Track Output column "PHYSICAL"/"VIRTUAL" translated to
       "MAIN"/"COMBINATION" at parse time to avoid collision with
       Cluster 1's segment-boundary terminology.
     - fadcAutoReset cells parsed into logic tree
       { op: "OR"|"AND", operands: [...] }, up to 8 operands,
       single operator per cell only (mixed → reject).
     - YES/NO cells (E-CHC, autoResetByTimer) → boolean.

   Data Transmission Inputs sheet:
     - Two sub-tables: dataSafetyLevels (DP NAME, SAFETY_LEVEL_IN/OUT,
       SAFE_OUT_FDBCK_QUAD) and outputDataTransmission (SOURCE DP NAME,
       NMBR_OUT, POSITION).
     - SAFE_OUT_FDBCK_QUAD is numeric 0/1 in source and in JSON.
     - POSITION in outputDataTransmission is integer 0–31 (distinct from
       Control table's POSITION which is "ABOVE/BELOW THE RAIL"). Out of
       range → reject (PDQ_INVALID_RANGE).
     - Sheet absent = valid (no DT for project).
     - Sheet present + empty = reject as malformed.


VALIDATE-TIME BACKEND FLOW

   Step 1 — Preprocessor (validate-time, in-memory object)
     Inputs:  ComAebMap + parsed PDQ  (both guaranteed present)
     Outputs:
       - Expectations JSON
           keyed (ADC file, block, instance, entry) → expected value
       - Virtual-pair scaffold
           (channels grouped by homeCom/consumingCom)

     Source-of-truth rule:
     ADC files are validation TARGETS only.
     Nothing in the preprocessor reads from ADC.
     Expectations come from design baseline (FCT + PDQ),
     not from the artifact-under-test.

   Step 2 — Engine
     Consumes expectations JSON as additional external input source.
     - Engine extension needed: composite-key lookup mode for
       (file, block, instance, entry).
       [Mechanism still open — JSONPath in rule config vs named
        Java lookups. Discuss separately.]
     - Existing rule types stretch to cover Phase 2:
         Check A → InputMatch-style
         Check B → list-membership (≈ MultipleBlockMultipleInputMatch)
     - All Phase 1 rules continue to pass.
       BDD extended, not replaced.


CLUSTER → BLOCK → KEY → SOURCE ARTIFACT

   Cluster 1 — CAN Segment
     Blocks:  CFG_ZP_FMA1, CFG_ZP_FMA2, CFG_SECTION_OUT, CFG_CONTROL,
              CFG_SUPERVIS_FMA1, CFG_SUPERVIS_FMA2, CFG_DATA_OUT
     Keys:    ID, SLCT_TIMEOUT
              (validated wherever these keys appear, across all
               in-scope blocks)
              Plus CFG_FWRD_ACD entries (Check B forwarding validation)
     Source:  FCT ComAebMap + PDQ CCT
     Note:    ID and SLCT_TIMEOUT key validation is owned by Cluster 1
              across all in-scope blocks; not by the cluster that
              nominally owns the host block. Exception: Cluster 2 also
              validates ID and SECTION on CFG_SECTION_OUT, with
              different semantics.

   Cluster 2 — IOEXB ACO
     Block:   CFG_SECTION_OUT
     Keys:    ID, SECTION
              (validates IoExb position within board rack +
               number of ACO IoExbs attached to each AEB)
              Plus block count per file
     Source:  FCT ComAebMap (acoIoExbs)
     Note:    ID and SLCT_TIMEOUT also validated by Cluster 1 with
              different semantics. Both clusters run; no overlap
              in failure modes.

   Cluster 3 — Track Section
     Blocks:  TBD from Confluence requirements
     Keys:    TBD from Confluence requirements
     Source:  PDQ CCT

   Cluster 4 — CHC / External CHC
              (combined cluster; same block, different validation
               approaches per sub-cluster)
     Blocks:  TBD from Confluence requirements
     Keys:    TBD from Confluence requirements
     Source:  PDQ CCT

   Cluster 5 — Supervisor
     Blocks:  TBD from Confluence requirements
     Keys:    TBD from Confluence requirements
     Source:  PDQ CCT

   Cluster 6 — Data Transmission
     Blocks:  CFG_DATA_OUT, CFG_DATA_SAFETY_LEVELS
     Keys:    TBD from Confluence requirements
     Source:  PDQ Data Transmission Output subsheet


OUT OF SCOPE (flag if asked)

   - COM file IP validation — deferred. If reintroduced later,
     static checks only (valid format, distinct, exists in expected
     list). Not file-sourced cross-referencing.
   - 3G subsumed into 3E — no non-AEB ID references between COM files
     exist; cross-file ID resolution is identical to FCT2-derived
     segment membership lookup in 3E. No separate cluster.
   - Trackplan XMLs inside FCT archive (only Project.xml parsed).
   - Counting-head-output mode IoExbs (only ACO and DT modes in scope;
     FCT rejected if a counting-head IoExb is present).
   - Hidden rows/columns/sheets in PDQ workbook — parser respects
     visibility flags.


OPEN ITEMS

   AE-side:
   - PDQ workbook updates per gaps document (CQ-IR bracket additions,
     CFG_TIMEOUT unit fix, new PDQ row 1.08 for Single/Dual architecture,
     populated DT sheet example).
   - Confirm Reset Type catalog enumeration in Control table.
   - Provide MappingProperties values for IP_SWITCH_TIME, RESET_DELAY,
     SWITCH_GE, SWITCH_GSF, PRERESET_ACT_TIME, NMBR_OUT, and hex format
     pattern for protection code.

   Backend-side:
   - MappingProperties file updates for missing keys above.
   - CFG_TIMEOUT alias resolution against TIMEOUT_VALUE.
   - Engine composite-key lookup mechanism — JSONPath in rule config
     vs named Java lookups. Tradeoff parked.
   - Exact 8 root filenames + 2 folder names in FCT archive
     (needed for proper file identification and malformed-FCT
     rejection during parse).
   - Cluster 3 / 4 / 5 / 6 block and key specifics — from Confluence
     requirements document.
   - Spring Boot 4.0.2 multipart default override behavior — verify.

═══════════════════════════════════════════════════════════════════
