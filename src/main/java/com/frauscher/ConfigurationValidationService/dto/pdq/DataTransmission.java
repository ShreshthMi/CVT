package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.List;

/** Parsed Data Transmission Inputs sheet — the two sub-tables (design §6.6). */
public record DataTransmission(
        List<DataSafetyLevel> dataSafetyLevels,
        List<OutputDataTransmission> outputDataTransmission) {
}