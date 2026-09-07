package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Control table track-section row (cols A–I). Design §6.5. {@code trackType} is the translated
 * Track Output ({@code PHYSICAL}→{@code MAIN}, {@code VIRTUAL}→{@code COMBINATION}). {@code serialNo}
 * is kept as a string for consistency with the string-valued {@code cqIrParameters}.
 *
 * <p>{@code location} is the station code of the most recent {@code LOCATION - <code>} banner above
 * this row, or {@code null} in a single-location workbook that carries no banners. A track's identity
 * is {@code (location, name)} — two locations in one project may hold track sections with identical
 * names, which is precisely why the tag exists. Omitted from the JSON when null, so an untagged
 * workbook's response is unchanged.</p>
 */
public record TrackSection(
        String serialNo,
        String name,
        List<String> dpIn,
        List<String> dpOut,
        String resetType,
        String trackType,
        FadcAutoReset fadcAutoReset,
        boolean autoResetByTimer,
        @JsonInclude(JsonInclude.Include.NON_NULL) String location) {
}
