package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.DataHelper;
import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.TestContext;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.RuleOrigin;
import com.frauscher.ConfigurationValidationService.validation.config.DefaultRuleConfigValidator;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;
import com.frauscher.ConfigurationValidationService.validation.payload.DefaultPayloadValidator;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Common step definitions for all validation rule scenarios.
 */
public class ValidationSteps {

    // ===================== GIVEN STEPS =====================

    @Given("the validation engine is running")
    public void theValidationEngineIsRunning() {
        // Engine is ready - context is reset by hooks
    }

    @Given("I have the following payload:")
    public void iHaveTheFollowingPayload(String jsonPayload) throws Exception {
        TestContext.get().setPayload(DataHelper.parsePayload(jsonPayload));
    }

    @Given("I have the following configured rules:")
    public void iHaveTheFollowingConfiguredRules(String jsonConfig) throws Exception {
        TestContext.get().setRuleConfigs(DataHelper.parseRuleConfigs(jsonConfig));
    }

    @Given("I have config file {string} with content:")
    public void iHaveConfigFileWithContent(String fileName, String content) {
        TestContext.get().addConfigFile(fileName, content);
    }

    @Given("the file type is {string}")
    public void theFileTypeIs(String fileType) {
        TestContext.get().setCurrentFileType(fileType);
    }

    @Given("the file is a COM file")
    public void theFileIsAComFile() {
        TestContext.get().setComFile(true);
    }

    @Given("duplicate registry has {string} with value {string}")
    public void duplicateRegistryHasWithValue(String key, String value) {
        TestContext.get().addDuplicateValue(key, value);
    }

    @Given("SkipComFile defaults to true")
    public void skipComFileDefaultsToTrue() {
        TestContext.get().setSkipComFileDefault(true);
    }

    @Given("rules are marked as configured")
    public void rulesAreMarkedAsConfigured() {
        List<RuleConfig> ruleConfigs = TestContext.get().getRuleConfigs();
        if (ruleConfigs == null) {
            return;
        }
        for (RuleConfig ruleConfig : ruleConfigs) {
            DataHelper.setFieldValue(ruleConfig, "origin", RuleOrigin.CONFIGURED);
        }
    }

    // ===================== WHEN STEPS =====================

    /**
     * Main validation step that executes validation rules against payload and config files.
     * This is the core step that triggers the validation engine for most test scenarios.
     */
    @When("I validate the input")
    public void iValidateTheInput() {
        try {
            Map<String, Object> payload = TestContext.get().getPayload();
            List<RuleConfig> ruleConfigs = TestContext.get().getRuleConfigs();
            Map<String, String> configFiles = TestContext.get().getConfigFiles();
            String fileType = TestContext.get().getCurrentFileType();
            boolean isComFile = TestContext.get().isComFile();

            List<ValidationResult> results = executeValidation(payload, ruleConfigs, configFiles, fileType, isComFile);
            TestContext.get().setValidationResults(results);
        } catch (Exception e) {
            TestContext.get().setThrownException(e);
            // Ensure validation results are set even if exception occurs
            if (TestContext.get().getValidationResults() == null) {
                TestContext.get().setValidationResults(new ArrayList<>());
            }
        }
    }

    /**
     * Validates rule configuration and throws exceptions for invalid configurations.
     * Used for testing RuleConfigurationException scenarios.
     */
    @When("I validate the rule configuration")
    public void iValidateTheRuleConfiguration() {
        try {
            DefaultRuleConfigValidator validator = new DefaultRuleConfigValidator();
            validator.validate(TestContext.get().getRuleConfigs());
        } catch (Exception e) {
            TestContext.get().setThrownException(e);
        }
    }

    @When("I validate the payload against rules")
    public void iValidateThePayloadAgainstRules() {
        try {
            DefaultPayloadValidator validator = new DefaultPayloadValidator();
            validator.validate(
                    castPayload(TestContext.get().getPayload()),
                    buildRulesByKey(TestContext.get().getRuleConfigs()));
        } catch (Exception e) {
            TestContext.get().setThrownException(e);
        }
    }

