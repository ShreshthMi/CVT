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
public class InputMatchRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.INPUT_MATCH;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        ResolvedPayload payload = context.payload();

        if (!payload.isPresent()) {
            return List.of();
        }

        String expected = payload.asString();

        List<String> actuals =
                context.fileContext()
                        .values(
                                context.key().getBlock(),
                                context.key().getEntry());

        if (actuals.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    expected,
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        boolean matches =
                actuals.stream().allMatch(expected::equals);

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                expected,
                String.join(",", actuals),
                matches ? ValidationStatus.PASS : ValidationStatus.FAIL
        ));
    }
}