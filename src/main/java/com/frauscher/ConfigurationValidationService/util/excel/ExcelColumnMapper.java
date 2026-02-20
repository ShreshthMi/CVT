package com.frauscher.ConfigurationValidationService.util.excel;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles column name mappings for different Excel sheet types.
 * Provides centralized management of field-to-display-name mappings.
 */
public class ExcelColumnMapper {
    
    private static final Map<String, Map<String, String>> COLUMN_NAME_MAPPINGS = new HashMap<>();
    
    static {
        initializeMappings();
    }
    
    /**
     * Initializes all column name mappings for different sheet types.
     */
    private static void initializeMappings() {
        // Validation Results column mappings
        Map<String, String> validationResultsMappings = new HashMap<>();
        validationResultsMappings.put("fileName", "File Name");
        validationResultsMappings.put("ruleType", "Rule Type");
        validationResultsMappings.put("configBlock", "Config Block Name");
        validationResultsMappings.put("entryKey", "Config Block Entry");
        validationResultsMappings.put("expectedValue", "Expected Value");
        validationResultsMappings.put("actualValue", "Actual Value");
        validationResultsMappings.put("status", "Status");
        COLUMN_NAME_MAPPINGS.put("Validation Results", validationResultsMappings);
        
        // DP Details column mappings
        Map<String, String> dpDetailsMappings = new HashMap<>();
        dpDetailsMappings.put("dpCanId", "DP Id");
        dpDetailsMappings.put("dpName", "DP Name");
        dpDetailsMappings.put("timeOut", "Configured\nTimeouts");
        dpDetailsMappings.put("commFail", "Comm Loss\n(COMM_FAIL)");
        dpDetailsMappings.put("behavGe", "Reset Restriction\n(BEHAV_GE)");
        dpDetailsMappings.put("clrTrack", "Track Clearing\n(CLR_TRACK)");
        dpDetailsMappings.put("resetIn", "Active Reset Restriction\n(RESET_IN)");
        dpDetailsMappings.put("resetOut", "Inactive Reset Restriction\n(RESET_OUT)");
        dpDetailsMappings.put("behavReset", "Toggle Switch\n(BEHAV_RESET)");
        dpDetailsMappings.put("behavSimul", "Toggle Switch\n(BEHAV_SIMUL)");
        COLUMN_NAME_MAPPINGS.put("DP Details", dpDetailsMappings);
        
        // Track Section Details column mappings
        Map<String, String> trackSectionMappings = new HashMap<>();
        trackSectionMappings.put("tsName", "TS Name");
        trackSectionMappings.put("dpId", "DP ID");
        trackSectionMappings.put("dpName", "DP Name");
        trackSectionMappings.put("fma", "FMA 1/2");
        trackSectionMappings.put("chDpId", "DP ID");
        trackSectionMappings.put("chDpName", "DP Name");
        trackSectionMappings.put("chSlctTimeout", "(SLCT_TIMEOUT)");
        trackSectionMappings.put("iChDpId", "DP ID");
        trackSectionMappings.put("iChDpName", "DP Name");
        trackSectionMappings.put("iChSlctTimeout", "(SLCT_TIMEOUT)");
        COLUMN_NAME_MAPPINGS.put("Track Section Details", trackSectionMappings);
        
        // CHC Details column name mappings
        Map<String, String> chcDetailMappings = new HashMap<>();
        chcDetailMappings.put("dpId", "DP ID");
        chcDetailMappings.put("dpName", "DP Name");
        chcDetailMappings.put("tsName1", "TS Name");
        chcDetailMappings.put("timeout1", "Timeout");
        chcDetailMappings.put("dpId1", "DP ID");
        chcDetailMappings.put("dpName1", "DP Name");
        chcDetailMappings.put("fmaDtl1", "FMA_1_2");
        chcDetailMappings.put("tsName2", "TS Name");
        chcDetailMappings.put("timeout2", "Timeout");
        chcDetailMappings.put("dpId2", "DP ID");
        chcDetailMappings.put("dpName2", "DP Name");
        chcDetailMappings.put("fmaDtl2", "FMA_1_2");
        chcDetailMappings.put("interval", "CHC\n(INTERVAL)");
        chcDetailMappings.put("supervisCount", "Reset permitted\n(SUPERVIS_COUNT)");
        chcDetailMappings.put("systemCount", "Occupancy Count\n(SYSTEM_COUNT)");
        chcDetailMappings.put("partialCount", "Partial Traversing\n(PARTIAL_COUNT)");
        COLUMN_NAME_MAPPINGS.put("CHC Details", chcDetailMappings);
        
        // Supervisor Details column name mappings
        Map<String, String> supervisorDetailMappings = new HashMap<>();
        supervisorDetailMappings.put("supName", "TS Name");
        supervisorDetailMappings.put("dpId", "DP Id");
        supervisorDetailMappings.put("dpName", "Dp Name");
        supervisorDetailMappings.put("supByTs", "Supervisor Name");
        supervisorDetailMappings.put("supByTsDpId", "DP Id");
        supervisorDetailMappings.put("supByTsDpName", "Dp Name");
        supervisorDetailMappings.put("supByTsFma", "FMA 1/2");
        supervisorDetailMappings.put("timeOut", "Timeout");
        supervisorDetailMappings.put("logicType", "Logic Type");
        supervisorDetailMappings.put("resetType", "Reset Type");
        supervisorDetailMappings.put("resetDelay", "Reset Delay");
        supervisorDetailMappings.put("autoResetType", "Auto Reset Type");
        supervisorDetailMappings.put("resetTimer", "Reset Timer");
        COLUMN_NAME_MAPPINGS.put("Supervisor Details", supervisorDetailMappings);
        
        // IOEXB Behaviour Details column name mappings
        Map<String, String> ioexbBehaviourMappings = new HashMap<>();
        ioexbBehaviourMappings.put("dpId", "DP Id");
        ioexbBehaviourMappings.put("dpName", "DP Name");
        ioexbBehaviourMappings.put("behavInput1", "(BEHAV_INPUT1)");
        ioexbBehaviourMappings.put("typeIn1", "(TYPE_IN1)");
        ioexbBehaviourMappings.put("behavInput2", "(BEHAV_INPUT2)");
        ioexbBehaviourMappings.put("typeIn2", "(TYPE_IN2)");
        ioexbBehaviourMappings.put("behavInput3", "(BEHAV_INPUT3)");
        ioexbBehaviourMappings.put("typeIn3", "(TYPE_IN3)");
        ioexbBehaviourMappings.put("behavIoexb", "(BEHAV_IOEXB)");
        ioexbBehaviourMappings.put("typeIoexb", "(TYPE_IOEXB)");
        ioexbBehaviourMappings.put("isCoopReset", "CO-OP Reset Applied?");
        ioexbBehaviourMappings.put("coopResetType", "Reset Type");
        ioexbBehaviourMappings.put("coopControlType", "Control Type");
        ioexbBehaviourMappings.put("resetTimeout", "Reset Timeout");
        COLUMN_NAME_MAPPINGS.put("IOEXB Behaviour Details", ioexbBehaviourMappings);
        
        // IOEXB ACO Details column name mappings
        Map<String, String> ioexbAcoMappings = new HashMap<>();
        ioexbAcoMappings.put("dpId", "DP Id");
        ioexbAcoMappings.put("dpName", "DP Name");
        ioexbAcoMappings.put("acoFma1", "TS Name");
        ioexbAcoMappings.put("clrOcc", "(CLR_OCC)");
        ioexbAcoMappings.put("typeAux1", "(TYPE_AUX1)");
        ioexbAcoMappings.put("typeAux2", "(TYPE_AUX2)");
        ioexbAcoMappings.put("aux1Out", "(AUX1_OUT)");
        ioexbAcoMappings.put("aux1NoNc", "(AUX1_NO_NC)");
        ioexbAcoMappings.put("aux2Out", "(AUX2_OUT)");
        ioexbAcoMappings.put("aux2NoNc", "(AUX2_NO_NC)");
        ioexbAcoMappings.put("fma12", "FMA_1_2");
        ioexbAcoMappings.put("timeOut", "(SLCT_TIMEOUT)");
        COLUMN_NAME_MAPPINGS.put("IOEXB ACO Details", ioexbAcoMappings);
        
        // Data Transmission Details column name mappings
        Map<String, String> dataTransmissionMappings = new HashMap<>();
        dataTransmissionMappings.put("dpId", "DP Id");
        dataTransmissionMappings.put("dpName", "DP Name");
        dataTransmissionMappings.put("safetyLevelIn", "(SAFETY_LEVEL_IN)");
        dataTransmissionMappings.put("safetyLevelOut", "(SAFETY_LEVEL_OUT)");
        dataTransmissionMappings.put("safeOutFdbckQuad", "(SAFE_OUT_FDBCK_QUAD)");
        dataTransmissionMappings.put("sourceDpId", "Source DP Id");
        dataTransmissionMappings.put("sourceDpName", "Source DP Name");
        dataTransmissionMappings.put("timeout", "(SLCT_TIMEOUT)");
        dataTransmissionMappings.put("nmbrOut", "(NMBR_OUT)");
        dataTransmissionMappings.put("position", "(POSITION)");
        COLUMN_NAME_MAPPINGS.put("Data Transmission Details", dataTransmissionMappings);
        
        // Ethernet Details column name mappings
        Map<String, String> ethernetMappings = new HashMap<>();
        ethernetMappings.put("comId", "COMM Id");
        ethernetMappings.put("ipNw1", "Ip Address");
        ethernetMappings.put("subnetMask1", "Subnet Mask");
        ethernetMappings.put("ipNw2", "IP Address");
        ethernetMappings.put("subnetMask2", "Subnet Mask");
        ethernetMappings.put("destIpNw1", "IP Primary");
        ethernetMappings.put("destIpNw2", "IP Secondary");
        ethernetMappings.put("fwrdAcdToDpIds", "DP Id");
        ethernetMappings.put("fwrdAcdToDpDtls", "DP Name (TS)");
        ethernetMappings.put("interval", "Interval");
        COLUMN_NAME_MAPPINGS.put("Ethernet Details", ethernetMappings);
    }
    
