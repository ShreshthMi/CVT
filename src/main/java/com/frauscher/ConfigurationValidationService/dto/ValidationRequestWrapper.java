package com.frauscher.ConfigurationValidationService.dto;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequestWrapper {
    private List<ParsedConfigFile> parsedConfigFiles;
    private UserValidationInputCriteria userInput;
}
