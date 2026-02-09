package com.frauscher.ConfigurationValidationService.validation.context;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DuplicateValueRegistry {

    private final Map<String, Integer> counts = new HashMap<>();

    public synchronized void record(
            String block,
            String entry,
            String value) {

        String key = block + "::" + entry + "::" + value;
        counts.merge(key, 1, Integer::sum);
    }

    public synchronized boolean isDuplicate(
            String block,
            String entry,
            String value) {

        String key = block + "::" + entry + "::" + value;
        return counts.getOrDefault(key, 0) > 1;
    }

    public synchronized void clear() {
        counts.clear();
    }
}
