package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.List;

/**
 * The validate-time Expectations JSON (design §4.1): the set of baseline-derived expected values the
 * engine resolves actual ADC values against. Built fresh per request, stateless.
 */
public record Expectations(List<Expectation> entries) {

    public static Expectations empty() {
        return new Expectations(List.of());
    }
}
