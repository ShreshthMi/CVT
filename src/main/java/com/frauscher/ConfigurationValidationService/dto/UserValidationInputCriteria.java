package com.frauscher.ConfigurationValidationService.dto;


import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserValidationInputCriteria {

    private final Map<String, Map<String, Object>> sections = new HashMap<>();

    @JsonAnySetter
    public void addSection(String sectionName, Map<String, Object> rules) {
        sections.put(sectionName, rules);
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }
}