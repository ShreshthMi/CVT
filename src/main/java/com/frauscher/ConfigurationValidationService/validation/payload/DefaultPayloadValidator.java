package com.frauscher.ConfigurationValidationService.validation.payload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.context.ResolvedPayloadContext;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;

@Component
public class DefaultPayloadValidator implements PayloadValidator {

    @Override
    public ResolvedPayloadContext validate(
            Map<String, Map<String, Object>> payload,
            Map<ValidationKey, List<RuleConfig>> rulesByKey) {

        Map<ValidationKey, ResolvedPayload> resolvedPayloads = new HashMap<>();

        // ------------------------------------------------
        // Resolve ALL payload inputs (rule-independent)
        // ------------------------------------------------
        if (payload != null) {
            for (Map.Entry<String, Map<String, Object>> blockEntry : payload.entrySet()) {

                String block = blockEntry.getKey();
                Map<String, Object> entries = blockEntry.getValue();

                if (entries == null) continue;

                for (Map.Entry<String, Object> entry : entries.entrySet()) {
                    if (entry.getValue() != null) {
                        resolvedPayloads.put(
                                new ValidationKey(block, entry.getKey()),
                                ResolvedPayload.present(entry.getValue())
                        );
                    }
                }
            }
        }

        // ------------------------------------------------
        // Validate payload ONLY for configured rules
        // ------------------------------------------------
        for (Map.Entry<ValidationKey, List<RuleConfig>> entry : rulesByKey.entrySet()) {

            ValidationKey key = entry.getKey();
            List<RuleConfig> rules = entry.getValue();

            if (rules == null || rules.isEmpty()) {
                continue;
            }

            Map<String, Object> blockPayload =
                    payload != null ? payload.get(key.getBlock()) : null;

            for (RuleConfig rule : rules) {
                validateForRule(rule, key, blockPayload, resolvedPayloads);
            }
        }

        return new ResolvedPayloadContext(resolvedPayloads);
    }

    // ------------------------------------------------
    // Payload validation (ONLY PLACE FOR EXCEPTIONS)
    // ------------------------------------------------
    private void validateForRule(
            RuleConfig rule,
            ValidationKey key,
            Map<String, Object> blockPayload,
            Map<ValidationKey, ResolvedPayload> resolvedPayloads) {

        // UIInputRequired enforcement
        if ("Yes".equalsIgnoreCase(rule.getUiInputRequired())
                && !resolvedPayloads.containsKey(key)) {

            throw new InvalidUserValidationInputException(
                    "payload input required for "
                            + key.getBlock() + "::" + key.getEntry());
        }

        RuleType type = RuleType.fromExternal(rule.getRuleType());

        switch (type) {

            case RANGE_CHECK ->
                    validateRangePayload(key, blockPayload, rule);

            case OPTIONAL_INPUT_MATCH,
                 MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH ->
                    validateArrayPayload(key, blockPayload);

            case PROJECT_BLOCK_CHECK ->
                    validateProjectBlockPayload(key, blockPayload);

            default -> {

            }
        }
    }

    private void validateRangePayload(
            ValidationKey key,
            Map<String, Object> blockPayload,
            RuleConfig rule) {
        if (ValidationConstants.NO.equalsIgnoreCase(rule.getUiInputRequired())) {
            return;
        }

        if (blockPayload == null
                || !(blockPayload.get(key.getEntry()) instanceof Map<?, ?> range)
                || !range.containsKey("min")
                || !range.containsKey("max")) {

            throw new InvalidUserValidationInputException(
                    "RangeCheck requires {min, max} for "
                            + key.getBlock() + "::" + key.getEntry());
        }
    }

    private void validateArrayPayload(
            ValidationKey key,
            Map<String, Object> blockPayload) {

        Object val =
                blockPayload != null
                        ? blockPayload.get(key.getEntry())
                        : null;

        if (!(val instanceof List<?>)) {
            throw new InvalidUserValidationInputException(
                    "Array value required for "
                            + key.getBlock() + "::" + key.getEntry());
        }
    }

    private void validateProjectBlockPayload(
            ValidationKey key,
            Map<String, Object> blockPayload) {

        if (blockPayload == null
                || !(blockPayload.get("BLOCK_EXISTS") instanceof Boolean)) {

            throw new InvalidUserValidationInputException(
                    "ProjectBlockCheck requires BLOCK_EXISTS boolean in block "
                            + key.getBlock());
        }
    }
}