package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.List;
import java.util.Map;

/**
 * The validate-time expectations the engine resolves actual ADC values against, in two buckets
 * (v2-expectations-contract.md §4–5): {@code scalarExpectations} (block → entry → value, validated
 * every-occurrence by the reused Phase 1 engine) and {@code instancedExpectations} (per-entity, keyed
 * by file + match strategy). Built fresh per request, stateless.
 */
public record Expectations(
        Map<String, Map<String, Object>> scalarExpectations,
        List<InstancedExpectation> instancedExpectations) {

    public static Expectations empty() {
        return new Expectations(Map.of(), List.of());
    }
}
