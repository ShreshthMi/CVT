package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.IOEXBBehaviourDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for extracting IOEXB behaviour details from parsed configuration files
 */
@Slf4j
@Service
public class IOEXBBehaviourExtractorService {

    private final ValueMappingService valueMappingService;

    public IOEXBBehaviourExtractorService(ValueMappingService valueMappingService) {
        this.valueMappingService = valueMappingService;
    }

    /**
     * Extracts IOEXB behaviour details from parsed configuration files
     * Only processes files where ioexbDetails is true
     * 
     * @param parsedFiles list of parsed configuration files
     * @return list of IOEXB behaviour details
     */
    public List<IOEXBBehaviourDetail> extractIOEXBBehaviourDetails(List<ParsedConfigFile> parsedFiles) {
        List<IOEXBBehaviourDetail> ioexbBehaviourDetailsList = new ArrayList<>();

        for (ParsedConfigFile file : parsedFiles) {
            // Only process files with IOEXB details
            if (!file.isIoexbDetails()) {
                continue;
            }

            try {
                IOEXBBehaviourDetail details = extractIOEXBDetailsFromFile(file);
                if (details != null) {
                    ioexbBehaviourDetailsList.add(details);
                }
            } catch (Exception e) {
                log.error("Error extracting IOEXB behaviour details from file: {}", file.getFileName(), e);
            }
        }

        return ioexbBehaviourDetailsList;
    }

    /**
     * Extracts IOEXB behaviour details from a single parsed configuration file
     */
    private IOEXBBehaviourDetail extractIOEXBDetailsFromFile(ParsedConfigFile file) {
        // Extract DP ID and name from ID block
        String dpId = extractEntryValue(file, "ID", "ID");
        String dpName = extractEntryComment(file, "ID", "ID");

        // Find CFG_AXCNT block (appears only once)
        ConfigBlock axcntBlock = findBlockByName(file, "CFG_AXCNT");
        if (axcntBlock == null) {
            log.warn("CFG_AXCNT block not found in file: {}", file.getFileName());
            return null;
        }

        // Extract values from CFG_AXCNT block
        String behavInput1 = extractAndMapValue("BEHAV_INPUT1", axcntBlock);
        String typeIn1 = extractAndMapValue("TYPE_IN1", axcntBlock);
        String behavInput2 = extractAndMapValue("BEHAV_INPUT2", axcntBlock);
        String typeIn2 = extractAndMapValue("TYPE_IN2", axcntBlock);
        String behavInput3 = extractAndMapValue("BEHAV_INPUT3", axcntBlock);
        String typeIn3 = extractAndMapValue("TYPE_IN3", axcntBlock);
        String behavIoexb = extractAndMapValue("BEHAV_IOEXB", axcntBlock);
        String typeIoexb = extractAndMapValue("TYPE_IOEXB", axcntBlock);

        // Find CFG_COOP_RESET block (optional)
        ConfigBlock coopResetBlock = findBlockByName(file, "CFG_COOP_RESET");
        boolean isCoopReset = coopResetBlock != null;
        
        String coopResetType = "";
        String coopControlType = "";
        String resetTimeout = "";

        if (coopResetBlock != null) {
            coopResetType = extractAndMapValue("RESET_TYPE", coopResetBlock);
            coopControlType = extractAndMapValue("CTRL_TYPE", coopResetBlock);
            resetTimeout = extractEntryValueFromBlock(coopResetBlock, "RESET_TIMEOUT");
        }

        return IOEXBBehaviourDetail.builder()
                .dpId(dpId)
                .dpName(dpName)
                .behavInput1(behavInput1)
                .typeIn1(typeIn1)
                .behavInput2(behavInput2)
                .typeIn2(typeIn2)
                .behavInput3(behavInput3)
                .typeIn3(typeIn3)
                .behavIoexb(behavIoexb)
                .typeIoexb(typeIoexb)
                .isCoopReset(isCoopReset)
                .coopResetType(coopResetType)
                .coopControlType(coopControlType)
                .resetTimeout(resetTimeout)
                .build();
    }

    /**
     * Finds a specific block by name in the parsed file
     */
    private ConfigBlock findBlockByName(ParsedConfigFile file, String blockName) {
        return file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .findFirst()
                .orElse(null);
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
     * Extracts and maps value using the value mapping service
     */
    private String extractAndMapValue(String entryKey, ConfigBlock block) {
        String rawValue = block.getEntries().stream()
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> entry.getValue() != null ? entry.getValue() : "")
                .findFirst()
                .orElse("");
        
        return valueMappingService.mapValue(entryKey, rawValue);
    }
}
