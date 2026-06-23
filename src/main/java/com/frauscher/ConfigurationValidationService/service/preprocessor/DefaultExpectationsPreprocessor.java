package com.frauscher.ConfigurationValidationService.service.preprocessor;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;

import lombok.RequiredArgsConstructor;

/**
 * The real {@link ExpectationsPreprocessor}. Runs the {@link BaselineGate} (hard HTTP 400 admission
 * control, v2-expectations-contract.md §3) and then builds the {@link Expectations} buckets. The
 * scalar bucket (§4) lands in M3 and the instanced bucket (§5) in M5; until then the gate runs and an
 * empty {@link Expectations} is returned (emission-only — BE-06 consumes).
 */
@Service
@RequiredArgsConstructor
public class DefaultExpectationsPreprocessor implements ExpectationsPreprocessor {

    private final BaselineGate baselineGate;

    @Override
    public Expectations preprocess(ValidationInputV2 userInput) {
        baselineGate.check(userInput);

        // TODO(VTF-335 M3): scalarExpectations. TODO(VTF-335 M5): instancedExpectations.
        return Expectations.empty();
    }
}
