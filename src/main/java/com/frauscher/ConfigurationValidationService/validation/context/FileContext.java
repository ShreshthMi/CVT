package com.frauscher.ConfigurationValidationService.validation.context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

public class FileContext {

    private final ParsedConfigFile file;

    public FileContext(ParsedConfigFile file) {
        this.file = file;
    }

    public ParsedConfigFile file() {
        return file;
    }

    public boolean hasBlock(String blockName) {
        return file.getBlocks().stream()
                .anyMatch(b -> b.getName().equals(blockName));
    }

    public List<String> values(String blockName, String entryKey) {
        return file.getBlocks().stream()
                .filter(b -> b.getName().equals(blockName))
                .flatMap(b -> b.getEntries().stream())
                .filter(e -> entryKey.equals(e.getKey()))
                .map(ConfigEntry::getValue)
                .toList();
    }
}
