package com.frauscher.ConfigurationValidationService.dto.pdq;

/** A Control table DP-table row (cols J–M). Design §6.5. {@code position} is ABOVE/BELOW THE RAIL. */
public record DpTableRow(String serialNo, String name, String position, boolean eChc) {
}