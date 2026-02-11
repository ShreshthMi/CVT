package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.TrackSectionDetail;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for extracting track section details from parsed configuration files
 */
@Service
public class TrackSectionExtractorService {

    /**
     * Extracts track section details from multiple parsed config files
     * Only processes files with trackSectionDetails=true
     */
    public List<TrackSectionDetail> extractTrackSectionDetails(List<ParsedConfigFile> files) {
        return files.stream()
                .filter(file -> file.isTrackSectionDetails())
                .map(this::extractTrackSectionDetailsFromFile)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Extracts track section details from a single parsed config file
     */
    private List<TrackSectionDetail> extractTrackSectionDetailsFromFile(ParsedConfigFile file) {
        List<TrackSectionDetail> trackSectionDetails = new ArrayList<>();
        
        // Process CFG_ZP_FMA1 blocks
        trackSectionDetails.addAll(extractTrackSectionFromFmaBlock(file, "CFG_ZP_FMA1", "1"));
        
        // Process CFG_ZP_FMA2 blocks
        trackSectionDetails.addAll(extractTrackSectionFromFmaBlock(file, "CFG_ZP_FMA2", "2"));
        
        return trackSectionDetails;
    }

    /**
     * Extracts track section details from a specific FMA block type
     */
    private List<TrackSectionDetail> extractTrackSectionFromFmaBlock(ParsedConfigFile file, String blockName, String fmaValue) {
        List<TrackSectionDetail> details = new ArrayList<>();
        
        // Get all blocks for this FMA type
        List<ConfigBlock> fmaBlocks = file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .collect(Collectors.toList());
        
        if (fmaBlocks.isEmpty()) {
            return details;
        }
        
        // Extract track section name from first block
        String tsName = extractTrackSectionName(fmaBlocks.get(0));
        
        // Separate blocks by DIR_INV value
        List<ConfigBlock> dirInv0Blocks = fmaBlocks.stream()
                .filter(block -> isDirInvValue(block, "0"))
                .collect(Collectors.toList());
        
        List<ConfigBlock> dirInv1Blocks = fmaBlocks.stream()
                .filter(block -> isDirInvValue(block, "1"))
                .collect(Collectors.toList());
        
        // Extract counting head details (DIR_INV = 0)
        List<String> chDpIds = extractDpIds(dirInv0Blocks);
        List<String> chDpNames = extractDpNames(dirInv0Blocks);
        List<String> chSlctTimeouts = extractSelectTimeouts(file, dirInv0Blocks);
        
        // Extract inverse counting head details (DIR_INV = 1)
        List<String> iChDpIds = extractDpIds(dirInv1Blocks);
        List<String> iChDpNames = extractDpNames(dirInv1Blocks);
        List<String> iChSlctTimeouts = extractSelectTimeouts(file, dirInv1Blocks);
        
        // Get DP ID and name from ID block (not from FMA blocks)
        String dpId = "";
        String dpName = "";
        
        // Find ID block that corresponds to this track section
        List<ConfigBlock> idBlocks = file.getBlocks().stream()
                .filter(block -> "ID".equals(block.getName()))
                .collect(Collectors.toList());
        
        // For each track section, find the corresponding ID block based on track section name
        // The ID block comment should match the track section name
        for (ConfigBlock idBlock : idBlocks) {
            String idBlockTrackSectionName = extractTrackSectionNameFromIdBlock(idBlock);
            if (tsName.equals(idBlockTrackSectionName)) {
                dpId = extractIdValue(idBlock);
                dpName = extractIdComment(idBlock);
                break;
            }
        }
        
        // If no matching ID block found by name, try to find by position/index
        if (dpId.isEmpty()) {
            // Use the first ID block as fallback or find by some other logic
            if (!idBlocks.isEmpty()) {
                dpId = extractIdValue(idBlocks.get(0));
                dpName = extractIdComment(idBlocks.get(0));
            }
        }
        
        // Create track section detail
        TrackSectionDetail detail = TrackSectionDetail.builder()
                .tsName(tsName)
                .fma(fmaValue)
                .dpId(dpId)
                .dpName(dpName)
                .chDpId(chDpIds)
                .chDpName(chDpNames)
                .chSlctTimeout(chSlctTimeouts)
                .iChDpId(iChDpIds)
                .iChDpName(iChDpNames)
                .iChSlctTimeout(iChSlctTimeouts)
                .build();
        
        details.add(detail);
        return details;
    }

    /**
     * Extracts track section name from ID block
     */
    private String extractTrackSectionNameFromIdBlock(ConfigBlock idBlock) {
        return idBlock.getEntries().stream()
                .filter(entry -> "ID".equals(entry.getKey()))
                .map(entry -> entry.getComment() != null ? entry.getComment() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts track section name from FMA block comment
     */
    private String extractTrackSectionName(ConfigBlock block) {
        return block.getEntries().stream()
                .filter(entry -> block.getName().equals(entry.getKey()))
                .findFirst()
                .map(entry -> entry.getComment() != null ? entry.getComment() : "")
                .orElse("");
    }

    /**
     * Checks if DIR_INV value matches the expected value
     */
    private boolean isDirInvValue(ConfigBlock block, String expectedValue) {
        return block.getEntries().stream()
                .filter(entry -> "DIR_INV".equals(entry.getKey()))
                .anyMatch(entry -> expectedValue.equals(entry.getValue()));
    }

    /**
     * Extracts DP IDs from blocks
     */
    private List<String> extractDpIds(List<ConfigBlock> blocks) {
        return blocks.stream()
                .map(this::extractIdValue)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Extracts DP names from blocks
     */
    private List<String> extractDpNames(List<ConfigBlock> blocks) {
        return blocks.stream()
                .map(this::extractIdComment)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Extracts ID value from block
     */
    private String extractIdValue(ConfigBlock block) {
        return block.getEntries().stream()
                .filter(entry -> "ID".equals(entry.getKey()))
                .map(entry -> entry.getValue() != null ? entry.getValue() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts ID comment from block
     */
    private String extractIdComment(ConfigBlock block) {
        return block.getEntries().stream()
                .filter(entry -> "ID".equals(entry.getKey()))
                .map(entry -> entry.getComment() != null ? entry.getComment() : "")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts select timeout values with CFG_TIMEOUT lookup and multiplication by 10
     * Following requirements: 
     * 1. Extract SLCT_TIMEOUT.value from FMA blocks
     * 2. Use that value to search CFG_TIMEOUT block by block index
     * 3. Fetch CFG_TIMEOUT.TIMEOUT_VALUE and multiply by 10
     */
    private List<String> extractSelectTimeouts(ParsedConfigFile file, List<ConfigBlock> blocks) {
        return blocks.stream()
                .map(block -> {
                    // Step 1: Extract SLCT_TIMEOUT.value from the FMA block
                    String slctTimeoutValue = block.getEntries().stream()
                            .filter(entry -> "SLCT_TIMEOUT".equals(entry.getKey()))
                            .map(entry -> entry.getValue() != null ? entry.getValue() : "0")
                            .findFirst()
                            .orElse("0");
                    
                    // Step 2: Use SLCT_TIMEOUT.value to search CFG_TIMEOUT block by block index
                    // Step 3: Fetch CFG_TIMEOUT.TIMEOUT_VALUE and multiply by 10
                    return ConfigExtractionUtil.extractTimeoutValue(file, slctTimeoutValue);
                })
                .collect(Collectors.toList());
    }
}
