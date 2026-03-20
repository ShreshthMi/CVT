package com.frauscher.ConfigurationValidationService.validation.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.RuleExecutionResult;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.context.DuplicateValueRegistry;
import com.frauscher.ConfigurationValidationService.validation.context.FileContext;
import com.frauscher.ConfigurationValidationService.validation.context.ResolvedPayloadContext;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationContext;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecision;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecisionEngine;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RuleExecutionEngine {

    private final Map<RuleType, ValidationRule> rulesByType;
    private final DuplicateValueRegistry duplicateRegistry;

    public RuleExecutionEngine(
            List<ValidationRule> rules,
            DuplicateValueRegistry duplicateRegistry) {

        this.rulesByType = new EnumMap<>(RuleType.class);
        for (ValidationRule rule : rules) {
            rulesByType.put(rule.supportedType(), rule);
        }
        this.duplicateRegistry = duplicateRegistry;
    }

    public RuleExecutionResult execute(
            ParsedConfigFile file,
            FileContext fileContext,
            ValidationKey key,
            List<RuleConfig> rules,
            ResolvedPayloadContext inputContext) {

        boolean configuredRuleExecuted = false;
        List<ValidationResult> results = new ArrayList<>();

        boolean hasProjectBlockCheckForBlock =
                rules.stream().anyMatch(r ->
                        RuleType.fromExternal(r.getRuleType()) == RuleType.PROJECT_BLOCK_CHECK
                                && r.getConfigBlockName().equals(key.getBlock())
                );

        for (RuleConfig rule : rules) {

            if (!isFileEligible(rule, file)) {
                continue;
            }

            RuleType type = RuleType.fromExternal(rule.getRuleType());

            boolean payloadPresent =
                    inputContext.payloadFor(key).isPresent();

            ValidationDecision decision =
                    ValidationDecisionEngine.decide(
                            new ValidationContext(payloadPresent, rule, file));

            if (decision == ValidationDecision.IGNORE) {
                continue;
            }

            ValidationRule impl = rulesByType.get(type);

            if (impl == null) {
                log.warn(
                        "No ValidationRule implementation found for type [{}]",
                        rule.getRuleType());
                continue;
            }

            RuleExecutionContext context =
                    new RuleExecutionContext(
                            fileContext,
                            key,
                            rule,
                            inputContext.payloadFor(key),
                            inputContext,
                            duplicateRegistry);
            results.addAll(impl.execute(context));
            configuredRuleExecuted = true;
        }

        // If ProjectBlockCheck exists for this block, treat rule as executed to suppress default
        if (hasProjectBlockCheckForBlock) {
            configuredRuleExecuted = true;
        }

        return new RuleExecutionResult(results, configuredRuleExecuted);
    }

    private boolean isFileEligible(
            RuleConfig rule,
            ParsedConfigFile file) {

        String marker = rule.getValidateOnlyInFilesWith();

        if (marker == null) {
            return true;
        }

        return switch (marker.toUpperCase()) {
            case "ACOIOEXBDETAILS" -> file.isAcoIoexbDetails();
            case "DTIOEXBDETAILS" -> file.isDtIoexbDetails();
            case "TRACKSECTIONDETAILS" -> file.isTrackSectionDetails();
            case "COMDETAILS" -> file.isComDetails();
            default -> false;
        };
    }
}