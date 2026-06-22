package com.frauscher.ConfigurationValidationService.service.preprocessor;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;

/**
 * Placeholder {@link ExpectationsPreprocessor}: returns no expectations. The real derivations — the
 * input gate, the scalar bucket and the instanced bucket — land across VTF-335 (BE-05) milestones
 * M2–M5.
 */
@Service
public class StubExpectationsPreprocessor implements ExpectationsPreprocessor {

    @Override
    public Expectations preprocess(ValidationInputV2 userInput) {
        return Expectations.empty();
    }
}
