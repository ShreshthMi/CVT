package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.Map;

/**
 * A single per-entity expected value the Phase 2 engine (BE-06) resolves against the actual ADC value.
 * Identity is {@code (fileId, block, <selector>, key)} where the selector depends on {@link #matchMode}:
 * a {@code linkedId} identity map (BY_IDENTITY), a {@code position} ordinal (POSITIONAL), or neither
 * (SINGLE). Produced by the {@link ExpectationsPreprocessor} from the FCT + PDQ baseline; ADC files are
 * never a source. See {@code vtf-335-scope.md} §6.
 *
 * <p>{@code fileId} is the numeric ADC {@code [IDENTIFICATION] ID} (DP id / COM id) the engine joins on.
 * For POSITIONAL (ACO) the {@code ID}/{@code SECTION} are emitted as {@code key} rows so a re-sequenced
 * configuration fails at the slot.</p>
 *
 * <p>{@code defaultValue} encodes optionality: {@code null} = a mandatory match (block/entry absent →
 * FAIL, like {@code InputMatch}); non-null = optional ({@code OptionalInputMatchOrBlockNotFound}) — the
 * engine treats an absent block as PASS iff {@code defaultValue.equals(expectedValue)}, else validates
 * the present value against {@code expectedValue}. Used by {@code CFG_IP_SWITCH} on a non-redundant COM
 * (the block is usually absent there).</p>
 */
public record InstancedExpectation(
        int fileId,
        String block,
        MatchMode matchMode,
        Map<String, String> linkedId,
        Integer position,
        String key,
        String expectedValue,
        String defaultValue) {

    /** A mandatory {@link MatchMode#SINGLE} expectation: the one occurrence in the file {@code fileId}. */
    public static InstancedExpectation single(int fileId, String block, String key, String expectedValue) {
        return new InstancedExpectation(fileId, block, MatchMode.SINGLE, Map.of(), null, key, expectedValue, null);
    }

    /** An optional {@link MatchMode#SINGLE} expectation: absent block → PASS iff {@code defaultValue == expectedValue}. */
    public static InstancedExpectation singleOptional(
            int fileId, String block, String key, String expectedValue, String defaultValue) {
        return new InstancedExpectation(fileId, block, MatchMode.SINGLE, Map.of(), null, key, expectedValue, defaultValue);
    }

    /** A {@link MatchMode#BY_IDENTITY} expectation: the occurrence whose identity entries equal {@code linkedId}. */
    public static InstancedExpectation byIdentity(
            int fileId, String block, Map<String, String> linkedId, String key, String expectedValue) {
        return new InstancedExpectation(
                fileId, block, MatchMode.BY_IDENTITY, Map.copyOf(linkedId), null, key, expectedValue, null);
    }

    /** A {@link MatchMode#POSITIONAL} expectation: the {@code position}-th occurrence (ACO slot). */
    public static InstancedExpectation positional(
            int fileId, String block, int position, String key, String expectedValue) {
        return new InstancedExpectation(fileId, block, MatchMode.POSITIONAL, Map.of(), position, key, expectedValue, null);
    }
}
