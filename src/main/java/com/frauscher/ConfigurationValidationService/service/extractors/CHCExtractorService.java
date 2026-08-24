package com.frauscher.ConfigurationValidationService.service.extractors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;

@Service
public class CHCExtractorService {

    @Autowired
    private ValueMappingService valueMappingService;

    public List<CHCDetail> extractCHCDetails(List<ParsedConfigFile> parsedConfigFiles) {
        List<CHCDetail> chcDetailsList = new ArrayList<>();

        // Filter files with comDetails=false (CHC files)
        List<ParsedConfigFile> chcFiles = parsedConfigFiles.stream()
                .filter(file -> !file.isComDetails())
                .collect(Collectors.toList());

        for (ParsedConfigFile file : chcFiles) {
            CHCDetail chcDetails = extractCHCDetailsFromFile(file, parsedConfigFiles);
            if (chcDetails != null) {
                chcDetailsList.add(chcDetails);
            }
        }

        return chcDetailsList;
    }

    private CHCDetail extractCHCDetailsFromFile(ParsedConfigFile file, List<ParsedConfigFile> allParsedFiles) {
        Map<String, List<ConfigBlock>> blocksByName = file.getBlocks().stream()
                .collect(Collectors.groupingBy(ConfigBlock::getName));

        // Extract DP ID and DP Name from ID block
        String dpId = extractValueFromBlock(blocksByName, "ID", "ID");
        String dpName = extractCommentFromBlock(blocksByName, "ID", "ID");

        // Extract CFG_ZP block for interval, supervis_count, system_count, partial_count
        ConfigBlock cfgZpBlock = findBlockByName(blocksByName, "CFG_ZP");
        if (cfgZpBlock == null) {
            return null; // CFG_ZP is required
        }

        String interval = extractValueFromBlock(cfgZpBlock, "INTERVAL");
        String intervalMapped = valueMappingService.mapValue("INTERVAL", interval);

        String supervisCount = extractValueFromBlock(cfgZpBlock, "SUPERVIS_COUNT");
        String systemCount = extractValueFromBlock(cfgZpBlock, "SYSTEM_COUNT");
        String partialCount = extractValueFromBlock(cfgZpBlock, "PARTIAL_COUNT");

        // Extract CFG_CONTROL blocks (0, 1, or 2 occurrences)
        List<ConfigBlock> cfgControlBlocks = blocksByName.getOrDefault("CFG_CONTROL", new ArrayList<>());

        // Skip files without CFG_CONTROL blocks
        if (cfgControlBlocks.isEmpty()) {
            return null;
        }

        // Sort by block index to ensure consistent ordering
        cfgControlBlocks.sort((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()));

        // Extract data for first CFG_CONTROL (blockIndex=0)
        String tsName1 = "";
        String timeout1 = "";
        String dpId1 = "";
        String dpName1 = "";
        String fmaDtl1 = "";

        // Extract data for second CFG_CONTROL (blockIndex=1) if exists
        String tsName2 = "";
        String timeout2 = "";
        String dpId2 = "";
        String dpName2 = "";
        String fmaDtl2 = "";

        if (cfgControlBlocks.size() >= 1) {
            ConfigBlock controlBlock1 = cfgControlBlocks.get(0);
            tsName1 = extractCommentFromBlock(controlBlock1, "ID");

            String slctTimeout1 = extractValueFromBlock(controlBlock1, "SLCT_TIMEOUT");
            timeout1 = calculateTimeout(file, slctTimeout1);

            dpId1 = extractValueFromBlock(controlBlock1, "ID");
            dpName1 = findDPNameById(allParsedFiles, dpId1);

            String section1 = extractValueFromBlock(controlBlock1, "SECTION");
            fmaDtl1 = ConfigExtractionUtil.fmaFromSection(section1);
        }

        if (cfgControlBlocks.size() >= 2) {
            ConfigBlock controlBlock2 = cfgControlBlocks.get(1);
            tsName2 = extractCommentFromBlock(controlBlock2, "ID");

            String slctTimeout2 = extractValueFromBlock(controlBlock2, "SLCT_TIMEOUT");
            timeout2 = calculateTimeout(file, slctTimeout2);

            dpId2 = extractValueFromBlock(controlBlock2, "ID");
            dpName2 = findDPNameById(allParsedFiles, dpId2);

            String section2 = extractValueFromBlock(controlBlock2, "SECTION");
            fmaDtl2 = ConfigExtractionUtil.fmaFromSection(section2);
        }

        return CHCDetail.builder()
                .dpId(dpId)
                .dpName(dpName)
                .tsName1(tsName1)
                .timeout1(timeout1)
                .dpId1(dpId1)
                .dpName1(dpName1)
                .fmaDtl1(fmaDtl1)
                .tsName2(tsName2)
                .timeout2(timeout2)
                .dpId2(dpId2)
                .dpName2(dpName2)
                .fmaDtl2(fmaDtl2)
                .interval(intervalMapped)
                .supervisCount(supervisCount)
                .systemCount(systemCount)
                .partialCount(partialCount)
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
                    // Find ID block and get comment from ID entry
                    Optional<ConfigBlock> idBlock = file.getBlocks().stream()
                            .filter(block -> "ID".equals(block.getName()))
                            .findFirst();

                    if (idBlock.isPresent()) {
                        Optional<ConfigEntry> idEntry = idBlock.get().getEntries().stream()
                                .filter(entry -> "ID".equals(entry.getKey()))
                                .findFirst();

                        if (idEntry.isPresent()) {
                            return idEntry.get().getComment();
                        }
                    }
                    break;
                }
            }
        } catch (NumberFormatException e) {
            // Invalid dpId format
        }
        return "";
    }
}