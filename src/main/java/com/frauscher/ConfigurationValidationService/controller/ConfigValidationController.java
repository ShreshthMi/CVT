package com.frauscher.configvalidator.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.frauscher.configvalidator.dto.UserValidationInputCriteria;
import com.frauscher.configvalidator.dto.ValidationRequestWrapper;
import com.frauscher.configvalidator.exception.InvalidUserValidationInputException;
import com.frauscher.configvalidator.model.ParsedConfigFile;
import com.frauscher.configvalidator.model.ValidationResult;
import com.frauscher.configvalidator.model.ValidationSummary;
import com.frauscher.configvalidator.service.ConfigValidationService;
import com.frauscher.configvalidator.service.SummaryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigValidationController {

    private final ConfigValidationService configValidationService;
    private final SummaryService summaryService;

    @PostMapping(value = "/validate")
    public ResponseEntity<ValidationSummary> validate(
            @RequestBody ValidationRequestWrapper request) {

        List<ParsedConfigFile> parsedConfigFiles = request.getParsedConfigFiles();
        UserValidationInputCriteria userValidationInputCriteria = request.getUserInput();

        if (parsedConfigFiles == null || parsedConfigFiles.isEmpty()) {
            throw new InvalidUserValidationInputException(
                    "At least one config file is required");
        }

        if (userValidationInputCriteria == null) {
            throw new InvalidUserValidationInputException(
                    "Payload criteria is required");
        }

        if (userValidationInputCriteria.getSections() == null
                || userValidationInputCriteria.getSections().isEmpty()) {

            throw new InvalidUserValidationInputException(
                    "Payload sections must not be empty");
        }

        List<ValidationResult> results =
                configValidationService.validateParsedFiles(parsedConfigFiles, userValidationInputCriteria.getSections());

        summaryService.setValidationResults(results);
        ValidationSummary summary = summaryService.generateSummary(parsedConfigFiles);
        
        return ResponseEntity.ok(summary);
    }
}
