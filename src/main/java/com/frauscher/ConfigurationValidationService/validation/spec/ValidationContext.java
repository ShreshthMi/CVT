package com.frauscher.ConfigurationValidationService.validation.spec;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;

public class ValidationContext {

	private final boolean payloadPresent;
	private final RuleConfig rule;
	private final ParsedConfigFile file;

	public ValidationContext(boolean payloadPresent, RuleConfig rule, ParsedConfigFile file) {
		this.payloadPresent = payloadPresent;
		this.rule = rule;
		this.file = file;
	}

	public boolean isPayloadPresent() {
		return payloadPresent;
	}

	public RuleConfig getRule() {
		return rule;
	}

	public ParsedConfigFile getFile() {
		return file;
	}

	public boolean hasRule() {
		return rule != null;
	}

	public boolean isUiInputRequired() {
		return rule != null && ValidationConstants.YES.equalsIgnoreCase(rule.getUiInputRequired());
	}

	public boolean isRuleApplicableForFile() {
		if (rule == null || rule.getValidateOnlyInFilesWith() == null) {
			return true; 
		}
		FileApplicability applicability = FileApplicability.from(rule.getValidateOnlyInFilesWith());
		return applicability.applies(file);
	}

	public boolean shouldSkipForComFile() {
		boolean skipCom = rule == null || rule.getSkipComFile() == null || Boolean.TRUE.equals(rule.getSkipComFile());

		return skipCom && file.isComDetails();
	}
}
