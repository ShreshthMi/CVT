package com.frauscher.ConfigurationValidationService.service.preprocessor;

/**
 * How the Phase 2 engine (BE-06) selects the ADC block occurrence(s) an {@link InstancedExpectation}
 * is validated against. See {@code vtf-335-scope.md} §6.
 */
public enum MatchMode {

    /** The one occurrence in the file picked by {@code fileId} (e.g. CFG_AXCNT.BEHAV_INPUT3, CFG_IP_SWITCH). */
    SINGLE,

    /**
     * Occurrence(s) whose identity entries equal {@code linkedId}; the {@code (fileId, block)} group is
     * compared by <b>strict set-equality</b> (missing → FAIL, extra → FLAG), order-independent
     * (e.g. CFG_ZP_FMA*, CFG_SUPERVIS_FMA*, CFG_CONTROL, CFG_FWRD_ACD).
     */
    BY_IDENTITY,

    /**
     * The {@code position}-th occurrence; the ADC sequence must match the baseline slot-for-slot. ACO
     * ({@code CFG_SECTION_OUT}) only — its pairs map by position to physical IO-EXB cards, so a
     * complete-but-re-sequenced config must fail.
     */
    POSITIONAL
}
