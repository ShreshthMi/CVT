package com.frauscher.ConfigurationValidationService.dto.pdq;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Control table DP-table row (cols K–N). Design §6.5. {@code position} is ABOVE/BELOW THE RAIL.
 *
 * <p>{@code location} is the station code of the most recent {@code LOCATION - <code>} banner above
 * this row, or {@code null} when the sub-table carries no banners. DP names repeat across locations,
 * so the row's identity is {@code (location, name)}. Omitted from the JSON when null.</p>
 */
public record DpTableRow(
        String serialNo,
        String name,
        String position,
        boolean eChc,
        @JsonInclude(JsonInclude.Include.NON_NULL) String location) {
}
