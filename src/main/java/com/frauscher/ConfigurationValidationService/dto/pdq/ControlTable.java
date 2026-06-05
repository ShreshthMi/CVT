package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.List;

/** Parsed Control table sheet — the two side-by-side sub-tables (design §6.5). */
public record ControlTable(List<TrackSection> trackSections, List<DpTableRow> dpTable) {
}