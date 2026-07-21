package com.frauscher.ConfigurationValidationService.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.preprocessor.BaselineInconsistencies;
import com.frauscher.ConfigurationValidationService.service.preprocessor.Expectations;
import com.frauscher.ConfigurationValidationService.service.preprocessor.ExpectationsPreprocessor;
import com.frauscher.ConfigurationValidationService.validation.annotation.MismatchAnnotator;
import com.frauscher.ConfigurationValidationService.validation.cluster.CanSegmentValidator;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedExpectationEvaluator;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedFinding;
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
    private final MismatchAnnotator mismatchAnnotator;
    private final CanSegmentValidator canSegmentValidator;

    public ValidationSummary validate(List<ParsedConfigFile> parsedConfigFiles, ValidationInputV2 userInput) {

        Expectations expectations = expectationsPreprocessor.preprocess(userInput);
        log.debug("v2 validate: preprocessor produced {} scalar + {} instanced expectation(s)",
                expectations.scalarExpectations().size(), expectations.instancedExpectations().size());

        // Scalar bucket (§6): reuse the Phase 1 engine. The expected values come from the PDQ/FCT
        // baseline, not user UI inputs, so resolve them leniently (no UIInputRequired/type enforcement)
        // and run the same rule dispatch — a value the baseline omits leaves its rule dormant.
        var scalarPayload = payloadValidator.resolve(expectations.scalarExpectations());
        List<ValidationResult> scalarResults = configValidationService.validateParsedFiles(
                parsedConfigFiles, expectations.scalarExpectations(), scalarPayload);

        // Instanced bucket (§5–§6): per-entity occurrence selection the flattening Phase 1 engine can't do.
        // The annotated form additionally carries the per-result cell coordinate for the BE-07 join.
        // VTF-360: evaluation-phase baseline defects (forwarding socket→COM resolution) accumulate and,
        // if any, reject once with the complete list — the same model as the preprocessor's end-check.
        BaselineInconsistencies evaluationProblems = new BaselineInconsistencies();
        List<InstancedFinding> findings = instancedExpectationEvaluator.evaluateAnnotated(
                parsedConfigFiles, expectations.instancedExpectations(), evaluationProblems);
        evaluationProblems.throwIfAny();

        // Cluster 1 (BE-08) Check-A named verdicts: refine SLCT_TIMEOUT / unknown-reference findings in
        // place and add ORPHANED results for uploaded AEB ADCs absent from the FCT-defined CAN segments.
        List<ValidationResult> orphaned = canSegmentValidator.apply(
                findings, userInput.getFctData(), parsedConfigFiles);

        List<ValidationResult> results = new ArrayList<>(scalarResults);
        findings.forEach(f -> results.add(f.result()));
        results.addAll(orphaned);

        // Assign each result a response-scoped opaque id (the cell→log navigation target — response
        // contract §2/§5). Phase 1 results keep a null id and the field is omitted from that response.
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setId("r" + i);
        }

        ValidationSummary summary = summaryService.generateSummary(parsedConfigFiles, results);

        // BE-07: map each failing result onto its detail-table cell (red highlight + Expected/Actual
        // tooltip + click-to-log-entry). Additive — a clean run adds nothing.
        mismatchAnnotator.annotate(summary, scalarResults, findings, parsedConfigFiles);
        return summary;
    }
}
