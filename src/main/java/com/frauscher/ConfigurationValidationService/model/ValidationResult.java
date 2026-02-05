package com.frauscher.ConfigurationValidationService.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationResult {

    private String fileName;
    private String ruleType;
    private String blockName;
    private String entryKey;
    private String expectedValue;
    private String actualValue;
    private String status; // PASS / FAIL
}