package com.frauscher.ConfigurationValidationService.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiErrorResponse {

    private final String errorCode;
    private final String message;
    private final Instant timestamp;
}
