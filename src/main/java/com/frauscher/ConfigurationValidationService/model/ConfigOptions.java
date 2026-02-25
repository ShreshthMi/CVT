package com.frauscher.ConfigurationValidationService.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ConfigOptions {

    private String key;
    private List<OptionMapping> optionMappings;
}
