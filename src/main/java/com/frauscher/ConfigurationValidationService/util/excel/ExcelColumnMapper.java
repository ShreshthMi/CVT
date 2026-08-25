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
        validationResultsMappings.put("fileName", "FILE NAME");
        validationResultsMappings.put("ruleType", "RULE TYPE");
        validationResultsMappings.put("configBlock", "CONFIG BLOCK NAME");
        validationResultsMappings.put("entryKey", "CONFIG BLOCK ENTRY");
        validationResultsMappings.put("expectedValue", "EXPECTED VALUE");
        validationResultsMappings.put("actualValue", "ACTUAL VALUE");
        validationResultsMappings.put("status", "STATUS");
        COLUMN_NAME_MAPPINGS.put("Validation Results", validationResultsMappings);
        
        // DP Details column mappings
        Map<String, String> dpDetailsMappings = new HashMap<>();
        dpDetailsMappings.put("dpCanId", "DP ID");
        dpDetailsMappings.put("dpName", "DP NAME");
        dpDetailsMappings.put("timeOut", "CONFIGURED\nTIMEOUTS");
        dpDetailsMappings.put("commFail", "COMM LOSS\nCOMM_FAIL");
        dpDetailsMappings.put("behavGe", "RESET RESTRICTION\nBEHAV_GE");
        dpDetailsMappings.put("clrTrack", "TRACK CLEARING\nCLR_TRACK");
        dpDetailsMappings.put("resetIn", "ACTIVE RESET RESTRICTION\nRESET_IN");
        dpDetailsMappings.put("resetOut", "INACTIVE RESET RESTRICTION\nRESET_OUT");
        dpDetailsMappings.put("behavReset", "TOGGLE SWITCH\nBEHAV_RESET");
        dpDetailsMappings.put("behavSimul", "TOGGLE SWITCH\nBEHAV_SIMUL");
        COLUMN_NAME_MAPPINGS.put("DP Details", dpDetailsMappings);
        
        // Track Section Details column mappings
        Map<String, String> trackSectionMappings = new HashMap<>();
        trackSectionMappings.put("tsName", "TS NAME");
        trackSectionMappings.put("dpId", "DP ID");
        trackSectionMappings.put("dpName", "DP NAME");
        trackSectionMappings.put("fma", "FMA 1/2");
        trackSectionMappings.put("chDpId", "DP ID");
        trackSectionMappings.put("chDpName", "DP NAME");
        trackSectionMappings.put("chSlctTimeout", "SLCT_TIMEOUT");
        trackSectionMappings.put("iChDpId", "DP ID");
        trackSectionMappings.put("iChDpName", "DP NAME");
        trackSectionMappings.put("iChSlctTimeout", "SLCT_TIMEOUT");
        COLUMN_NAME_MAPPINGS.put("Track Section Details", trackSectionMappings);
        
        // CHC Details column name mappings
        Map<String, String> chcDetailMappings = new HashMap<>();
        chcDetailMappings.put("dpId", "DP ID");
        chcDetailMappings.put("dpName", "DP NAME");
        chcDetailMappings.put("tsName1", "TS NAME");
        chcDetailMappings.put("timeout1", "TIMEOUT");
        chcDetailMappings.put("dpId1", "DP ID");
        chcDetailMappings.put("dpName1", "DP NAME");
        chcDetailMappings.put("fmaDtl1", "FMA_1_2");
        chcDetailMappings.put("tsName2", "TS NAME");
        chcDetailMappings.put("timeout2", "TIMEOUT");
        chcDetailMappings.put("dpId2", "DP ID");
        chcDetailMappings.put("dpName2", "DP NAME");
        chcDetailMappings.put("fmaDtl2", "FMA_1_2");
        chcDetailMappings.put("interval", "CHC\nINTERVAL");
        chcDetailMappings.put("supervisCount", "RESET PERMITTED\nSUPERVIS_COUNT");
        chcDetailMappings.put("systemCount", "OCCUPANCY COUNT\nSYSTEM_COUNT");
        chcDetailMappings.put("partialCount", "PARTIAL TRAVERSING\nPARTIAL_COUNT");
        COLUMN_NAME_MAPPINGS.put("CHC Details", chcDetailMappings);
        
        // Supervisor Details column name mappings
        Map<String, String> supervisorDetailMappings = new HashMap<>();
        supervisorDetailMappings.put("supName", "TS NAME");
        supervisorDetailMappings.put("dpId", "DP ID");
        supervisorDetailMappings.put("dpName", "DP NAME");
        supervisorDetailMappings.put("supByTs", "SUPERVISOR NAME");
        supervisorDetailMappings.put("supByTsDpId", "DP ID");
        supervisorDetailMappings.put("supByTsDpName", "DP NAME");
        supervisorDetailMappings.put("supByTsFma", "FMA 1/2");
        supervisorDetailMappings.put("timeOut", "TIMEOUT");
        supervisorDetailMappings.put("logicType", "LOGIC TYPE");
        supervisorDetailMappings.put("resetType", "RESET TYPE");
        supervisorDetailMappings.put("resetDelay", "RESET DELAY");
        supervisorDetailMappings.put("autoResetType", "AUTO RESET TYPE");
        supervisorDetailMappings.put("resetTimer", "RESET TIMER");
        COLUMN_NAME_MAPPINGS.put("Supervisor Details", supervisorDetailMappings);
        
        // IOEXB Behaviour Details column name mappings
        Map<String, String> ioexbBehaviourMappings = new HashMap<>();
        ioexbBehaviourMappings.put("dpId", "DP ID");
        ioexbBehaviourMappings.put("dpName", "DP NAME");
        ioexbBehaviourMappings.put("behavInput1", "BEHAV_INPUT1");
        ioexbBehaviourMappings.put("typeIn1", "TYPE_IN1");
        ioexbBehaviourMappings.put("behavInput2", "BEHAV_INPUT2");
        ioexbBehaviourMappings.put("typeIn2", "TYPE_IN2");
        ioexbBehaviourMappings.put("behavInput3", "BEHAV_INPUT3");
        ioexbBehaviourMappings.put("typeIn3", "TYPE_IN3");
        ioexbBehaviourMappings.put("behavIoexb", "BEHAV_IOEXB");
        ioexbBehaviourMappings.put("typeIoexb", "TYPE_IOEXB");
        ioexbBehaviourMappings.put("isCoopReset", "CO-OP RESET APPLIED?");
        ioexbBehaviourMappings.put("coopResetType", "RESET TYPE");
        ioexbBehaviourMappings.put("coopControlType", "CONTROL TYPE");
        ioexbBehaviourMappings.put("resetTimeout", "RESET TIMEOUT");
        COLUMN_NAME_MAPPINGS.put("IOEXB Behaviour Details", ioexbBehaviourMappings);
        
        // IOEXB ACO Details column name mappings
        Map<String, String> ioexbAcoMappings = new HashMap<>();
        ioexbAcoMappings.put("dpId", "DP ID");
        ioexbAcoMappings.put("dpName", "DP NAME");
        ioexbAcoMappings.put("slot", "SLOT");
        ioexbAcoMappings.put("acoFma1", "TS NAME");
        ioexbAcoMappings.put("clrOcc", "CLR_OCC");
        ioexbAcoMappings.put("typeAux1", "TYPE_AUX1");
        ioexbAcoMappings.put("typeAux2", "TYPE_AUX2");
        ioexbAcoMappings.put("aux1Out", "AUX1_OUT");
        ioexbAcoMappings.put("aux1NoNc", "AUX1_NO_NC");
        ioexbAcoMappings.put("aux2Out", "AUX2_OUT");
        ioexbAcoMappings.put("aux2NoNc", "AUX2_NO_NC");
        ioexbAcoMappings.put("fma12", "FMA_1_2");
        ioexbAcoMappings.put("timeOut", "SLCT_TIMEOUT");
        COLUMN_NAME_MAPPINGS.put("IOEXB ACO Details", ioexbAcoMappings);
        
        // Data Transmission Details column name mappings
        Map<String, String> dataTransmissionMappings = new HashMap<>();
        dataTransmissionMappings.put("dpId", "DP ID");
        dataTransmissionMappings.put("dpName", "DP NAME");
        dataTransmissionMappings.put("safetyLevelIn", "SAFETY_LEVEL_IN");
        dataTransmissionMappings.put("safetyLevelOut", "SAFETY_LEVEL_OUT");
        dataTransmissionMappings.put("safeOutFdbckQuad", "SAFE_OUT_FDBCK_QUAD");
        dataTransmissionMappings.put("sourceDpId", "SOURCE DP ID");
        dataTransmissionMappings.put("sourceDpName", "SOURCE DP NAME");
        dataTransmissionMappings.put("timeout", "SLCT_TIMEOUT");
        dataTransmissionMappings.put("nmbrOut", "NMBR_OUT");
        dataTransmissionMappings.put("position", "POSITION");
        COLUMN_NAME_MAPPINGS.put("Data Transmission Details", dataTransmissionMappings);
        
        // Ethernet Details column name mappings
        Map<String, String> ethernetMappings = new HashMap<>();
        ethernetMappings.put("comId", "COMM ID");
        ethernetMappings.put("ipNw1", "IP ADDRESS");
        ethernetMappings.put("subnetMask1", "SUBNET MASK");
        ethernetMappings.put("ipNw2", "IP ADDRESS");
        ethernetMappings.put("subnetMask2", "SUBNET MASK");
        ethernetMappings.put("destIpNw1", "IP PRIMARY");
        ethernetMappings.put("destIpNw2", "IP SECONDARY");
        ethernetMappings.put("fwrdAcdToDpIds", "DP ID");
        ethernetMappings.put("fwrdAcdToDpDtls", "DP NAME TS");
        ethernetMappings.put("interval", "INTERVAL");
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
