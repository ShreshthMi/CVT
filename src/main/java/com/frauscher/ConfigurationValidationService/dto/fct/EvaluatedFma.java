package com.frauscher.ConfigurationValidationService.dto.fct;

/**
 * An FMA belonging to an AEB (from the AEB's nested {@code <Fmas><Fma>}). Design §5.5.
 *
 * @param fmaName from {@code Fma@name}
 * @param fmaId   from {@code Fma@fmaId}
 * @param dpId    the parent AEB's {@code <Id>}
 */
public record EvaluatedFma(String fmaName, String fmaId, String dpId) {
}