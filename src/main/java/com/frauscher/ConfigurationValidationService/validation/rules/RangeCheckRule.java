package com.frauscher.ConfigurationValidationService.validation.rules;
import static com.frauscher.ConfigurationValidationService.validation.ValidationResultFactory.create;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleType;
import com.frauscher.ConfigurationValidationService.validation.ValidationRule;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;
import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

@Component
public class RangeCheckRule implements ValidationRule {

    @Override
    public RuleType supportedType() {
        return RuleType.RANGE_CHECK;
    }

    @Override
    public List<ValidationResult> execute(RuleExecutionContext context) {

        Integer min = context.rule().getMin();
        Integer max = context.rule().getMax();

        ResolvedPayload payload = context.payload();

        if (payload.isPresent() && payload.raw() instanceof Map<?, ?> map) {
            min = ((Number) map.get("min")).intValue();
            max = ((Number) map.get("max")).intValue();
        }

        List<String> values =
                context.fileContext()
                        .values(
                                context.key().getBlock(),
                                context.key().getEntry());

        if (values.isEmpty()) {
            return List.of(create(
                    context.fileContext().file(),
                    context.rule(),
                    min + " - " + max,
                    ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND,
                    ValidationStatus.FAIL
            ));
        }

        int actual = Integer.parseInt(values.get(0));
        boolean pass = actual >= min && actual <= max;

        return List.of(create(
                context.fileContext().file(),
                context.rule(),
                min + " - " + max,
                String.valueOf(actual),
                pass ? ValidationStatus.PASS : ValidationStatus.FAIL
        ));
    }
}
