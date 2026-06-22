package com.frauscher.ConfigurationValidationService.service.preprocessor;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;

/**
 * Validate-time preprocessor seam (v2-expectations-contract.md §1): cross-correlates the FCT + PDQ
 * (+ optional tpf) baseline carried by {@link ValidationInputV2} into {@link Expectations} (the scalar
 * and instanced buckets). The real derivations land across VTF-335 (BE-05); until then a stub returns
 * no expectations so the v2 path stays shape-complete. ADC files (the validation targets) are never an
 * input here — every expectation derives from the FCT + PDQ baseline.
 */
public interface ExpectationsPreprocessor {

    Expectations preprocess(ValidationInputV2 userInput);
}
