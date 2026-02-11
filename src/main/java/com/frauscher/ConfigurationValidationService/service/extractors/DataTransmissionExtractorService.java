package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.DataTransmissionDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DataTransmissionExtractorService {

    private final ValueMappingService valueMappingService;

    public DataTransmissionExtractorService(ValueMappingService valueMappingService) {
        this.valueMappingService = valueMappingService;
    }

    public List<DataTransmissionDetail> extractDataTransmissionDetails(List<ParsedConfigFile> parsedFiles) {
        List<DataTransmissionDetail> dataTransmissionDetailsList = new ArrayList<>();

        for (ParsedConfigFile file : parsedFiles) {
            // Only process IOEXB files
            if (!file.isIoexbDetails()) {
                continue;
            }

            dataTransmissionDetailsList.addAll(extractDataTransmissionDetailsFromFile(parsedFiles, file));
        }

        return dataTransmissionDetailsList;
    }

    private List<DataTransmissionDetail> extractDataTransmissionDetailsFromFile(List<ParsedConfigFile> allParsedFiles, ParsedConfigFile file) {
        List<DataTransmissionDetail> dataTransmissionDetailsList = new ArrayList<>();

        // Extract DP ID and name from ID block
        String dpId = extractEntryValue(file, "ID", "ID");
        String dpName = extractEntryComment(file, "ID", "ID");

        // Find all CFG_DATA_SAFETY_LEVEL blocks
        List<ConfigBlock> safetyLevelBlocks = file.getBlocks().stream()
                .filter(block -> "CFG_DATA_SAFETY_LEVEL".equals(block.getName()))
                .toList();

        // Find all CFG_DATA_OUT blocks
        List<ConfigBlock> dataOutBlocks = file.getBlocks().stream()
                .filter(block -> "CFG_DATA_OUT".equals(block.getName()))
                .toList();

        // Process each pair of blocks by index
        int maxBlocks = Math.max(safetyLevelBlocks.size(), dataOutBlocks.size());
        for (int i = 0; i < maxBlocks; i++) {
            DataTransmissionDetail details = extractDataTransmissionDetailsForIndex(
                allParsedFiles, file, i, 
                i < safetyLevelBlocks.size() ? safetyLevelBlocks.get(i) : null,
                i < dataOutBlocks.size() ? dataOutBlocks.get(i) : null,
                dpId, dpName
            );
            
            if (details != null) {
                dataTransmissionDetailsList.add(details);
            }
        }

        return dataTransmissionDetailsList;
    }

    private DataTransmissionDetail extractDataTransmissionDetailsForIndex(
            List<ParsedConfigFile> allParsedFiles,
            ParsedConfigFile file, int blockIndex,
            ConfigBlock safetyLevelBlock, 
            ConfigBlock dataOutBlock,
            String dpId, String dpName) {

        var builder = DataTransmissionDetail.builder()
                .dpId(dpId)
                .dpName(dpName);

        // Extract from CFG_DATA_SAFETY_LEVEL block
        if (safetyLevelBlock != null) {
            String safetyLevelIn = extractEntryValueFromBlock(safetyLevelBlock, "SAFETY_LEVEL_IN");
            String safetyLevelOut = extractEntryValueFromBlock(safetyLevelBlock, "SAFETY_LEVEL_OUT");
            String safeOutFdbckQuad = extractEntryValueFromBlock(safetyLevelBlock, "SAFE_OUT_FDBCK_QUAD");

            // Apply value mappings
            builder.safetyLevelIn(valueMappingService.mapValue("SAFETY_LEVEL_IN", safetyLevelIn));
            builder.safetyLevelOut(valueMappingService.mapValue("SAFETY_LEVEL_OUT", safetyLevelOut));
            builder.safeOutFdbckQuad(valueMappingService.mapValue("SAFE_OUT_FDBCK_QUAD", safeOutFdbckQuad));
        }

        // Extract from CFG_DATA_OUT block
        if (dataOutBlock != null) {
            String sourceDpId = extractEntryValueFromBlock(dataOutBlock, "ID");
            String nmbrOut = extractEntryValueFromBlock(dataOutBlock, "NMBR_OUT");
            String position = extractEntryValueFromBlock(dataOutBlock, "POSITION");
            String slctTimeout = extractEntryValueFromBlock(dataOutBlock, "SLCT_TIMEOUT");

            builder.sourceDpId(sourceDpId);
            
            // Get source DP name by navigating to the config with this ID
            String sourceDpName = getSourceDpName(allParsedFiles, sourceDpId);
            builder.sourceDpName(sourceDpName);
            
            builder.nmbrOut(nmbrOut);
            builder.position(position);

            // Calculate timeout based on CFG_DATA_OUT.SLCT_TIMEOUT value
            String timeout = calculateTimeout(allParsedFiles, slctTimeout);
            builder.timeout(timeout);
        }

        return builder.build();
    }

    private String getSourceDpName(List<ParsedConfigFile> allParsedFiles, String sourceDpId) {
        if (sourceDpId == null || sourceDpId.isEmpty()) {
            return "";
        }

        // Look for ID block with this value across all parsed files
        for (ParsedConfigFile parsedFile : allParsedFiles) {
            for (ConfigBlock block : parsedFile.getBlocks()) {
                if ("ID".equals(block.getName())) {
                    String idValue = extractEntryValueFromBlock(block, "ID");
                    if (sourceDpId.equals(idValue)) {
                        return extractEntryCommentFromBlock(block, "ID");
                    }
                }
            }
        }

        return "";
    }

    private String calculateTimeout(List<ParsedConfigFile> allParsedFiles, String slctTimeout) {
        if (slctTimeout == null || slctTimeout.isEmpty()) {
            return "";
        }

        try {
            int timeoutIndex = Integer.parseInt(slctTimeout);
            
            // Find CFG_TIMEOUT block with this block index across all parsed files
            for (ParsedConfigFile parsedFile : allParsedFiles) {
                for (ConfigBlock block : parsedFile.getBlocks()) {
                    if ("CFG_TIMEOUT".equals(block.getName()) && block.getBlockIndex() == timeoutIndex) {
                        String timeoutValue = extractEntryValueFromBlock(block, "TIMEOUT_VALUE");
                        if (timeoutValue != null && !timeoutValue.isEmpty()) {
                            int value = Integer.parseInt(timeoutValue);
                            return String.valueOf(value * 10); // Multiply by 10
                        }
                        break;
                    }
                }
            }
        } catch (NumberFormatException e) {
            log.debug("Invalid timeout index: {}", slctTimeout);
        }

        return "";
    }

    /**
     * Extracts entry value from any block in the file
     */
    private String extractEntryValue(ParsedConfigFile file, String blockName, String entryKey) {
        return file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> entry.getValue() != null ? entry.getValue() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts entry comment from any block in the file
     */
    private String extractEntryComment(ParsedConfigFile file, String blockName, String entryKey) {
        return file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> entry.getComment() != null ? entry.getComment() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts entry value from a specific block
     */
    private String extractEntryValueFromBlock(ConfigBlock block, String entryKey) {
        return block.getEntries().stream()
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> entry.getValue() != null ? entry.getValue() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts entry comment from a specific block
     */
    private String extractEntryCommentFromBlock(ConfigBlock block, String entryKey) {
        return block.getEntries().stream()
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> entry.getComment() != null ? entry.getComment() : "")
                .findFirst()
                .orElse("");
    }
}
