package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.IOEXBAcoDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for extracting IOEXB ACO (Axle Counting Output) details from parsed configuration files
 */
@Slf4j
@Service
public class IOEXBAcoExtractorService {

    private final ValueMappingService valueMappingService;

    public IOEXBAcoExtractorService(ValueMappingService valueMappingService) {
        this.valueMappingService = valueMappingService;
    }

    /**
     * Extracts IOEXB ACO details from parsed configuration files
     * Only processes files where acoIoexbDetails is true
     * 
     * @param parsedFiles list of parsed configuration files
     * @return list of IOEXB ACO details
     */
    public List<IOEXBAcoDetail> extractIOEXBAcoDetails(List<ParsedConfigFile> parsedFiles) {
        List<IOEXBAcoDetail> ioexbAcoDetailsList = new ArrayList<>();

        for (ParsedConfigFile file : parsedFiles) {
            // Only process files with IOEXB details
            if (!file.isAcoIoexbDetails()) {
                continue;
            }

            try {
                List<IOEXBAcoDetail> fileAcoDetails = extractAcoDetailsFromFile(file);
                ioexbAcoDetailsList.addAll(fileAcoDetails);
            } catch (Exception e) {
                log.error("Error extracting IOEXB ACO details from file: {}", file.getFileName(), e);
            }
        }

        return ioexbAcoDetailsList;
    }

    /**
     * Extracts ACO details from a single parsed configuration file: one row per
     * {@code CFG_SECTION_OUT} block, in file order.
     *
     * <p>Blocks are emitted 2 per attached ACO IO-EXB card (card i's two section outputs) and the
     * order is significant -- it determines the order of the output FMAs, which is what
     * {@code AcoExpectationsBuilder} validates positionally. This used to skip a block whose header
     * comment had already been seen, which silently dropped a row whenever a card drove the same track
     * section from both of its outputs. In a captured production package that lost 5 of 118 blocks, and
     * one card ({@code 250BT}/{@code 250BT}) rendered as a single row -- so those slots had no cell for
     * the annotator to mark and a positional FAIL on them survived only in {@code validation_results}.
     */
    private List<IOEXBAcoDetail> extractAcoDetailsFromFile(ParsedConfigFile file) {
        List<IOEXBAcoDetail> acoDetailsList = new ArrayList<>();

        // Extract DP ID and name from ID block
        String dpId = extractEntryValue(file, "ID", "ID");
        String dpName = extractEntryComment(file, "ID", "ID");

        // Find all CFG_SECTION_OUT blocks
        List<ConfigBlock> sectionOutBlocks = file.getBlocks().stream()
                .filter(block -> "CFG_SECTION_OUT".equals(block.getName()))
                .toList();

        for (int slot = 0; slot < sectionOutBlocks.size(); slot++) {
            IOEXBAcoDetail acoDetails = extractAcoDetailsFromSectionOutBlock(
                    sectionOutBlocks.get(slot), slot, dpId, dpName, file);
            if (acoDetails != null) {
                acoDetailsList.add(acoDetails);
            }
        }

        return acoDetailsList;
    }

    /**
     * Extracts ACO details from a single CFG_SECTION_OUT block
     */
    private IOEXBAcoDetail extractAcoDetailsFromSectionOutBlock(ConfigBlock sectionOutBlock, int slot,
                                                                  String dpId, String dpName,
                                                                  ParsedConfigFile file) {
        // Extract values from CFG_SECTION_OUT block
        String acoFma1 = extractEntryCommentFromBlock(sectionOutBlock, "CFG_SECTION_OUT");
        String clrOcc = extractAndMapValue("CLR_OCC", sectionOutBlock);
        String typeAux1 = extractAndMapValue("TYPE_AUX1", sectionOutBlock);
        String typeAux2 = extractAndMapValue("TYPE_AUX2", sectionOutBlock);
        String aux1Out = extractAndMapValue("AUX1_OUT", sectionOutBlock);
        String aux1NoNc = extractAndMapValue("AUX1_NO_NC", sectionOutBlock);
        String aux2Out = extractAndMapValue("AUX2_OUT", sectionOutBlock);
        String aux2NoNc = extractAndMapValue("AUX2_NO_NC", sectionOutBlock);
        
        // Extract SECTION value and add 1 to get FMA_1_2
        String sectionValue = extractEntryValueFromBlock(sectionOutBlock, "SECTION");
        String fma12 = ConfigExtractionUtil.fmaFromSection(sectionValue);

        // Extract SLCT_TIMEOUT and get corresponding timeout value
        String slctTimeout = extractEntryValueFromBlock(sectionOutBlock, "SLCT_TIMEOUT");
        String timeOut = "";
        if (slctTimeout != null && !slctTimeout.isEmpty()) {
            timeOut = ConfigExtractionUtil.extractTimeoutValue(file, slctTimeout);
        }

        return IOEXBAcoDetail.builder()
                .dpId(dpId)
                .dpName(dpName)
                .slot(String.valueOf(slot))
                .acoFma1(acoFma1)
                .clrOcc(clrOcc)
                .typeAux1(typeAux1)
                .typeAux2(typeAux2)
                .aux1Out(aux1Out)
                .aux1NoNc(aux1NoNc)
                .aux2Out(aux2Out)
                .aux2NoNc(aux2NoNc)
                .fma12(fma12)
                .timeOut(timeOut)
                .build();
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
