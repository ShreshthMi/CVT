package com.frauscher.ConfigurationValidationService.dto.pdq;

/**
 * A Data Transmission "Output Data Transmission" row (validate-time block {@code CFG_DATA_OUT}).
 * Numeric fields are kept as strings for consistency with {@code cqIrParameters}.
 *
 * @param sourceDpName DP from which the DT bits are received (ADC: identified via the ID key)
 * @param nmbrOut      number of outputs to be output (0–15)
 * @param position     position of the output info in the sender ID data packet (0–31)
 */
public record OutputDataTransmission(String sourceDpName, String nmbrOut, String position) {
}