package com.frauscher.ConfigurationValidationService.dto;

import java.util.List;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/config/v2/validate}. Mirrors {@link ValidationRequestWrapper}
 * but carries the upload-sourced {@link ValidationInputV2} (FCT + PDQ baseline) as its userInput.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequestV2 {
    private List<ParsedConfigFile> parsedConfigFiles;
    private ValidationInputV2 userInput;
}
