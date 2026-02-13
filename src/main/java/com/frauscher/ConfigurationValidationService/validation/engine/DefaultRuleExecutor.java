package com.frauscher.ConfigurationValidationService.validation.engine;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.DefaultRuleFactory;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.context.*;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationContext;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecision;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecisionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DefaultRuleExecutor {

    private final Map<RuleType, ValidationRule> rulesByType;
    private final DuplicateValueRegistry duplicateRegistry;

    public DefaultRuleExecutor(
            List<ValidationRule> rules,
            DuplicateValueRegistry duplicateRegistry) {

        this.rulesByType = new EnumMap<>(RuleType.class);
        for (ValidationRule rule : rules) {
            rulesByType.put(rule.supportedType(), rule);
        }
        this.duplicateRegistry = duplicateRegistry;
    }

    public List<ValidationResult> executeDefault(
            ParsedConfigFile file,
            FileContext fileContext,
            ValidationKey key,
            ResolvedPayloadContext inputContext) {

        RuleConfig defaultRule =
                DefaultRuleFactory.inputMatch(
                        key.getBlock(),
                        key.getEntry());

        ValidationDecision decision =
                ValidationDecisionEngine.decide(
                        new ValidationContext(
                                inputContext.payloadFor(key).isPresent(),
                                defaultRule,
                                file));

        if (decision == ValidationDecision.IGNORE) {
            return List.of();
        }

        ValidationRule inputMatch =
                rulesByType.get(RuleType.INPUT_MATCH);

        if (inputMatch == null) {
            throw new IllegalStateException(
                    "INPUT_MATCH rule not registered");
        }

        RuleExecutionContext context =
                new RuleExecutionContext(
                        fileContext,
                        key,
                        defaultRule,
                        inputContext.payloadFor(key),
                        inputContext,              
                        duplicateRegistry);

        return inputMatch.execute(context);
    }
}
