package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.SupervisorDetail;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SupervisorExtractorService {

    @Autowired
    private ValueMappingService valueMappingService;

    public List<SupervisorDetail> extractSupervisorDetails(List<ParsedConfigFile> parsedConfigFiles) {
        List<SupervisorDetail> supervisorDetailsList = new ArrayList<>();

        // Filter files with comDetails=false (CHC files)
        List<ParsedConfigFile> chcFiles = parsedConfigFiles.stream()
                .filter(file -> !file.isComDetails())
                .collect(Collectors.toList());

        for (ParsedConfigFile file : chcFiles) {
            // Extract FMA1 supervisor details
            SupervisorDetail fma1Details = extractSupervisorDetailsForFMA(file, "CFG_SUPERVIS_FMA1", "CFG_AUTORESET_FMA1", parsedConfigFiles);
            if (fma1Details != null) {
                supervisorDetailsList.add(fma1Details);
            }

            // Extract FMA2 supervisor details
            SupervisorDetail fma2Details = extractSupervisorDetailsForFMA(file, "CFG_SUPERVIS_FMA2", "CFG_AUTORESET_FMA2", parsedConfigFiles);
            if (fma2Details != null) {
                supervisorDetailsList.add(fma2Details);
            }
        }

        // Sort supervisor details by dp_id
        supervisorDetailsList.sort((a, b) -> {
            // Compare by dp_id
            String dpIdA = a.getDpId() != null ? a.getDpId() : "";
            String dpIdB = b.getDpId() != null ? b.getDpId() : "";
            
            // Try to parse as integers for numeric comparison
            try {
                int numA = Integer.parseInt(dpIdA);
                int numB = Integer.parseInt(dpIdB);
                return Integer.compare(numA, numB);
            } catch (NumberFormatException e) {
                // If not numeric, fall back to string comparison
                return dpIdA.compareTo(dpIdB);
            }
        });

        return supervisorDetailsList;
    }

    private SupervisorDetail extractSupervisorDetailsForFMA(ParsedConfigFile file, String supervisBlockName, String autoResetBlockName, List<ParsedConfigFile> allParsedFiles) {
        Map<String, List<ConfigBlock>> blocksByName = file.getBlocks().stream()
                .collect(Collectors.groupingBy(ConfigBlock::getName));

        List<ConfigBlock> supervisBlocks = blocksByName.getOrDefault(supervisBlockName, new ArrayList<>());
        
        // Skip if no supervisor blocks found
        if (supervisBlocks.isEmpty()) {
            return null;
        }

        // Sort by block index to ensure consistent ordering
        supervisBlocks.sort((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()));

        // Extract DP ID and DP Name from ID block
        String dpId = extractValueFromBlock(blocksByName, "ID", "ID");
        String dpName = extractCommentFromBlock(blocksByName, "ID", "ID");

        // Get supervisor name from first supervisor block comment
        String supName = supervisBlocks.get(0).getEntries().stream()
                .filter(entry -> supervisBlockName.equals(entry.getKey()))
                .map(ConfigEntry::getComment)
                .findFirst()
                .orElse("");

        // Extract common fields from first supervisor block
        ConfigBlock firstSupervisBlock = supervisBlocks.get(0);
        String resetType = extractValueFromBlock(firstSupervisBlock, "RESET_TYPE");
        String resetDelay = extractValueFromBlock(firstSupervisBlock, "RESET_DELAY");

        // Apply value mapping
        String resetTypeMapped = valueMappingService.mapValue("RESET_TYPE", resetType);

        // Extract supervisor by track section arrays
        List<String> supByTs = new ArrayList<>();
        List<String> supByTsDpId = new ArrayList<>();
        List<String> supByTsDpName = new ArrayList<>();
        List<String> supByTsFma = new ArrayList<>();
        List<String> timeOut = new ArrayList<>();
        List<String> logicType = new ArrayList<>();

        for (ConfigBlock supervisBlock : supervisBlocks) {
            // Track section names (comments)
            String tsName = extractCommentFromBlock(supervisBlock, "ID");
            supByTs.add(tsName);

            // Track section DP IDs
            String tsDpId = extractValueFromBlock(supervisBlock, "ID");
            supByTsDpId.add(tsDpId);

            // Track section DP names (lookup from other files)
            String tsDpName = findDPNameById(allParsedFiles, tsDpId);
            supByTsDpName.add(tsDpName);

            // FMA section (SECTION + 1)
            String section = extractValueFromBlock(supervisBlock, "SECTION");
            supByTsFma.add(ConfigExtractionUtil.fmaFromSection(section));

            // Timeout values
            String slctTimeout = extractValueFromBlock(supervisBlock, "SLCT_TIMEOUT");
            String timeout = calculateTimeout(file, slctTimeout);
            timeOut.add(timeout);
            
            // Logic type values
            String blockLogicType = extractValueFromBlock(supervisBlock, "LOGIC_TYPE");
            String logicTypeMapped = valueMappingService.mapValue("LOGIC_TYPE", blockLogicType);
            logicType.add(logicTypeMapped);
        }

        // Extract auto-reset details (optional block)
        ConfigBlock autoResetBlock = findBlockByName(blocksByName, autoResetBlockName);
        String autoResetType = "";
        String resetTimer = "";
        
        if (autoResetBlock != null) {
            autoResetType = valueMappingService.mapValue("RESET_TYPE", extractValueFromBlock(autoResetBlock, "RESET_TYPE"));
            String resetTimerValue = extractValueFromBlock(autoResetBlock, "RESET_TIMER");
            try {
                int timer = Integer.parseInt(resetTimerValue);
                resetTimer = String.valueOf(timer * 10);
            } catch (NumberFormatException e) {
                resetTimer = resetTimerValue;
            }
        }

        return SupervisorDetail.builder()
                .supName(supName)
                .dpId(dpId)
                .dpName(dpName)
                .supByTs(supByTs)
                .supByTsDpId(supByTsDpId)
                .supByTsDpName(supByTsDpName)
                .supByTsFma(supByTsFma)
                .timeOut(timeOut)
                .logicType(logicType)
                .resetType(resetTypeMapped)
                .resetDelay(resetDelay)
                .autoResetType(autoResetType)
                .resetTimer(resetTimer)
                .build();
    }

    private String extractValueFromBlock(Map<String, List<ConfigBlock>> blocksByName, String blockName, String entryKey) {
        ConfigBlock block = findBlockByName(blocksByName, blockName);
        if (block != null) {
            return extractValueFromBlock(block, entryKey);
        }
        return "";
    }

    private String extractValueFromBlock(ConfigBlock block, String entryKey) {
        Optional<ConfigEntry> entry = block.getEntries().stream()
                .filter(e -> entryKey.equals(e.getKey()))
                .findFirst();
        return entry.map(ConfigEntry::getValue).orElse("");
    }

    private String extractCommentFromBlock(Map<String, List<ConfigBlock>> blocksByName, String blockName, String entryKey) {
        ConfigBlock block = findBlockByName(blocksByName, blockName);
        if (block != null) {
            return extractCommentFromBlock(block, entryKey);
        }
        return "";
    }

    private String extractCommentFromBlock(ConfigBlock block, String entryKey) {
        Optional<ConfigEntry> entry = block.getEntries().stream()
                .filter(e -> entryKey.equals(e.getKey()))
                .findFirst();
        return entry.map(ConfigEntry::getComment).orElse("");
    }

    private ConfigBlock findBlockByName(Map<String, List<ConfigBlock>> blocksByName, String blockName) {
        List<ConfigBlock> blocks = blocksByName.get(blockName);
        return (blocks != null && !blocks.isEmpty()) ? blocks.get(0) : null;
    }

    private String calculateTimeout(ParsedConfigFile file, String slctTimeoutValue) {
        try {
            int timeoutIndex = Integer.parseInt(slctTimeoutValue);
            String timeoutValueStr = ConfigExtractionUtil.extractTimeoutValue(file, String.valueOf(timeoutIndex));
            return timeoutValueStr; // ConfigExtractionUtil already multiplies by 10
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String findDPNameById(List<ParsedConfigFile> allParsedFiles, String dpId) {
        try {
            int targetId = Integer.parseInt(dpId);
            for (ParsedConfigFile file : allParsedFiles) {
                if (file.getId() == targetId) {
                    // Find ID block and get comment
                    return file.getBlocks().stream()
                            .filter(block -> "ID".equals(block.getName()))
                            .flatMap(block -> block.getEntries().stream())
                            .filter(entry -> "ID".equals(entry.getKey()))
                            .map(ConfigEntry::getComment)
                            .findFirst()
                            .orElse("");
                }
            }
        } catch (NumberFormatException e) {
            return "";
        }
        return "";
    }
}
