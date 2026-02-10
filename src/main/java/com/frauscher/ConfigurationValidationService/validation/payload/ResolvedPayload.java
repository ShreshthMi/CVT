package com.frauscher.ConfigurationValidationService.validation.payload;

import java.util.List;

public final class ResolvedPayload {

    private final boolean present;
    private final Object value;

    private ResolvedPayload(boolean present, Object value) {
        this.present = present;
        this.value = value;
    }

    public static ResolvedPayload present(Object value) {
        return new ResolvedPayload(true, value);
    }

    public static ResolvedPayload missing() {
        return new ResolvedPayload(false, null);
    }

    public boolean isPresent() {
        return present;
    }

    public String asString() {
        return value == null ? null : String.valueOf(value);
    }

    public Object raw() {
        return value;
    }

    public List<String> asStringList() {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }


}