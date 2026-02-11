package com.frauscher.ConfigurationValidationService.validation.payload;

import java.util.List;
import java.util.Map;

import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.context.ResolvedPayloadContext;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;

public interface PayloadValidator {

    /**
     * Validates payload structure AND resolves UI expected values.
     */
    ResolvedPayloadContext validate(
            Map<String, Map<String, Object>> payload,
            Map<ValidationKey, List<RuleConfig>> rulesByKey);
}
