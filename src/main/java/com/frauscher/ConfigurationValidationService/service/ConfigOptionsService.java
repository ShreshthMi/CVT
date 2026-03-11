package com.frauscher.ConfigurationValidationService.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.model.ConfigOptions;
import com.frauscher.ConfigurationValidationService.model.OptionMapping;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConfigOptionsService {

    private final List<ConfigOptions> configOptions;

    public ConfigOptionsService() {
        this.configOptions = loadConfigOptions();
    }

    private List<ConfigOptions> loadConfigOptions() {
        Properties properties = new Properties();

        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("value-mappings.properties")) {

            if (inputStream == null) {
                throw new IOException("value-mappings.properties file not found in classpath");
            }
            properties.load(inputStream);

        } catch (IOException e) {
            log.error("Failed to load value-mappings.properties", e);
            return List.of();
        }

        Map<String, List<OptionMapping>> grouped = new LinkedHashMap<>();

        properties.stringPropertyNames().stream().sorted().forEach(propKey -> {
            int dotIndex = propKey.indexOf('.');
            if (dotIndex < 0) {
                return;
            }

            String paramName = propKey.substring(0, dotIndex);
            String optionKey = propKey.substring(dotIndex + 1);
            String rawValue = properties.getProperty(propKey, "").trim();

            // Range-based metadata keys have a redundant prefix (e.g., "min - 0", "description - ...")
            // Strip the prefix for these; return raw value as-is for discrete options
            String value;
            boolean isRangeMetadata = optionKey.equals("min") || optionKey.equals("max")
                    || optionKey.equals("step") || optionKey.equals("description");
            int separatorIndex = rawValue.indexOf(" - ");
            if (isRangeMetadata && separatorIndex >= 0) {
                value = rawValue.substring(separatorIndex + 3).trim();
            } else {
                value = rawValue;
            }

            grouped.computeIfAbsent(paramName, k -> new ArrayList<>())
                    .add(new OptionMapping(optionKey, value));
        });

        return grouped.entrySet().stream()
                .map(entry -> new ConfigOptions(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<ConfigOptions> getConfigOptions() {
        return configOptions;
    }
}
