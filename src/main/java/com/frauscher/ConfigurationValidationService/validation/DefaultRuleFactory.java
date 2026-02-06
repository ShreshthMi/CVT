package com.frauscher.ConfigurationValidationService.validation;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;

public final class DefaultRuleFactory {

    private DefaultRuleFactory() {
    }

    public static RuleConfig inputMatch(String blockName, String entryKey) {

        RuleConfig rule = new RuleConfig();
        rule.setRuleType(RuleType.INPUT_MATCH.getExternalName());
        rule.setConfigBlockName(blockName);
        rule.setConfigEntryKey(entryKey);

        rule.setSkipComFile(true);
        rule.setUiInputRequired(ValidationConstants.NO);

        rule.setOrigin(RuleOrigin.DEFAULT);

        return rule;
    }
}