package com.frauscher.ConfigurationValidationService.model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConfigBlock {
    private String name;
    private int sequenceNumber;
    private int totalOccurrence;
    private int startSequenceNumber;
    private int endSequenceNumber;
    private int blockIndex;
    private List<ConfigEntry> entries = new ArrayList<>();
}