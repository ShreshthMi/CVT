package com.frauscher.configvalidator.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.frauscher.configvalidator.dto.RuleExecutionResult;
import com.frauscher.configvalidator.exception.ValidationEngineException;
import com.frauscher.configvalidator.model.ParsedConfigFile;
import com.frauscher.configvalidator.model.RuleConfig;
import com.frauscher.configvalidator.model.ValidationResult;
import com.frauscher.configvalidator.startup.ValidationConfigurationLoader;
import com.frauscher.configvalidator.validation.context.DuplicateValueRegistry;
import com.frauscher.configvalidator.validation.context.FileContext;
import com.frauscher.configvalidator.validation.context.ResolvedPayloadContext;
import com.frauscher.configvalidator.validation.context.ValidationKey;
import com.frauscher.configvalidator.validation.engine.DefaultRuleExecutor;
import com.frauscher.configvalidator.validation.engine.RuleExecutionEngine;
import com.frauscher.configvalidator.validation.payload.PayloadValidator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConfigValidationService {

    private static final String BLOCK_EXISTS = "BLOCK_EXISTS";

    private final RuleExecutionEngine ruleEngine;
    private final DefaultRuleExecutor defaultRuleExecutor;
    private final PayloadValidator payloadValidator;
    private final DuplicateValueRegistry duplicateRegistry;
    private final Map<ValidationKey, List<RuleConfig>> rulesByKey;

    public ConfigValidationService(
            ValidationConfigurationLoader ruleLoader,
            RuleExecutionEngine ruleEngine,
            DefaultRuleExecutor defaultRuleExecutor,
            PayloadValidator payloadValidator,
            DuplicateValueRegistry duplicateRegistry) {

        this.ruleEngine = ruleEngine;
        this.defaultRuleExecutor = defaultRuleExecutor;
        this.payloadValidator = payloadValidator;
        this.duplicateRegistry = duplicateRegistry;
        
        this.rulesByKey = ruleLoader.loadConfiguredRules()
                .stream()
                .collect(Collectors.groupingBy(
                        r -> new ValidationKey(
                                r.getConfigBlockName(),
                                r.getConfigEntryKey()
                        )));
    }
    
    public List<ValidationResult> validateParsedFiles(
    		List<ParsedConfigFile> parsedFiles,
            Map<String, Map<String, Object>> userInput) {

        ResolvedPayloadContext resolvedPayloadContext =
                payloadValidator.validate(userInput, rulesByKey);

        duplicateRegistry.clear();

        List<ValidationResult> results = new ArrayList<>();

        for (ParsedConfigFile file : parsedFiles) {

            FileContext ctx = new FileContext(file);

            Set<ValidationKey> universe = buildValidationUniverse(
                    rulesByKey.keySet(),
                    userInput);

            for (ValidationKey key : universe) {

                try {
                    List<RuleConfig> ruleConfigs =
                            rulesByKey.getOrDefault(key, List.of());

                    RuleExecutionResult execResult =
                            ruleEngine.execute(
                                    file,
                                    ctx,
                                    key,
                                    ruleConfigs,
                                    resolvedPayloadContext);

                    if (execResult.isRuleExecuted()) {
                        results.addAll(execResult.getResults());
                    } else {

                        boolean hasMarkerRule =
                                ruleConfigs.stream()
                                        .anyMatch(r ->
                                                r.getValidateOnlyInFilesWith() != null);

                        if (!hasMarkerRule) {
                            results.addAll(
                                    defaultRuleExecutor.executeDefault(
                                            file,
                                            ctx,
                                            key,
                                            resolvedPayloadContext));
                        }
                    }

                } catch (Exception e) {
                    throw new ValidationEngineException(
                            "Unexpected validation engine failure", e);
                }
            }
        }

        // Sort validation results by rule type, config block, and entry key (case-insensitive)
        List<ValidationResult> sortedResults = results.stream()
                .sorted((r1, r2) -> {
                    String ruleType1 = r1.getRuleType() != null ? r1.getRuleType() : "";
                    String ruleType2 = r2.getRuleType() != null ? r2.getRuleType() : "";
                    int ruleTypeCompare = ruleType1.compareToIgnoreCase(ruleType2);
                    if (ruleTypeCompare != 0) {
                        return ruleTypeCompare;
                    }
                    
                    String blockName1 = r1.getBlockName() != null ? r1.getBlockName() : "";
                    String blockName2 = r2.getBlockName() != null ? r2.getBlockName() : "";
                    int blockNameCompare = blockName1.compareToIgnoreCase(blockName2);
                    if (blockNameCompare != 0) {
                        return blockNameCompare;
                    }
                    
                    String entryKey1 = r1.getEntryKey() != null ? r1.getEntryKey() : "";
                    String entryKey2 = r2.getEntryKey() != null ? r2.getEntryKey() : "";
                    return entryKey1.compareToIgnoreCase(entryKey2);
                })
                .toList();

        return sortedResults;
    }

    private Set<ValidationKey> buildValidationUniverse(
            Set<ValidationKey> ruleKeys,
            Map<String, Map<String, Object>> userInput) {

        Set<ValidationKey> keys = new HashSet<>(ruleKeys);

        if (userInput == null) {
            return keys;
        }

        userInput.forEach((block, entries) -> {
            if (entries == null) {
                return;
            }

            entries.keySet().stream()
                    .filter(entry -> !BLOCK_EXISTS.equalsIgnoreCase(entry))
                    .forEach(entry ->
                            keys.add(new ValidationKey(block, entry)));
        });

        return keys;
    }
}
