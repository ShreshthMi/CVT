package com.frauscher.ConfigurationValidationService.validation.rules;

import static com.frauscher.ConfigurationValidationService.validation.ValidationResultFactory.create;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
public class MultipleBlockMultipleInputMatchRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        ResolvedPayload payload = context.payload();

        if (!payload.isPresent()) {
            return List.of();
        }

        Set<String> allowedValues =
                payload.asStringList()
                        .stream()
                        .map(String::valueOf)
                        .collect(Collectors.toCollection(TreeSet::new));

        List<String> actualValues =
                context.fileContext()
                        .values(
                                context.key().getBlock(),
                                context.key().getEntry())
                        .stream()
                        .sorted()
                        .toList();

        // ---------------------------------------------
        // BLOCK OR ENTRY NOT FOUND
        // ---------------------------------------------
        if (actualValues.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    allowedValues.toString(),
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        // ---------------------------------------------
        // Match check
        // ---------------------------------------------
        boolean allMatch =
                actualValues.stream()
                        .allMatch(allowedValues::contains);

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                allowedValues.toString(),
                "[" + String.join(",", actualValues) + "]",
                allMatch ? ValidationStatus.PASS : ValidationStatus.FAIL
        ));
    }
}
