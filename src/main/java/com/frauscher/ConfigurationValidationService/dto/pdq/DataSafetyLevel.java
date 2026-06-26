package com.frauscher.ConfigurationValidationService.dto.pdq;

/**
 * A Data Transmission "Data Safety Level" row (validate-time block {@code CFG_DATA_SAFETY_LEVELS}).
 * Numeric fields are kept as strings for consistency with {@code cqIrParameters}.
 *
 * @param dpName           DP where the DATA_SAFETY_LEVEL block is found (literal)
 * @param safetyLevelIn    input data safety level (0–3)
 * @param safetyLevelOut   output data safety level (0–3)
 * @param safeOutFdbckQuad safe-output feedback (0/1)
 */
public record DataSafetyLevel(String dpName, String safetyLevelIn, String safetyLevelOut, String safeOutFdbckQuad) {
}