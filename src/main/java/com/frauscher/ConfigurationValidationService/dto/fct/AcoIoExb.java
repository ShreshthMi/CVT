package com.frauscher.ConfigurationValidationService.dto.fct;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An ACO-mode IoExb attached to an AEB. Each {@code OutputFma} is resolved (FMA internId →
 * name / {@code fmaId} / parent-AEB {@code <Id>}). Single-track ACOs have no OutputFma2, so those
 * fields are omitted from the JSON. Design §5.5.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AcoIoExb(
        String label,
        String outputFma1Name,
        String outputFma1Id,
        String outputFma1DpId,
        String outputFma2Name,
        String outputFma2Id,
        String outputFma2DpId) {
}