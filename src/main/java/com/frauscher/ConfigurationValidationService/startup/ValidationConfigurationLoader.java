package com.frauscher.ConfigurationValidationService.startup;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.frauscher.ConfigurationValidationService.exception.RuleConfigurationException;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.RuleOrigin;
import com.frauscher.ConfigurationValidationService.validation.config.RuleConfigValidator;

@Component
public class ValidationConfigurationLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final RuleConfigValidator validator;
    private final String rulesPath;

    public ValidationConfigurationLoader(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            RuleConfigValidator validator,
            @Value("${validation.configuration.path:ValidationConfiguration.json}") String rulesPath) {

        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.rulesPath = rulesPath;
    }

    /**
     * Loads and validates rule configurations.
     *
     * Contract:
     * - If this method returns rules, they are guaranteed to be VALID
     * - Invalid rules cause startup/runtime failure with clear error
     */
    public List<RuleConfig> loadConfiguredRules() {
        Resource resource = resourceLoader.getResource(resolvePath(rulesPath));

        // mandatory
        if (!resource.exists()) {
            throw new RuleConfigurationException(
                    "Validation Configuration file is missing but is mandatory"
            );
        }

        try (InputStream is = resource.getInputStream()) {

            List<RuleConfig> ruleConfigs = objectMapper.readValue(
                    is,
                    new TypeReference<List<RuleConfig>>() {}
            );

            // must not be empty
            if (ruleConfigs == null || ruleConfigs.isEmpty()) {
                throw new RuleConfigurationException(
                        "Validation Configuration file is empty. At least one rule must be configured"
                );
            }

            for (RuleConfig rule : ruleConfigs) {
                rule.setOrigin(RuleOrigin.CONFIGURED);
            }

            // content must be valid
            validator.validate(ruleConfigs);

            return ruleConfigs;

        } catch (UnrecognizedPropertyException e) {

            String fieldName = e.getPropertyName();
            String location =
                    e.getPath() != null && !e.getPath().isEmpty()
                            ? " at rule index " + e.getPath().get(0).getIndex()
                            : "";

            throw new RuleConfigurationException(
                    "Invalid rule configuration: unrecognized field '" +
                            fieldName + "'" + location,
                    e
            );

        } catch (RuleConfigurationException e) {
            throw e;

        } catch (Exception e) {
            throw new RuleConfigurationException(
                    "Failed to read or parse Validation Configuration file, format is invalid",
                    e
            );
        }

    }


    private String resolvePath(String path) {
        if (path.startsWith("classpath:")
                || path.startsWith("file:")) {
            return path;
        }
        return "classpath:" + path;
    }
}
