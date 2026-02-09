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
public class OptionalInputMatchRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.OPTIONAL_INPUT_MATCH;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        ResolvedPayload payload = context.payload();

        if (!payload.isPresent()) {
            return List.of();
        }

        Set<String> expectedValues =
                payload.asStringList()
                        .stream()
                        .map(String::valueOf)
                        .collect(Collectors.toSet());

        List<String> actualValues =
                context.fileContext()
                        .values(
                                context.key().getBlock(),
                                context.key().getEntry());

        if (actualValues.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    expectedValues.toString(),
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        boolean matches =
                actualValues.stream()
                        .anyMatch(expectedValues::contains);

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                expectedValues.toString(),
                String.join(",", actualValues),
                matches ? ValidationStatus.PASS : ValidationStatus.FAIL
        ));
    }
}
