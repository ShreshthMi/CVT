package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.List;

/**
 * A Control table track-section row (cols A–H). Design §6.5. {@code trackType} is the translated
 * Track Output ({@code PHYSICAL}→{@code MAIN}, {@code VIRTUAL}→{@code COMBINATION}). {@code serialNo}
 * is kept as a string for consistency with the string-valued {@code cqIrParameters}.
 */
public record TrackSection(
        String serialNo,
        String name,
        List<String> dpIn,
        List<String> dpOut,
        String resetType,
        String trackType,
        FadcAutoReset fadcAutoReset,
        boolean autoResetByTimer) {
}