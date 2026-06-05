package com.frauscher.ConfigurationValidationService.dto.fct;

import java.util.List;

/** The parsed FCT baseline — one chain per CAN segment. Body of {@code POST /api/upload/fct}. */
public record ComAebMap(List<Chain> chains) {
}