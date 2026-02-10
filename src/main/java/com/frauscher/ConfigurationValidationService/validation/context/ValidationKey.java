package com.frauscher.ConfigurationValidationService.validation.context;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class ValidationKey {

    private final String block;
    private final String entry;

    public ValidationKey(String block, String entry) {
        this.block = block;
        this.entry = entry;
    }

    public String getBlock() {
        return block;
    }

    public String getEntry() {
        return entry;
    }
}
