package com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common;

import com.frauscher.ConfigurationValidationService.dto.RuleExecutionResult;
import com.frauscher.ConfigurationValidationService.model.*;
import com.frauscher.ConfigurationValidationService.validation.context.FileContext;
import com.frauscher.ConfigurationValidationService.validation.context.ResolvedPayloadContext;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationContext;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecision;

import java.util.ArrayList;
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

    // Engine testing fields
    private ParsedConfigFile parsedConfigFile;
    private List<ParsedConfigFile> parsedConfigFiles;
    private FileContext fileContext;
    private ValidationKey validationKey;
    private ResolvedPayloadContext resolvedPayloadContext;
    private RuleExecutionResult ruleExecutionResult;
    private ValidationDecision validationDecision;
    private ValidationContext validationContext;

    // Extractor testing fields
    private List<TrackSectionDetail> trackSectionDetails;
    private List<SupervisorDetail> supervisorDetails;
    private List<IOEXBAcoDetail> ioexbAcoDetails;
    private List<IOEXBBehaviourDetail> ioexbBehaviourDetails;
    private List<CHCDetail> chcDetails;
    private List<DpDetail> dpDetails;
    private List<DataTransmissionDetail> dataTransmissionDetails;
    private List<EthernetDetail> ethernetDetails;

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

        // Reset engine testing fields
        this.parsedConfigFile = null;
        this.parsedConfigFiles = new ArrayList<>();
        this.fileContext = null;
        this.validationKey = null;
        this.resolvedPayloadContext = null;
        this.ruleExecutionResult = null;
        this.validationDecision = null;
        this.validationContext = null;

        // Reset extractor testing fields
        this.trackSectionDetails = null;
        this.supervisorDetails = null;
        this.ioexbAcoDetails = null;
        this.ioexbBehaviourDetails = null;
        this.chcDetails = null;
        this.dpDetails = null;
        this.dataTransmissionDetails = null;
        this.ethernetDetails = null;
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

    // ==================== Engine Testing Fields ====================

    public ParsedConfigFile getParsedConfigFile() {
        return parsedConfigFile;
    }

    public void setParsedConfigFile(ParsedConfigFile parsedConfigFile) {
        this.parsedConfigFile = parsedConfigFile;
    }

    public List<ParsedConfigFile> getParsedConfigFiles() {
        if (parsedConfigFiles == null) {
            parsedConfigFiles = new ArrayList<>();
        }
        return parsedConfigFiles;
    }

    public void setParsedConfigFiles(List<ParsedConfigFile> parsedConfigFiles) {
        this.parsedConfigFiles = parsedConfigFiles;
    }

    public void addParsedConfigFile(ParsedConfigFile file) {
        if (parsedConfigFiles == null) {
            parsedConfigFiles = new ArrayList<>();
        }
        parsedConfigFiles.add(file);
    }

    public FileContext getFileContext() {
        return fileContext;
    }

    public void setFileContext(FileContext fileContext) {
        this.fileContext = fileContext;
    }

    public ValidationKey getValidationKey() {
        return validationKey;
    }

    public void setValidationKey(ValidationKey validationKey) {
        this.validationKey = validationKey;
    }

    public ResolvedPayloadContext getResolvedPayloadContext() {
        return resolvedPayloadContext;
    }

    public void setResolvedPayloadContext(ResolvedPayloadContext resolvedPayloadContext) {
        this.resolvedPayloadContext = resolvedPayloadContext;
    }

    public RuleExecutionResult getRuleExecutionResult() {
        return ruleExecutionResult;
    }

    public void setRuleExecutionResult(RuleExecutionResult ruleExecutionResult) {
        this.ruleExecutionResult = ruleExecutionResult;
    }

    public ValidationDecision getValidationDecision() {
        return validationDecision;
    }

    public void setValidationDecision(ValidationDecision validationDecision) {
        this.validationDecision = validationDecision;
    }

    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public void setValidationContext(ValidationContext validationContext) {
        this.validationContext = validationContext;
    }

    // ==================== Extractor Testing Fields ====================

    public List<TrackSectionDetail> getTrackSectionDetails() {
        return trackSectionDetails;
    }

    public void setTrackSectionDetails(List<TrackSectionDetail> trackSectionDetails) {
        this.trackSectionDetails = trackSectionDetails;
    }

    public List<SupervisorDetail> getSupervisorDetails() {
        return supervisorDetails;
    }

    public void setSupervisorDetails(List<SupervisorDetail> supervisorDetails) {
        this.supervisorDetails = supervisorDetails;
    }

    public List<IOEXBAcoDetail> getIoexbAcoDetails() {
        return ioexbAcoDetails;
    }

    public void setIoexbAcoDetails(List<IOEXBAcoDetail> ioexbAcoDetails) {
        this.ioexbAcoDetails = ioexbAcoDetails;
    }

    public List<IOEXBBehaviourDetail> getIoexbBehaviourDetails() {
        return ioexbBehaviourDetails;
    }

    public void setIoexbBehaviourDetails(List<IOEXBBehaviourDetail> ioexbBehaviourDetails) {
        this.ioexbBehaviourDetails = ioexbBehaviourDetails;
    }

    public List<CHCDetail> getChcDetails() {
        return chcDetails;
    }

    public void setChcDetails(List<CHCDetail> chcDetails) {
        this.chcDetails = chcDetails;
    }

    public List<DpDetail> getDpDetails() {
        return dpDetails;
    }

    public void setDpDetails(List<DpDetail> dpDetails) {
        this.dpDetails = dpDetails;
    }

    public List<DataTransmissionDetail> getDataTransmissionDetails() {
        return dataTransmissionDetails;
    }

    public void setDataTransmissionDetails(List<DataTransmissionDetail> dataTransmissionDetails) {
        this.dataTransmissionDetails = dataTransmissionDetails;
    }

    public List<EthernetDetail> getEthernetDetails() {
        return ethernetDetails;
    }

    public void setEthernetDetails(List<EthernetDetail> ethernetDetails) {
        this.ethernetDetails = ethernetDetails;
    }
}
