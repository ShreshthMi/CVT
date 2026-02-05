package com.frauscher.ConfigurationValidationService.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigEntry {
    private String key;
    private int bits;
    private String value;
    private String comment;
}
