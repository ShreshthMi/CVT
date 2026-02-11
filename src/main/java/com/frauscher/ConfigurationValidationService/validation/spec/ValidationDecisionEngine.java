package com.frauscher.ConfigurationValidationService.validation.spec;

import com.frauscher.ConfigurationValidationService.model.RuleConfig;

public final class ValidationDecisionEngine {

	private ValidationDecisionEngine() {
	}

	public static ValidationDecision decide(ValidationContext ctx) {

		RuleConfig rule = ctx.getRule();

		if (rule != null && !ctx.isRuleApplicableForFile()) {
			return ValidationDecision.IGNORE;
		}

		if (ctx.shouldSkipForComFile()) {
			return ValidationDecision.IGNORE;
		}

		boolean payloadPresent = ctx.isPayloadPresent();

		if (rule == null) {
			return payloadPresent ? ValidationDecision.APPLY_DEFAULT : ValidationDecision.IGNORE;
		}

		return ValidationDecision.APPLY_RULE;
	}
}
