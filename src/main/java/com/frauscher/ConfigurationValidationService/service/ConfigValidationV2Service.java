package com.frauscher.ConfigurationValidationService.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.preprocessor.Expectations;
import com.frauscher.ConfigurationValidationService.service.preprocessor.ExpectationsPreprocessor;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedExpectationEvaluator;
import com.frauscher.ConfigurationValidationService.validation.payload.PayloadValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the Phase 2 (v2) validate path (design §4). Builds baseline {@link Expectations} from
 * the gated FCT + PDQ input via the {@link ExpectationsPreprocessor} seam, then assembles the
 * {@link ValidationSummary}. The expectations are consumed in two buckets (v2-expectations-contract.md
 * §6): the scalar bucket reuses the Phase 1 engine ({@link ConfigValidationService#validateParsedFiles})
 * verbatim, and the instanced bucket is resolved per-entity by the {@link InstancedExpectationEvaluator}
 * (identity / positional / set-equality occurrence selection). Both result lists feed the summary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigValidationV2Service {

    private final ExpectationsPreprocessor expectationsPreprocessor;
    private final ConfigValidationService configValidationService;
    private final PayloadValidator payloadValidator;
    private final InstancedExpectationEvaluator instancedExpectationEvaluator;
    private final SummaryService summaryService;

    public ValidationSummary validate(List<ParsedConfigFile> parsedConfigFiles, ValidationInputV2 userInput) {

        Expectations expectations = expectationsPreprocessor.preprocess(userInput);
        log.debug("v2 validate: preprocessor produced {} scalar + {} instanced expectation(s)",
                expectations.scalarExpectations().size(), expectations.instancedExpectations().size());

        List<ValidationResult> results = new ArrayList<>();

        // Scalar bucket (§6): reuse the Phase 1 engine. The expected values come from the PDQ/FCT
        // baseline, not user UI inputs, so resolve them leniently (no UIInputRequired/type enforcement)
        // and run the same rule dispatch — a value the baseline omits leaves its rule dormant.
        var scalarPayload = payloadValidator.resolve(expectations.scalarExpectations());
        results.addAll(configValidationService.validateParsedFiles(
                parsedConfigFiles, expectations.scalarExpectations(), scalarPayload));

        // Instanced bucket (§5–§6): per-entity occurrence selection the flattening Phase 1 engine can't do.
        results.addAll(instancedExpectationEvaluator.evaluate(
                parsedConfigFiles, expectations.instancedExpectations()));

        return summaryService.generateSummary(parsedConfigFiles, results);
    }
}
