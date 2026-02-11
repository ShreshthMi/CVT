package com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common;

import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test context for storing state across step definitions.
 * Thread-safe implementation using ThreadLocal pattern.
 */
public class TestContext {

    private static final ThreadLocal<TestContext> THREAD_LOCAL = ThreadLocal.withInitial(TestContext::new);
    
    private Map<String, Object> payload;
    private List<RuleConfig> ruleConfigs;
    private Exception thrownException;
    private List<ValidationResult> validationResults;
    private Map<String, String> configFiles;
    private String currentFileType;
    private boolean isComFile;
    private Map<String, String> duplicateRegistry;
    private boolean skipComFileDefault;

    public static TestContext get() {
        return THREAD_LOCAL.get();
    }
    
    public static void clear() {
        THREAD_LOCAL.remove();
    }

    public void reset() {
        this.payload = null;
        this.ruleConfigs = null;
        this.thrownException = null;
        this.validationResults = null;
        this.configFiles = new HashMap<>();
        this.currentFileType = null;
        this.isComFile = false;
        this.duplicateRegistry = new HashMap<>();
        this.skipComFileDefault = false;
    }

    // Payload
    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    // Rule Configs
    public List<RuleConfig> getRuleConfigs() {
        return ruleConfigs;
    }

    public void setRuleConfigs(List<RuleConfig> ruleConfigs) {
        this.ruleConfigs = ruleConfigs;
    }

    // Exception
    public Exception getThrownException() {
        return thrownException;
    }

    public void setThrownException(Exception thrownException) {
        this.thrownException = thrownException;
    }

    // Validation Results
    public List<ValidationResult> getValidationResults() {
        return validationResults;
    }

    public void setValidationResults(List<ValidationResult> validationResults) {
        this.validationResults = validationResults;
    }

    // Config Files
    public Map<String, String> getConfigFiles() {
        if (configFiles == null) {
            configFiles = new HashMap<>();
        }
        return configFiles;
    }

    public void addConfigFile(String fileName, String content) {
        if (configFiles == null) {
            configFiles = new HashMap<>();
        }
        configFiles.put(fileName, content);
    }

    // Current File Type (for ValidateOnlyInFilesWith)
    public String getCurrentFileType() {
        return currentFileType;
    }

    public void setCurrentFileType(String currentFileType) {
        this.currentFileType = currentFileType;
    }

    // COM File
    public boolean isComFile() {
        return isComFile;
    }

    public void setComFile(boolean comFile) {
        this.isComFile = comFile;
    }

    // Duplicate Registry
    public Map<String, String> getDuplicateRegistry() {
        if (duplicateRegistry == null) {
            duplicateRegistry = new HashMap<>();
        }
        return duplicateRegistry;
    }

    public void addDuplicateValue(String key, String value) {
        if (duplicateRegistry == null) {
            duplicateRegistry = new HashMap<>();
        }
        duplicateRegistry.put(key, value);
    }

    public boolean isDuplicate(String key, String value) {
        if (duplicateRegistry == null) {
            return false;
        }
        String existingValue = duplicateRegistry.get(key);
        return existingValue != null && existingValue.equals(value);
    }

    // SkipComFile Default
    public boolean isSkipComFileDefault() {
        return skipComFileDefault;
    }

    public void setSkipComFileDefault(boolean skipComFileDefault) {
        this.skipComFileDefault = skipComFileDefault;
    }
}
