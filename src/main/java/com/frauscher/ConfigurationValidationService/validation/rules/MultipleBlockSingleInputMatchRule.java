package com.frauscher.ConfigurationValidationService.validation.rules;

import static com.frauscher.ConfigurationValidationService.validation.ValidationResultFactory.create;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;
import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

@Component
public class MultipleBlockSingleInputMatchRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.MULTIPLE_BLOCK_SINGLE_INPUT_MATCH;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        ResolvedPayload payload = context.payload();

        if (!payload.isPresent()) {
            return List.of();
        }

        String expected = payload.asString();

        List<String> values = new ArrayList<>();
        boolean entryMissing = false;
        boolean blockFound = false;

        for (ConfigBlock block : context.fileContext().file().getBlocks()) {

            if (!context.key().getBlock().equals(block.getName())) {
                continue;
            }

            blockFound = true;
            boolean foundInBlock = false;

            for (ConfigEntry entry : block.getEntries()) {
                if (context.key().getEntry().equals(entry.getKey())) {
                    values.add(entry.getValue());
                    foundInBlock = true;
                    break;
                }
            }

            if (!foundInBlock) {
                entryMissing = true;
            }
        }

        if (!blockFound || entryMissing) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    expected,
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL));
        }


        boolean allSame = values.stream().distinct().count() == 1;
        boolean matches = values.stream().allMatch(expected::equals);

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                expected,
                "VALUES_FOUND=" + values,
                allSame && matches
                        ? ValidationStatus.PASS
                        : ValidationStatus.FAIL));
    }
}
