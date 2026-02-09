package com.frauscher.ConfigurationValidationService.service.extractors;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ValueMappingService {

    private Properties properties;

    @PostConstruct
    private void initializeProperties() {
        properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("value-mappings.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                throw new RuntimeException("value-mappings.properties file not found in classpath");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load value mappings", e);
        }
    }

    /**
     * Generic method to map any field value to its description
     * Example: mapValue("COMM_FAIL", "0") -> "0 - normal"
     *          mapValue("BEHAV_GE", "1") -> "1 - No Reset Restriction"
     */
    public String mapValue(String fieldKey, String value) {
        if (value == null) return "";
        String key = fieldKey + "." + value;
        return properties.getProperty(key, value);
    }
}
