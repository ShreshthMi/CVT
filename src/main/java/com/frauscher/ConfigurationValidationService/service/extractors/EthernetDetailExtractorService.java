package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.EthernetDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class EthernetDetailExtractorService {

    private final ValueMappingService valueMappingService;

    public EthernetDetailExtractorService(ValueMappingService valueMappingService) {
        this.valueMappingService = valueMappingService;
    }

    public List<EthernetDetail> extractEthernetDetails(List<ParsedConfigFile> parsedFiles) {
        List<EthernetDetail> ethernetDetailsList = new ArrayList<>();

        for (ParsedConfigFile file : parsedFiles) {
            // Only process COM files
            if (!file.isComDetails()) {
                continue;
            }

            EthernetDetail detail = extractEthernetDetailFromFile(file);
            if (detail != null) {
                ethernetDetailsList.add(detail);
            }
        }

        return ethernetDetailsList;
    }

    private EthernetDetail extractEthernetDetailFromFile(ParsedConfigFile file) {
        // Extract COM and ID from ID block
        String com = extractEntryComment(file, "ID", "ID");
        String id = extractEntryValue(file, "ID", "ID");

        if (com == null || com.isEmpty() || id == null || id.isEmpty()) {
            return null;
        }

        EthernetDetail.EthernetDetailBuilder builder = EthernetDetail.builder()
                .com(com)
                .id(id);

        // Extract IP addresses for network 1
        String ipNw1 = buildIpAddress(file, "CFG_MY_IP_NW1", 
            "MY_IP_NW1_B1", "MY_IP_NW1_B2", "MY_IP_NW1_B3", "MY_IP_NW1_B4");
        builder.ipNw1(ipNw1);

        // Extract IP addresses for network 2
        String ipNw2 = buildIpAddress(file, "CFG_MY_IP_NW2", 
            "MY_IP_NW2_B1", "MY_IP_NW2_B2", "MY_IP_NW2_B3", "MY_IP_NW2_B4");
        builder.ipNw2(ipNw2);

        // Extract subnet masks with value mapping
        String subnetMask1 = extractSubnetMask(file, "MY_MASK_NW1");
        builder.subnetMask1(subnetMask1);

        String subnetMask2 = extractSubnetMask(file, "MY_MASK_NW2");
        builder.subnetMask2(subnetMask2);

        // Extract destination IP addresses for network 1 (multiple blocks possible)
        List<String> destIpNw1List = buildIpAddressList(file, "CFG_INT_ID_DEST_NW1",
            "DEST_IP_INT_ID_NW1_B1", "DEST_IP_INT_ID_NW1_B2", "DEST_IP_INT_ID_NW1_B3", "DEST_IP_INT_ID_NW1_B4");
        builder.destIpNw1(destIpNw1List);

        // Extract destination IP addresses for network 2 (multiple blocks possible)
        List<String> destIpNw2List = buildIpAddressList(file, "CFG_INT_ID_DEST_NW2",
            "DEST_IP_INT_ID_NW2_B1", "DEST_IP_INT_ID_NW2_B2", "DEST_IP_INT_ID_NW2_B3", "DEST_IP_INT_ID_NW2_B4");
        builder.destIpNw2(destIpNw2List);

        // Extract forwarding ACD details
        List<String> fwrdAcdToDpIds = new ArrayList<>();
        List<String> fwrdAcdToDpDtls = new ArrayList<>();
        
        List<ConfigBlock> fwdAcdBlocks = file.getBlocks().stream()
                .filter(block -> "CFG_FWRD_ACD".equals(block.getName()))
                .toList();

        for (ConfigBlock block : fwdAcdBlocks) {
            String dpId = extractEntryValueFromBlock(block, "CAN_TX_ID");
            String dpDetail = extractEntryCommentFromBlock(block, "CAN_TX_ID");
            
            if (dpId != null && !dpId.isEmpty()) {
                fwrdAcdToDpIds.add(dpId);
                fwrdAcdToDpDtls.add(dpDetail != null ? dpDetail : "");
            }
        }

        builder.fwrdAcdToDpIds(fwrdAcdToDpIds);
        builder.fwrdAcdToDpDtls(fwrdAcdToDpDtls);

        // Extract interval with value mapping
        String interval = extractEntryValue(file, "CFG_INTERVAL", "INTERVAL");
        if (interval != null && !interval.isEmpty()) {
            interval = valueMappingService.mapValue("INTERVAL", interval);
        }
        builder.interval(interval != null ? interval : "");

        return builder.build();
    }

    private List<String> buildIpAddressList(ParsedConfigFile file, String blockName,
            String b1Key, String b2Key, String b3Key, String b4Key) {

        List<String> ipAddresses = new ArrayList<>();

        List<ConfigBlock> blocks = file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .toList();

        for (ConfigBlock block : blocks) {
            String b1 = extractEntryValueFromBlock(block, b1Key);
            String b2 = extractEntryValueFromBlock(block, b2Key);
            String b3 = extractEntryValueFromBlock(block, b3Key);
            String b4 = extractEntryValueFromBlock(block, b4Key);

            if (!b1.isEmpty() && !b2.isEmpty() && !b3.isEmpty() && !b4.isEmpty()) {
                ipAddresses.add(b1 + "." + b2 + "." + b3 + "." + b4);
            }
        }

        return ipAddresses;
    }

    private String buildIpAddress(ParsedConfigFile file, String blockName,
            String b1Key, String b2Key, String b3Key, String b4Key) {
        
        String b1 = extractEntryValue(file, blockName, b1Key);
        String b2 = extractEntryValue(file, blockName, b2Key);
        String b3 = extractEntryValue(file, blockName, b3Key);
        String b4 = extractEntryValue(file, blockName, b4Key);

        if (b1 == null || b1.isEmpty() || b2 == null || b2.isEmpty() || 
            b3 == null || b3.isEmpty() || b4 == null || b4.isEmpty()) {
            return "";
        }

        return b1 + "." + b2 + "." + b3 + "." + b4;
    }

    private String extractSubnetMask(ParsedConfigFile file, String maskKey) {
        String maskValue = extractEntryValue(file, "CFG_MY_MASK", maskKey);
        if (maskValue != null && !maskValue.isEmpty()) {
            return valueMappingService.mapValue(maskKey, maskValue);
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
