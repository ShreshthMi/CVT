package com.frauscher.ConfigurationValidationService.validation.context;

import java.util.Map;

import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

public class ResolvedPayloadContext {

    private final Map<ValidationKey, ResolvedPayload> payloads;

    public ResolvedPayloadContext(Map<ValidationKey, ResolvedPayload> payloads) {
        this.payloads = payloads;
    }

    public ResolvedPayload payloadFor(ValidationKey key) {
        return payloads.getOrDefault(key, ResolvedPayload.missing());
    }
}