    /**
     * Gets the custom column name for a field based on data type.
     * 
     * @param dataType The sheet type (e.g., "Validation Results")
     * @param fieldName The field name to map
     * @return The display name for the column, or formatted field name if no mapping exists
     */
    public static String getColumnName(String dataType, String fieldName) {
        Map<String, String> mappings = COLUMN_NAME_MAPPINGS.get(dataType);
        if (mappings != null && mappings.containsKey(fieldName)) {
            return mappings.get(fieldName);
        }
        
        // Fallback to default formatting if no custom mapping found
        return fieldName.replaceAll("([a-z])([A-Z])", "$1 $2")
                      .replaceAll("^([a-z])", "$1".toUpperCase());
    }
    
    /**
     * Gets all column mappings for a specific data type.
     * 
     * @param dataType The sheet type
     * @return Map of field names to display names
     */
    public static Map<String, String> getColumnMappings(String dataType) {
        return COLUMN_NAME_MAPPINGS.getOrDefault(dataType, new HashMap<>());
    }
    
    /**
     * Checks if a data type has column mappings defined.
     * 
     * @param dataType The sheet type to check
     * @return true if mappings exist, false otherwise
     */
    public static boolean hasMappings(String dataType) {
        return COLUMN_NAME_MAPPINGS.containsKey(dataType);
    }
}
