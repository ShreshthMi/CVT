# Task: Migrate FCVT context from memory into themed files

## The situation

My Claude memory is at the 30-entry cap. Roughly 24 of those entries are FCVT-related (a railway configuration validation tool I own end-to-end at Frauscher). The cap has been making it hard to accumulate new context as the project evolves — every new lock means displacing something existing.

The fix we've agreed on: move all FCVT context out of memory and into themed reference files that you read at the start of any FCVT-related conversation. Memory becomes person-level context only.

## What I want from you

Produce four themed markdown files covering all FCVT context, then clean up memory after I've confirmed the files are complete.

The four files:

1. **`fcvt-codebase.md`** — current state of the running tool. Architecture, endpoints, rule engine internals, ParsedConfigFile shape, known bugs, codebase facts that exist today. Stable; rarely changes. Reference-style with tables where useful.

2. **`fcvt-phase1-history.md`** — Phase 1 requirements through release. Architecture decisions, BDD setup, the development arc, Lisa's challenge questions and rebuttals, How-To-Use guide history, the release process. I'll upload separate documents I generated with Claude Code that cover Phase 1; use those as primary source. Chronological narrative style.

3. **`fcvt-phase2-design.md`** — in-flight Phase 2 architecture. Cluster decisions, FCT upload endpoint design, validate-time preprocessor, coupled-artifacts decision, cluster→block→key mapping, open items. Decision-record-style. Updated frequently as design progresses (but that's for later — for now, capture current state).

4. **`fcvt-meeting-and-strategy.md`** — Max weekly syncs, Salai decisions, Vinod conversations, AE coordination, Jira/epic structure, career-strategy framing as it relates to FCVT delivery. Dated entries in reverse chronological order.

Each file gets its own appropriate structure — don't force a single template. Pick what serves the content. Nothing relevant can be left out of any file.

All files go to `/mnt/user-data/outputs/`.

## Inputs you have

**1. Memory (view first)**

You have access to all 30 of my memory entries via `memory_user_edits view`. View them before doing anything else.

Entries to keep: **1, 2, 3, 4, 17, 18** (six entries). These cover person-level context — Shreshth's profile, Frauscher employment, family/friends, career history, Frauscher order processing workflow (broader than FCVT), AE team structure (broader than FCVT). Confirm by reading the entries themselves; do not rely solely on my numbering if entries appear to have shifted (memory auto-updates periodically).

Entries to drop after files are confirmed: **5–16 and 19–30** (24 entries, all FCVT-related). Use `memory_user_edits remove` for each. Do NOT remove anything before I confirm the files are complete.

**2. Existing design document on disk**

A previous conversation produced `/mnt/user-data/outputs/fct-upload-design.md` (519 lines). It covers the FCT upload endpoint design comprehensively. Read it — it's a major input for `fcvt-phase2-design.md`.

⚠ **Three known deltas** in that doc that are now outdated:

- *Coupled artifacts*: doc treats FCT and PDQ as independent uploads. **Current state**: `/validate` cannot run unless BOTH PDQ and FCT are uploaded, OR NEITHER (UI fallback). No partial validation. Reason: Cluster 1 Check A on FCT alone passes SLCT_TIMEOUT against an unverified ref AEB (correctness needs PDQ CCT) — passing rule hides config errors, worse than no check. Same logic applies to all FCT-touching clusters.

- *Preprocessor architecture*: doc may show preprocessor work happening at FCT upload time. **Current state**: FCT upload extracts only ComAebMap. Everything else (expectations JSON, virtual-pair scaffold) moves to a validate-time preprocessor — an in-memory object built fresh per `/validate` request, scoped to method-local variables, stateless. Source-of-truth rule: ADC files are validation targets only; nothing in the preprocessor reads from ADC. Expectations come from design baseline (FCT + PDQ).

- *Cluster table*: doc has no cluster→block→key mapping table. **Current state**: see the table block below, embedded verbatim.

**3. Cluster→block→key table (from a session this morning)**

```
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
```

**4. Vinod sync notes (reference document, captures Phase 2 plan as presented in a recent sync)**

```
═══════════════════════════════════════════════════════════════════
PHASE 2 VALIDATION — SYNC NOTES (for Vinod)
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
   - In:  .xlsx workbook (200KB–2MB typical)
   - Out: parsed PDQ JSON — CQ-IR + ConfigControlTable + DT Output subsheet
   - Response shape designing later with parser-complexity guardrails.

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


[Cluster table — embedded above as Input 3]


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


OPEN ITEMS

   - PDQ upload response shape (designing later).
   - Engine composite-key lookup mechanism — JSONPath in rule config
     vs named Java lookups. Tradeoff parked.
   - Exact 8 root filenames + 2 folder names in FCT archive
     (needed for proper file identification and malformed-FCT
     rejection during parse).
   - Cluster 3 / 4 / 5 / 6 block and key specifics — from Confluence
     requirements document.
   - Spring Boot 4.0.2 multipart default override behavior — verify.

═══════════════════════════════════════════════════════════════════
```

**5. Phase 1 documents (I will upload separately)**

I will upload a set of documents I generated with Claude Code that cover Phase 1 of FCVT — requirements, architecture, development, release. These are the primary source for `fcvt-phase1-history.md`. Read them carefully when I upload them.

If any of the Phase 1 docs contradict what's currently in memory entries 5–10 (codebase facts), the uploaded docs win — they're sourced material; memory is my summarization. Flag contradictions to me before resolving them.

## Sequence I want you to follow

1. View memory entries (`memory_user_edits view`).
2. Read `/mnt/user-data/outputs/fct-upload-design.md`.
3. Confirm the three deltas in that doc against the current-state guidance above.
4. **Stop. Present a checklist of clarifying questions** before drafting anything. Examples of things I expect you to flag:
   - Content that could plausibly live in multiple files (e.g. a codebase fact that emerged from Phase 1 history — does it go in codebase.md or history.md?)
   - Gaps you see between the inputs and what the files need to cover
   - Anything in the inputs that contradicts itself or memory
   - Any uncertainty about how to interpret an instruction
5. Wait for my answers.
6. When I say I'm ready, I'll upload the Phase 1 docs.
7. Read the Phase 1 docs. Present another short checklist if new questions surface.
8. Draft the four files. Share each one with me for review before moving to the next. I'll read and give feedback.
9. After I confirm all four files are complete and correct, **only then** remove the 24 FCVT memory entries (5–16, 19–30) using `memory_user_edits remove`.
10. Confirm the final memory state (should be 6 entries: 1, 2, 3, 4, 17, 18).

## Critical constraints

- **No hallucination.** If you don't have certainty about something, say so. Do not invent details, do not paper over gaps with plausible-sounding content, do not produce filler.

- **No premature memory deletion.** Memory removal happens only after I've signed off on all four files. If you delete entries before that, the context is irretrievable.

- **Be honest about confidence.** If a Phase 1 detail is in memory but not in the uploaded docs, flag it. If the uploaded docs cover something in more depth than memory does, note that too.

- **Each file is comprehensive within its scope.** "Nothing relevant left out" is a hard constraint. If you find you're tempted to abbreviate a section because "the reader can probably figure it out," don't — write it explicitly.

- **Belt and braces on persistence.** Files in `/mnt/user-data/outputs/` may or may not persist across true session resets. Remind me to download local copies before we close out.

- **Stop and ask.** If anything in this prompt or in the inputs is ambiguous, ask before acting. The goal is correctness, not speed.

## What success looks like

- Four markdown files on disk, comprehensive within their respective scopes, factually accurate, internally consistent.
- Memory at 6 entries (1, 2, 3, 4, 17, 18).
- I have local copies of all four files.
- Future FCVT conversations can start by you reading the relevant file(s), with no need for me to re-explain context.

## What failure looks like

- Files with gaps, missing context, hand-wavy sections.
- Memory deleted before file confirmation.
- Files lost without local backup.
- Files that contradict each other or contradict the uploaded Phase 1 docs.
- Hallucinated details added to fill perceived completeness.

---

Take your time. Ask questions before acting. I'd rather spend an extra hour clarifying than discover gaps after memory is gone.
