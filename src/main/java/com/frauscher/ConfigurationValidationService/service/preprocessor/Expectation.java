package com.frauscher.ConfigurationValidationService.service.preprocessor;

/**
 * A single expected value keyed by {@code (file, block, instance, entry)} — the composite key the
 * Phase 2 engine (VTF-336) will look up against the actual ADC value. Produced by the
 * {@link ExpectationsPreprocessor} from the FCT + PDQ baseline; ADC files are never a source.
 */
public record Expectation(
        String fileName,
        String block,
        int instance,
        String entryKey,
        String expectedValue) {
}
