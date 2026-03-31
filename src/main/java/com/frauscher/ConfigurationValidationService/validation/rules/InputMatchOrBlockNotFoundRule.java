package com.frauscher.ConfigurationValidationService.validation.rules;

import static com.frauscher.ConfigurationValidationService.validation.ValidationResultFactory.create;

import java.util.List;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;
import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

@Component
public class InputMatchOrBlockNotFoundRule implements ValidationRule {

    private final InputMatchRule inputMatchRule;

    public InputMatchOrBlockNotFoundRule(InputMatchRule inputMatchRule) {
        this.inputMatchRule = inputMatchRule;
    }

    @Override
    public RuleType supportedType() {
        return RuleType.INPUT_MATCH_OR_BLOCK_NOT_FOUND;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        if (!context.fileContext().hasBlock(context.key().getBlock())) {

            ResolvedPayload payload = context.payload();
            String expected = payload.asString();

            if (!payload.isPresent()) {
                return List.of(create(
                        context.fileContext().file(),
                        context.rule(),
                        expected,
                        ValidationConstants.CONFIG_BLOCK_NOT_FOUND,
                        ValidationStatus.PASS));
            }

            String defaultValue = context.rule().getDefaultValue();
            boolean matchesDefault = defaultValue.equals(expected);
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    expected,
                    ValidationConstants.CONFIG_BLOCK_NOT_FOUND,
                    matchesDefault ? ValidationStatus.PASS : ValidationStatus.FAIL));
        }

        return inputMatchRule.execute(context);
    }
}
