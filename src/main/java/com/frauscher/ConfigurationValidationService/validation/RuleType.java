package com.frauscher.ConfigurationValidationService.validation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.frauscher.ConfigurationValidationService.exception.RuleConfigurationException;

public enum RuleType {

    INPUT_MATCH("InputMatch"),
    INPUT_MATCH_OR_BLOCK_NOT_FOUND("InputMatchOrBlockNotFound"),
    OPTIONAL_INPUT_MATCH("OptionalInputMatch"),
    RANGE_CHECK("RangeCheck"),
    DUPLICATE_CHECK("DuplicateCheck"),
    MULTIPLE_BLOCK_SINGLE_INPUT_MATCH("MultipleBlockSingleInputMatch"),
    MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH("MultipleBlockMultipleInputMatch"),
    PROJECT_BLOCK_CHECK("ProjectBlockCheck");

    private final String externalName;

    RuleType(String externalName) {
        this.externalName = externalName;
    }

    public String getExternalName() {
        return externalName;
    }

    public static Set<String> supportedExternalNames() {
        return Arrays.stream(values())
                .map(RuleType::getExternalName)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static RuleType fromExternal(String value) {

        if (value == null || value.isBlank()) {
            throw new RuleConfigurationException("ruleType is mandatory");
        }

        return Arrays.stream(values())
                .filter(t -> t.externalName.equals(value))
                .findFirst()
                .orElseThrow(() ->
                        new RuleConfigurationException(
                                "Unsupported ruleType: " + value +
                                        ". Supported values: " + supportedExternalNames()
                        )
                );
    }
}
