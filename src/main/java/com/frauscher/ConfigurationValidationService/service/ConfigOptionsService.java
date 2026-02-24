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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConfigOptionsService {

    private List<ConfigOptions> configOptions;

    @PostConstruct
    private void loadConfigOptions() {
        Properties properties = new Properties();

        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("value-mappings.properties")) {

            if (inputStream == null) {
                throw new IOException("value-mappings.properties file not found in classpath");
            }
            properties.load(inputStream);

        } catch (IOException e) {
            log.error("Failed to load value-mappings.properties", e);
            configOptions = List.of();
            return;
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

            // Parse description: "0 - normal" → description = "normal"
            // Fallback for entries without " - " separator: use full value
            String description;
            int separatorIndex = rawValue.indexOf(" - ");
            if (separatorIndex >= 0) {
                description = rawValue.substring(separatorIndex + 3).trim();
            } else {
                description = rawValue;
            }

            grouped.computeIfAbsent(paramName, k -> new ArrayList<>())
                    .add(new OptionMapping(optionKey, description));
        });

        configOptions = grouped.entrySet().stream()
                .map(entry -> new ConfigOptions(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<ConfigOptions> getConfigOptions() {
        return configOptions;
    }
}
