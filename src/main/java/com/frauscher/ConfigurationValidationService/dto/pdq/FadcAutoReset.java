package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.List;

/**
 * Parsed {@code FAdC - FAdC Auto reset} cell from the Control table — a single-operator logic tree
 * (design §6.5). A {@code NA}/blank cell yields {@code null} (no auto-reset), not this object.
 *
 * @param op       {@code "OR"} or {@code "AND"} (null for a single bare operand with no operator)
 * @param operands the referenced track sections (1–8)
 */
public record FadcAutoReset(String op, List<String> operands) {
}