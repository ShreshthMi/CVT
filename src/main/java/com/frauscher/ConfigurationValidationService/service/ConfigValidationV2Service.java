package com.frauscher.ConfigurationValidationService.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.Phase2ValidationInput;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.preprocessor.Expectations;
import com.frauscher.ConfigurationValidationService.service.preprocessor.ExpectationsPreprocessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the Phase 2 (v2) validate path (design §4). Builds baseline {@link Expectations} from
 * the gated FCT + PDQ input via the {@link ExpectationsPreprocessor} seam, then assembles the
 * {@link ValidationSummary}. The engine extension that evaluates parsed ADC files against the
 * composite-key expectations is VTF-336 (BE-06); until then no v2 results are produced, so the
 * response is the Phase 1-shaped summary (detail tables from the parsed files, empty results).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigValidationV2Service {

    private final ExpectationsPreprocessor expectationsPreprocessor;
    private final SummaryService summaryService;

    public ValidationSummary validate(List<ParsedConfigFile> parsedConfigFiles, Phase2ValidationInput userInput) {

        Expectations expectations =
                expectationsPreprocessor.preprocess(userInput.getFctData(), userInput.getPdqData());
        log.debug("v2 validate: preprocessor produced {} expectation(s)", expectations.entries().size());

        // TODO(VTF-336): evaluate parsedConfigFiles against `expectations` to produce v2 ValidationResults.
        List<ValidationResult> results = List.of();

        return summaryService.generateSummary(parsedConfigFiles, results);
    }
}
