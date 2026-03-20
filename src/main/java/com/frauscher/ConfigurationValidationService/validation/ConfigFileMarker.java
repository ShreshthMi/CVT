package com.frauscher.ConfigurationValidationService.validation;

import java.util.Arrays;

public enum ConfigFileMarker {

    ACOIOEXBDETAILS,
    DTIOEXBDETAILS,
    TRACKSECTIONDETAILS,
    COMDETAILS;

    public static boolean isValid(String value) {
        if (value == null) {
            return true;
        }

        return Arrays.stream(values())
                .anyMatch(v -> v.name().equalsIgnoreCase(value));
    }
}
