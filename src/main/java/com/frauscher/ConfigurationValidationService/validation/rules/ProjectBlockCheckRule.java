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
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;

@Component
public class ProjectBlockCheckRule implements ValidationRule {

    private static final String BLOCK_EXISTS = "BLOCK_EXISTS";

    @Override
    public RuleType supportedType() {
        return RuleType.PROJECT_BLOCK_CHECK;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        String block = context.key().getBlock();
        String entry = context.key().getEntry();

        ValidationKey blockExistsKey =
                new ValidationKey(block, BLOCK_EXISTS);

        boolean shouldExist =
                Boolean.parseBoolean(
                        context.resolvedPayloadContext()
                                .payloadFor(blockExistsKey)
                                .asString()
                );

        boolean blockExists =
                context.fileContext().hasBlock(block);

        if (!shouldExist) {

            if (blockExists) {

                String actual =
                        ValidationConstants.CONFIG_BLOCK_FOUND
                                + " ("
                                + entry
                                + "="
                                + context.fileContext()
                                .values(block, entry)
                                .stream()
                                .findFirst()
                                .orElse("UNKNOWN")
                                + ")";

                return List.of(create(
                        context.fileContext().file(),
                        context.rule(),
                        ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                        actual,
                        ValidationStatus.FAIL
                ));
            }

            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.PASS
            ));
        }

        if (!blockExists) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    context.payload().asString(), // PROJECT_NUMBER
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        List<String> values =
                context.fileContext().values(block, entry);

        if (values.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    context.payload().asString(),
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        String actual = values.get(0);
        String expected = context.payload().asString();

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                expected,
                actual,
                expected.equals(actual)
                        ? ValidationStatus.PASS
                        : ValidationStatus.FAIL
        ));
    }
}