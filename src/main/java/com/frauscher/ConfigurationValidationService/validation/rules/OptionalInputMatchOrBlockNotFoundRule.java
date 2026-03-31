package com.frauscher.ConfigurationValidationService.validation.rules;

import static com.frauscher.ConfigurationValidationService.validation.ValidationResultFactory.create;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;
import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

@Component
public class OptionalInputMatchOrBlockNotFoundRule implements ValidationRule {

    private final OptionalInputMatchRule optionalInputMatchRule;

    public OptionalInputMatchOrBlockNotFoundRule(OptionalInputMatchRule optionalInputMatchRule) {
        this.optionalInputMatchRule = optionalInputMatchRule;
    }

    @Override
    public RuleType supportedType() {
        return RuleType.OPTIONAL_INPUT_MATCH_OR_BLOCK_NOT_FOUND;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        ResolvedPayload payload = context.payload();

        if (!payload.isPresent()) {
            return List.of();
        }

        if (!context.fileContext().hasBlock(context.key().getBlock())) {

            Set<String> expectedValues = payload.asStringList()
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.toSet());

            String defaultValue = context.rule().getDefaultValue();
            boolean matchesDefault = expectedValues.contains(defaultValue);

            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    expectedValues.toString(),
                    ValidationConstants.CONFIG_BLOCK_NOT_FOUND,
                    matchesDefault ? ValidationStatus.PASS : ValidationStatus.FAIL));
        }

        return optionalInputMatchRule.execute(context);
    }
}