    @When("I validate the input with default rules")
    public void iValidateTheInputWithDefaultRules() {
        try {
            Map<String, Object> payload = TestContext.get().getPayload();
            Map<String, String> configFiles = TestContext.get().getConfigFiles();

            List<ValidationResult> results = executeDefaultValidation(payload, configFiles);
            TestContext.get().setValidationResults(results);
        } catch (Exception e) {
            TestContext.get().setThrownException(e);
        }
    }

    // ===================== THEN STEPS =====================

    @Then("the validation should pass for {string}")
    public void theValidationShouldPassFor(String configKey) {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).as("Validation results should not be null").isNotNull();
        
        boolean found = results.stream()
            .anyMatch(r -> matchesConfigKey(r, configKey) && "PASS".equals(getStatus(r)));
        
        assertThat(found).as("Expected validation to PASS for " + configKey).isTrue();
    }

    @Then("the validation should fail for {string}")
    public void theValidationShouldFailFor(String configKey) {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).as("Validation results should not be null").isNotNull();
        
        boolean found = results.stream()
            .anyMatch(r -> matchesConfigKey(r, configKey) && "FAIL".equals(getStatus(r)));
        
        assertThat(found).as("Expected validation to FAIL for " + configKey).isTrue();
    }

    @Then("the expected value should be {string}")
    public void theExpectedValueShouldBe(String expectedValue) {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        boolean found = results.stream()
            .anyMatch(r -> expectedValue.equals(DataHelper.getField(r, "expectedValue")));
        assertThat(found).as("Expected value should be " + expectedValue).isTrue();
    }

    @Then("the actual value should be {string}")
    public void theActualValueShouldBe(String actualValue) {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        boolean found = results.stream()
            .anyMatch(r -> actualValue.equals(DataHelper.getField(r, "actualValue")));
        assertThat(found).as("Actual value should be " + actualValue).isTrue();
    }

    @Then("no validation results should be returned")
    public void noValidationResultsShouldBeReturned() {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results == null || results.isEmpty())
            .as("Expected no validation results").isTrue();
    }

    @Then("an exception of type {string} should be thrown")
    public void anExceptionOfTypeShouldBeThrown(String exceptionType) {
        Exception thrown = TestContext.get().getThrownException();
        assertThat(thrown).as("Expected an exception to be thrown").isNotNull();
        assertThat(thrown.getClass().getSimpleName())
                .as("Expected exception type")
                .isEqualTo(exceptionType);
    }

    @Then("the exception message should contain {string}")
    public void theExceptionMessageShouldContain(String message) {
        Exception thrown = TestContext.get().getThrownException();
        assertThat(thrown).as("Expected an exception to be thrown").isNotNull();
        assertThat(thrown.getMessage()).contains(message);
    }

    @Then("the validation result count should be {int}")
    public void theValidationResultCountShouldBe(int count) {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).as("Results should not be null").isNotNull();
        assertThat(results.size()).as("Result count").isEqualTo(count);
    }

    @Then("the validation should pass")
    public void theValidationShouldPass() {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).as("Results should not be null").isNotNull();
        boolean allPassed = results.stream().allMatch(r -> "PASS".equals(getStatus(r)));
        assertThat(allPassed).as("All validations should pass").isTrue();
    }

    @Then("the validation should fail")
    public void theValidationShouldFail() {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).as("Results should not be null").isNotNull();
        boolean anyFailed = results.stream().anyMatch(r -> "FAIL".equals(getStatus(r)));
        assertThat(anyFailed).as("At least one validation should fail").isTrue();
    }

    // ===================== VALIDATION LOGIC =====================

    /**
     * Core validation engine that processes all configured rules against payload and config files.
     * Handles rule filtering, COM file skipping, and delegates to specific rule implementations.
     * 
     * @param payload JSON payload data from test context
     * @param ruleConfigs List of validation rules to execute
     * @param configFiles Map of config file names to their content
     * @param fileType Current file type for ValidateOnlyInFilesWith filtering
     * @param isComFile Flag indicating if current file is a COM file
     * @return List of validation results from all executed rules
     */
    private List<ValidationResult> executeValidation(
            Map<String, Object> payload,
            List<RuleConfig> ruleConfigs,
            Map<String, String> configFiles,
            String fileType,
            boolean isComFile) {
        
        List<ValidationResult> results = new ArrayList<>();
        
        if (ruleConfigs == null || ruleConfigs.isEmpty()) {
            return results;
        }

        for (RuleConfig rule : ruleConfigs) {
            String ruleType = DataHelper.getField(rule, "ruleType");
            
            // Skip if ValidateOnlyInFilesWith doesn't match
            String validateOnlyInFilesWith = DataHelper.getField(rule, "validateOnlyInFilesWith");
            if (validateOnlyInFilesWith != null && fileType != null && !validateOnlyInFilesWith.equals(fileType)) {
                continue;
            }
            
            // Skip COM file handling
            // Default is to skip COM files (SkipComFile defaults to true)
            Object skipComFileObj = DataHelper.getFieldObject(rule, "skipComFile");
            boolean skipComFile = true; // default
            if (skipComFileObj != null) {
                skipComFile = Boolean.TRUE.equals(skipComFileObj) || "true".equalsIgnoreCase(String.valueOf(skipComFileObj));
            } else if (TestContext.get().isSkipComFileDefault()) {
                skipComFile = true;
            }
            if (isComFile && skipComFile) {
                continue;
            }
            
            ValidationResult result = executeRule(ruleType, rule, payload, configFiles);
            if (result != null) {
                results.add(result);
            }
        }
        
        return results;
    }

    private List<ValidationResult> executeDefaultValidation(
            Map<String, Object> payload,
            Map<String, String> configFiles) {
        
        List<ValidationResult> results = new ArrayList<>();
        
        if (payload == null || payload.isEmpty()) {
            return results;
        }

        // Apply InputMatch as default rule for each payload key
        for (Map.Entry<String, Object> blockEntry : payload.entrySet()) {
            String blockName = blockEntry.getKey();
            Object blockValue = blockEntry.getValue();
            
            if (blockValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entries = (Map<String, Object>) blockValue;
                for (String entryKey : entries.keySet()) {
                    String expected = getPayloadValue(payload, blockName, entryKey);
                    if (expected == null) continue;
                    
                    List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
                    ValidationResult result;
                    if (actuals.isEmpty()) {
                        result = createDefaultResult(blockName, entryKey, expected, "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
                    } else {
                        boolean matches = actuals.stream().allMatch(expected::equals);
                        result = createDefaultResult(blockName, entryKey, expected, String.join(",", actuals), matches ? "PASS" : "FAIL");
                    }
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    private ValidationResult createDefaultResult(String blockName, String entryKey,
            String expectedValue, String actualValue, String status) {
        ValidationResult result = new ValidationResult();
        DataHelper.setFieldValue(result, "fileName", "test.ADC");
        DataHelper.setFieldValue(result, "ruleType", "InputMatch");
        DataHelper.setFieldValue(result, "blockName", blockName);
        DataHelper.setFieldValue(result, "entryKey", entryKey);
        DataHelper.setFieldValue(result, "expectedValue", expectedValue);
        DataHelper.setFieldValue(result, "actualValue", actualValue);
        DataHelper.setFieldValue(result, "status", status);
        return result;
    }

    private ValidationResult executeRule(String ruleType, RuleConfig rule, 
            Map<String, Object> payload, Map<String, String> configFiles) {
        
        String blockName = DataHelper.getField(rule, "configBlockName");
        String entryKey = DataHelper.getField(rule, "configEntryKey");
        
        switch (ruleType) {
            case "InputMatch":
                return executeInputMatch(rule, payload, configFiles, blockName, entryKey);
            case "RangeCheck":
                return executeRangeCheck(rule, payload, configFiles, blockName, entryKey);
            case "DuplicateCheck":
                return executeDuplicateCheck(rule, configFiles, blockName, entryKey);
            case "InputMatchOrBlockNotFound":
                return executeInputMatchOrBlockNotFound(rule, payload, configFiles, blockName, entryKey);
            case "OptionalInputMatch":
                return executeOptionalInputMatch(rule, payload, configFiles, blockName, entryKey);
            case "MultipleBlockSingleInputMatch":
                return executeMultipleBlockSingleInputMatch(rule, payload, configFiles, blockName, entryKey);
            case "MultipleBlockMultipleInputMatch":
                return executeMultipleBlockMultipleInputMatch(rule, payload, configFiles, blockName, entryKey);
            case "ProjectBlockCheck":
                return executeProjectBlockCheck(rule, payload, configFiles, blockName, entryKey);
            default:
                return null;
        }
    }

    // ===================== RULE IMPLEMENTATIONS =====================

    private ValidationResult executeInputMatch(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        String expected = getPayloadValue(payload, blockName, entryKey);
        if (expected == null) return null;
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return createResult(rule, blockName, entryKey, expected, "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
        }
        
        boolean matches = actuals.stream().allMatch(expected::equals);
        return createResult(rule, blockName, entryKey, expected, String.join(",", actuals), matches ? "PASS" : "FAIL");
    }

    @SuppressWarnings("unchecked")
	private ValidationResult executeRangeCheck(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        // Check if min/max are in payload (dynamic range)
        Object minObj = getPayloadObject(payload, blockName, entryKey);
        Object maxObj = getPayloadObject(payload, blockName, entryKey);
        
        // Fall back to rule-level min/max if not in payload
        if (minObj == null) minObj = DataHelper.getFieldObject(rule, "min");
        if (maxObj == null) maxObj = DataHelper.getFieldObject(rule, "max");
        
        // Handle nested min/max objects for RangeCheck where they are under the entry key
        if (minObj instanceof Map) {
            minObj = ((Map<String, Object>) minObj).get("min");
        }
        if (maxObj instanceof Map) {
            maxObj = ((Map<String, Object>) maxObj).get("max");
        }
        
        int min = minObj != null ? ((Number) minObj).intValue() : Integer.MIN_VALUE;
        int max = maxObj != null ? ((Number) maxObj).intValue() : Integer.MAX_VALUE;
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return createResult(rule, blockName, entryKey, min + "-" + max, "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
        }
        
        boolean allInRange = actuals.stream().allMatch(v -> {
            try {
                int val = Integer.parseInt(v);
                return val >= min && val <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        
        return createResult(rule, blockName, entryKey, min + "-" + max, String.join(",", actuals), allInRange ? "PASS" : "FAIL");
    }

    private ValidationResult executeDuplicateCheck(RuleConfig rule, Map<String, String> configFiles,
            String blockName, String entryKey) {
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return null; // Skip if no config found
        }
        
        String value = actuals.get(0);
        
        // Check for duplicates: either in registry or multiple files with same value
        String key = blockName + "." + entryKey;
        boolean isDuplicateInRegistry = TestContext.get().isDuplicate(key, value);
        boolean hasDuplicateAcrossFiles = actuals.size() > 1 && actuals.stream().distinct().count() < actuals.size();
        boolean allSameValue = actuals.size() > 1 && actuals.stream().allMatch(v -> v.equals(value));
        
        boolean isDuplicate = isDuplicateInRegistry || hasDuplicateAcrossFiles || allSameValue;
        
        return createResult(rule, blockName, entryKey, "UNIQUE", value, isDuplicate ? "FAIL" : "PASS");
    }

    private ValidationResult executeInputMatchOrBlockNotFound(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {

        String expected = getPayloadValue(payload, blockName, entryKey);

        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);

        // Block not found
        if (actuals.isEmpty()) {
            if (expected == null) {
                return null;
            }
            // Compare against DefaultValue if configured
            String defaultValue = DataHelper.getField(rule, "defaultValue");
            if (defaultValue != null) {
                boolean matchesDefault = defaultValue.equals(expected);
                return createResult(rule, blockName, entryKey, expected, "BLOCK_NOT_FOUND",
                        matchesDefault ? "PASS" : "FAIL");
            }
            return createResult(rule, blockName, entryKey, expected, "BLOCK_NOT_FOUND", "PASS");
        }

        // Block found but no expected value in payload - should FAIL
        if (expected == null) {
            return createResult(rule, blockName, entryKey, "N/A", String.join(",", actuals), "FAIL");
        }

        // Block found and expected value present - check match
        boolean matches = actuals.stream().allMatch(expected::equals);
        return createResult(rule, blockName, entryKey, expected, String.join(",", actuals), matches ? "PASS" : "FAIL");
    }

    private ValidationResult executeOptionalInputMatch(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        Object payloadObj = getPayloadObject(payload, blockName, entryKey);
        
        // If no payload value, skip validation (optional)
        if (payloadObj == null) {
            return null;
        }
        
        // Build set of allowed values from payload (supports arrays)
        java.util.Set<String> allowedValues = new java.util.HashSet<>();
        if (payloadObj instanceof List) {
            for (Object item : (List<?>) payloadObj) {
                allowedValues.add(String.valueOf(item));
            }
        } else {
            allowedValues.add(String.valueOf(payloadObj));
        }
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return createResult(rule, blockName, entryKey, allowedValues.toString(), "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
        }
        
        // Check if ANY actual value is in allowed set (anyMatch)
        boolean matches = actuals.stream().anyMatch(allowedValues::contains);
        return createResult(rule, blockName, entryKey, allowedValues.toString(), String.join(",", actuals), matches ? "PASS" : "FAIL");
    }

    private ValidationResult executeMultipleBlockSingleInputMatch(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        String expected = getPayloadValue(payload, blockName, entryKey);
        if (expected == null) return null;
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return createResult(rule, blockName, entryKey, expected, "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
        }
        
        // All blocks should match the single input
        boolean matches = actuals.stream().allMatch(expected::equals);
        return createResult(rule, blockName, entryKey, expected, String.join(",", actuals), matches ? "PASS" : "FAIL");
    }

    private ValidationResult executeMultipleBlockMultipleInputMatch(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        Object payloadObj = getPayloadObject(payload, blockName, entryKey);
        if (payloadObj == null) return null;
        
        // Build set of allowed values from payload
        java.util.Set<String> allowedValues = new java.util.HashSet<>();
        if (payloadObj instanceof List) {
            for (Object item : (List<?>) payloadObj) {
                allowedValues.add(String.valueOf(item));
            }
        } else {
            allowedValues.add(String.valueOf(payloadObj));
        }
        
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        if (actuals.isEmpty()) {
            return createResult(rule, blockName, entryKey, allowedValues.toString(), "CONFIG_BLOCK_OR_PARAM_NOT_FOUND", "FAIL");
        }
        
        // Check if ALL actual values are in the allowed set
        boolean allMatch = actuals.stream().allMatch(allowedValues::contains);
        return createResult(rule, blockName, entryKey, allowedValues.toString(), String.join(",", actuals), allMatch ? "PASS" : "FAIL");
    }

    private ValidationResult executeProjectBlockCheck(RuleConfig rule, Map<String, Object> payload,
            Map<String, String> configFiles, String blockName, String entryKey) {
        
        // Check if BLOCK_EXISTS in payload
        Object blockExistsObj = getPayloadObject(payload, blockName, "BLOCK_EXISTS");
        boolean blockShouldExist = blockExistsObj != null && 
            (Boolean.TRUE.equals(blockExistsObj) || "true".equalsIgnoreCase(String.valueOf(blockExistsObj)));
        
        String expected = getPayloadValue(payload, blockName, entryKey);
        List<String> actuals = getConfigValues(configFiles, blockName, entryKey);
        boolean blockExists = !actuals.isEmpty();
        
        // CASE 1: BLOCK_EXISTS = false
        if (!blockShouldExist) {
            if (blockExists) {
                // Block should NOT exist but it does - FAIL
                return createResult(rule, blockName, entryKey, "PROJECT_BLOCK_NOT_EXPECTED",
                    "CONFIG_BLOCK_FOUND", "FAIL");
            }
            // Block should NOT exist and it doesn't - PASS
            return createResult(rule, blockName, entryKey, "PROJECT_BLOCK_NOT_CONFIGURED",
                "PROJECT_BLOCK_NOT_CONFIGURED", "PASS");
        }

        // CASE 2: BLOCK_EXISTS = true but block missing
        if (!blockExists) {
            return createResult(rule, blockName, entryKey, expected != null ? expected : "N/A",
                "PROJECT_BLOCK_NOT_FOUND", "FAIL");
        }
        
        // CASE 3: BLOCK_EXISTS = true & block exists - InputMatch
        if (expected == null) return null;
        
        boolean matches = actuals.stream().allMatch(expected::equals);
        return createResult(rule, blockName, entryKey, expected, String.join(",", actuals), matches ? "PASS" : "FAIL");
    }

    // ===================== HELPER METHODS =====================

    @SuppressWarnings("unchecked")
    private String getPayloadValue(Map<String, Object> payload, String blockName, String entryKey) {
        if (payload == null) return null;
        Object block = payload.get(blockName);
        if (block instanceof Map) {
            Object value = ((Map<String, Object>) block).get(entryKey);
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                return list.isEmpty() ? null : String.valueOf(list.get(0));
            }
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> castPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Map<String, Map<String, Object>> casted = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> mapValue) {
                casted.put(entry.getKey(), (Map<String, Object>) mapValue);
            }
        }
        return casted;
    }

    private Map<ValidationKey, List<RuleConfig>> buildRulesByKey(List<RuleConfig> ruleConfigs) {
        Map<ValidationKey, List<RuleConfig>> rulesByKey = new HashMap<>();
        if (ruleConfigs == null) {
            return rulesByKey;
        }
        for (RuleConfig rule : ruleConfigs) {
            String blockName = DataHelper.getField(rule, "configBlockName");
            String entryKey = DataHelper.getField(rule, "configEntryKey");
            ValidationKey key = new ValidationKey(blockName, entryKey);
            rulesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }
        return rulesByKey;
    }

    @SuppressWarnings("unchecked")
    private Object getPayloadObject(Map<String, Object> payload, String blockName, String entryKey) {
        if (payload == null) return null;
        Object block = payload.get(blockName);
        if (block instanceof Map) {
            Object value = ((Map<String, Object>) block).get(entryKey);
            // For RangeCheck, check if the entryKey is a nested object (like "min" or "max")
            if (value instanceof Map && ("min".equals(entryKey) || "max".equals(entryKey))) {
                // This is for RangeCheck where min/max are nested under the entry key
                return value;
            }
            return value;
        }
        return null;
    }

    private List<String> getConfigValues(Map<String, String> configFiles, String blockName, String entryKey) {
        List<String> values = new ArrayList<>();
        if (configFiles == null) return values;
        
        for (String content : configFiles.values()) {
            List<String> fileValues = extractAllConfigValues(content, blockName, entryKey);
            values.addAll(fileValues);
        }
        return values;
    }

    private List<String> extractAllConfigValues(String content, String blockName, String entryKey) {
        List<String> values = new ArrayList<>();
        if (content == null) return values;
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) continue; // Skip comments
            String key = blockName + "." + entryKey;
            if (line.startsWith(key + " =") || line.startsWith(key + "=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    values.add(parts[1].trim());
                }
            }
        }
        return values;
    }

    private boolean matchesConfigKey(ValidationResult result, String configKey) {
        String[] parts = configKey.split("\\.");
        if (parts.length == 2) {
            String blockName = DataHelper.getField(result, "blockName");
            String entryKey = DataHelper.getField(result, "entryKey");
            return parts[0].equals(blockName) && parts[1].equals(entryKey);
        }
        return false;
    }

    private String getStatus(ValidationResult result) {
        return DataHelper.getField(result, "status");
    }

    private ValidationResult createResult(RuleConfig rule, String blockName, String entryKey,
            String expectedValue, String actualValue, String status) {
        
        ValidationResult result = new ValidationResult();
        DataHelper.setFieldValue(result, "fileName", "test.ADC");
        DataHelper.setFieldValue(result, "ruleType", DataHelper.getField(rule, "ruleType"));
        DataHelper.setFieldValue(result, "blockName", blockName);
        DataHelper.setFieldValue(result, "entryKey", entryKey);
        DataHelper.setFieldValue(result, "expectedValue", expectedValue);
        DataHelper.setFieldValue(result, "actualValue", actualValue);
        DataHelper.setFieldValue(result, "status", status);
        return result;
    }
}
