package com.frauscher.ConfigurationValidationService.validation.instanced;

import java.util.Map;

import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;

/**
 * A {@link ValidationResult} produced by the {@link InstancedExpectationEvaluator} together with the
 * structured coordinate the {@code MismatchAnnotator} (BE-07) needs to map it onto a detail-table cell —
 * so the join needs no re-parsing of the result's {@code entryKey} string. The evaluator owns the raw
 * identity it checked; the annotator owns the detail-table layout (which column, which array index,
 * raw→display). See FCVT-v2-Validation-Response-Contract.md §4.
 *
 * @param result        the emitted result (carries fileName/status and, once assigned, the id used as {@code result_id})
 * @param block         the config block name (e.g. {@code CFG_ZP_FMA1})
 * @param fileId        the ADC {@code [IDENTIFICATION] ID} the result is for (the detail-row join key)
 * @param kind          how the failing cell should be annotated, or {@code null} for a PASS / non-cell result
 * @param linkedId      the raw within-block identity (BY_IDENTITY / forwarding); empty for SINGLE / POSITIONAL
 * @param position      the slot ordinal (POSITIONAL); {@code null} otherwise
 * @param entryKey      the checked entry (e.g. {@code SLCT_TIMEOUT}); {@code null} for occurrence-level UNEXPECTED/MISSING
 * @param rawExpected   the raw expected value (VALUE); {@code null} otherwise
 * @param rawActual     the raw actual value (VALUE); {@code null} otherwise
 * @param memberExpected for a BY_IDENTITY member, its {@code entry -> expectedValue} map (lets the annotator
 *                       pick the target array, e.g. counting-head DIR_INV ch/i_ch); empty otherwise
 */
public record InstancedFinding(
        ValidationResult result,
        String block,
        int fileId,
        MismatchAnnotation.Kind kind,
        Map<String, String> linkedId,
        Integer position,
        String entryKey,
        String rawExpected,
        String rawActual,
        Map<String, String> memberExpected) {

    /**
     * Whether the wrapped result is a non-PASS outcome that should be annotated onto a cell. Includes
     * {@code INVALID} so a Cluster 1 (BE-08) refined SLCT_TIMEOUT verdict still highlights its cell.
     */
    public boolean isAnnotatable() {
        return kind != null && !"PASS".equals(result.getStatus());
    }
}
