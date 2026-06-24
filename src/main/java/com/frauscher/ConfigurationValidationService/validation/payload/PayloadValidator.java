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

    /**
     * Resolves the supplied expected values into a {@link ResolvedPayloadContext} <b>without</b> the
     * UI-input enforcement (mandatory-presence + payload-type checks) {@link #validate} applies. Used by
     * the v2 path, whose expectations are derived from the PDQ/FCT baseline rather than user UI inputs:
     * a value the baseline does not supply simply leaves its rule dormant instead of being rejected.
     */
    ResolvedPayloadContext resolve(Map<String, Map<String, Object>> payload);
}
