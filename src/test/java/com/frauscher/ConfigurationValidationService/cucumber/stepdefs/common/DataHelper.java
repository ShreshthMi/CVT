package com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.RuleOrigin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class for parsing test data in step definitions.
 */
public class DataHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> parsePayload(String jsonPayload) throws Exception {
        return objectMapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});
    }

    public static List<RuleConfig> parseRuleConfigs(String jsonConfig) throws Exception {
        List<Map<String, Object>> configList = objectMapper.readValue(jsonConfig, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        List<RuleConfig> ruleConfigs = new ArrayList<>();
        for (Map<String, Object> config : configList) {
            RuleConfig ruleConfig = createRuleConfig(config);
            ruleConfigs.add(ruleConfig);
        }
        return ruleConfigs;
    }

    private static RuleConfig createRuleConfig(Map<String, Object> config) {
        RuleConfig ruleConfig = new RuleConfig();
        
        setField(ruleConfig, "ruleType", (String) config.get("RuleType"));
        setField(ruleConfig, "configBlockName", (String) config.get("ConfigBlockName"));
        setField(ruleConfig, "configEntryKey", (String) config.get("ConfigEntryKey"));
        setField(ruleConfig, "uiInputRequired", (String) config.get("UIInputRequired"));
        setField(ruleConfig, "validateOnlyInFilesWith", (String) config.get("ValidateOnlyInFilesWith"));
        
        if (config.get("SkipComFile") != null) {
            setField(ruleConfig, "skipComFile", config.get("SkipComFile"));
        }
        if (config.get("min") != null) {
            setField(ruleConfig, "min", config.get("min"));
        }
        if (config.get("max") != null) {
            setField(ruleConfig, "max", config.get("max"));
        }
        
        setField(ruleConfig, "origin", RuleOrigin.CONFIGURED);
        
        return ruleConfig;
    }

    private static void setField(Object obj, String fieldName, Object value) {
        if (value == null) return;
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            // Ignore field setting errors
        }
    }

    public static String getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getFieldObject(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setFieldValue(Object obj, String fieldName, Object value) {
        setField(obj, fieldName, value);
    }

    /**
     * Parse JSON content into a ParsedConfigFile object for BDD testing
     */
    public static ParsedConfigFile parseParsedConfigFile(String jsonContent) throws Exception {
        return objectMapper.readValue(jsonContent, ParsedConfigFile.class);
    }

    /**
     * Create a ConfigBlock for test scenarios
     */
    public static ConfigBlock createConfigBlock(String name, int blockIndex, List<ConfigEntry> entries) {
        ConfigBlock block = new ConfigBlock();
        block.setName(name);
        block.setBlockIndex(blockIndex);
        block.setEntries(entries);
        return block;
    }

    /**
     * Create a ConfigEntry for test scenarios
     */
    public static ConfigEntry createConfigEntry(String key, String value, String comment) {
        ConfigEntry entry = new ConfigEntry();
        entry.setKey(key);
        entry.setValue(value);
        entry.setComment(comment);
        return entry;
    }

    /**
     * Create a simple ParsedConfigFile for test scenarios
     */
    public static ParsedConfigFile createParsedConfigFile(String fileName, int id) {
        ParsedConfigFile file = new ParsedConfigFile();
        file.setFileName(fileName);
        file.setId(id);
        file.setBlocks(new ArrayList<>());
        return file;
    }

    /**
     * Create a ParsedConfigFile with file type markers
     */
    public static ParsedConfigFile createParsedConfigFile(String fileName, int id,
                                                          boolean trackSectionDetails,
                                                          boolean ioexbDetails,
                                                          boolean comDetails) {
        ParsedConfigFile file = createParsedConfigFile(fileName, id);
        file.setTrackSectionDetails(trackSectionDetails);
        file.setIoexbDetails(ioexbDetails);
        file.setComDetails(comDetails);
        return file;
    }
}
