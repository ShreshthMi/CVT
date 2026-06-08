package com.frauscher.ConfigurationValidationService.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.frauscher.ConfigurationValidationService.dto.Phase2ValidationInput;
import com.frauscher.ConfigurationValidationService.dto.ValidationRequestV2;
import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.exception.Phase2InputsIncompleteException;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.ConfigValidationV2Service;

import lombok.RequiredArgsConstructor;

/**
 * Phase 2 validate endpoint. {@code POST /api/config/v2/validate} enforces the coupled-artifacts
 * gate (design §2): both FCT and PDQ must be present, else {@code HTTP 400 PHASE2_INPUTS_INCOMPLETE}.
 * The Phase 1 {@code POST /api/config/validate} path is left untouched (a separate controller).
 */
@RestController
@RequestMapping("/api/config/v2")
@RequiredArgsConstructor
public class ConfigValidationV2Controller {

    private final ConfigValidationV2Service configValidationV2Service;

    @PostMapping(value = "/validate")
    public ResponseEntity<ValidationSummary> validate(
            @RequestBody ValidationRequestV2 request) {

        List<ParsedConfigFile> parsedConfigFiles = request.getParsedConfigFiles();
        Phase2ValidationInput userInput = request.getUserInput();

        if (parsedConfigFiles == null || parsedConfigFiles.isEmpty()) {
            throw new InvalidUserValidationInputException("At least one config file is required");
        }

        // Coupled-artifacts gate: both baseline uploads are mandatory on the v2 path.
        if (userInput == null || userInput.getFctData() == null || userInput.getPdqData() == null) {
            throw new Phase2InputsIncompleteException();
        }

        return ResponseEntity.ok(configValidationV2Service.validate(parsedConfigFiles, userInput));
    }
}
