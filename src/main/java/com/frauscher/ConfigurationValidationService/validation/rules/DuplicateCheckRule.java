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
@Component
public class DuplicateCheckRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.DUPLICATE_CHECK;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        List<String> values =
                context.fileContext()
                        .values(
                                context.key().getBlock(),
                                context.key().getEntry());

        if (values.isEmpty() || values.get(0) == null) {
            return List.of();
        }

        if (values.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    ValidationConstants.UNIQUE,
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        String value = values.get(0);

        boolean duplicate =
                context.duplicateRegistry().isDuplicate(
                        context.key().getBlock(),
                        context.key().getEntry(),
                        value
                );

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                ValidationConstants.UNIQUE,
                value,
                duplicate
                        ? ValidationStatus.FAIL
                        : ValidationStatus.PASS));
    }
}
