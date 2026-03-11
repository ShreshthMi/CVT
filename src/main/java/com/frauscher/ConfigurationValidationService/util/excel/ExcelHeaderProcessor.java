package com.frauscher.ConfigurationValidationService.util.excel;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;

/**
 * Handles header creation and processing for Excel sheets.
 * Manages both regular headers and grouped headers with styling.
 */
public class ExcelHeaderProcessor {
    
    private final ExcelStyleManager styleManager;
    
    public ExcelHeaderProcessor(ExcelStyleManager styleManager) {
        this.styleManager = styleManager;
    }
    
    /**
     * Creates headers for a sheet based on field names and sheet type.
     */
    public void createHeaders(Sheet sheet, Field[] fields, String sheetName) {
        Row header = sheet.createRow(0);
        
        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i].getName();
            String headerName = ExcelColumnMapper.getColumnName(sheetName, fieldName);
            Cell headerCell = header.createCell(i);
            headerCell.setCellValue(headerName);
            
            // Apply conditional styling based on sheet type
            applyHeaderStyling(headerCell, fieldName, sheetName);
        }
    }
    
    /**
     * Applies header styling to all header cells consistently (black bold text on grey).
     */
    private void applyHeaderStyling(Cell headerCell, String fieldName, String sheetName) {
        headerCell.setCellStyle(styleManager.getHeaderStyle());
    }
    
    /**
     * Creates grouped headers for specific sheet types.
     * Returns the field index map for merging cells.
     */
    public Map<String, Integer> createGroupedHeaders(Sheet sheet, String sheetName, Field[] fields) {
        Map<String, Integer> fieldIndexMap = createFieldIndexMap(fields);
        
        switch (sheetName) {
            case "IOEXB ACO Details":
                createIOEXBACOGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "IOEXB Behaviour Details":
                createIOEXBBehaviourGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "Supervisor Details":
                createSupervisorGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "Data Transmission Details":
                createDataTransmissionGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "Ethernet Details":
                createEthernetGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "CHC Details":
                createCHCGroupedHeaders(sheet, fieldIndexMap);
                break;
            case "Track Section Details":
                createTrackSectionGroupedHeaders(sheet, fieldIndexMap);
                break;
        }
        
        return fieldIndexMap;
    }
    
    /**
     * Creates a field index map for quick field lookup.
     */
    private Map<String, Integer> createFieldIndexMap(Field[] fields) {
        Map<String, Integer> fieldIndexMap = new HashMap<>();
        for (int i = 0; i < fields.length; i++) {
            fieldIndexMap.put(fields[i].getName(), i);
        }
        return fieldIndexMap;
    }
    
    /**
     * Creates grouped headers for IOEXB ACO Details sheet.
     */
    private void createIOEXBACOGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position
            if (fieldName.equals("dpId") || fieldName.equals("dpName")) {
                // First 2 columns blank
                groupedCell.setCellValue("");
            } else if (fieldName.equals("acoFma1")) {
                // "IOEXB Axle Counting Information" - merge acoFma1 through timeOut (11 cells total: columns 3-13)
                groupedCell.setCellValue("IOEXB Axle Counting Information");
                
                Integer clrOccIdx = fieldIndexMap.get("clrOcc");
                Integer typeAux1Idx = fieldIndexMap.get("typeAux1");
                Integer typeAux2Idx = fieldIndexMap.get("typeAux2");
                Integer aux1OutIdx = fieldIndexMap.get("aux1Out");
                Integer aux1NoNcIdx = fieldIndexMap.get("aux1NoNc");
                Integer aux2OutIdx = fieldIndexMap.get("aux2Out");
                Integer aux2NoNcIdx = fieldIndexMap.get("aux2NoNc");
                Integer fma12Idx = fieldIndexMap.get("fma12");
                Integer timeOutIdx = fieldIndexMap.get("timeOut");
                
                if (clrOccIdx != null && typeAux1Idx != null && typeAux2Idx != null && 
                    aux1OutIdx != null && aux1NoNcIdx != null && aux2OutIdx != null && 
                    aux2NoNcIdx != null && fma12Idx != null && timeOutIdx != null) {
                    // Merge from acoFma1 to timeOut (11 cells total: columns 3-13)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, timeOutIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Helper method to get field name by index from field index map.
     */
    private String getFieldByIndex(Map<String, Integer> fieldIndexMap, int index) {
        for (Map.Entry<String, Integer> entry : fieldIndexMap.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return "";
    }
    
    
    /**
     * Applies center alignment to merged cells in the grouped header row.
     */
    private void applyCenterAlignmentToMergedCells(Sheet sheet) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            org.apache.poi.ss.util.CellRangeAddress mergedRegion = sheet.getMergedRegion(i);
            if (mergedRegion.getFirstRow() == 0) {
                Row firstRow = sheet.getRow(mergedRegion.getFirstRow());
                if (firstRow != null) {
                    Cell firstCell = firstRow.getCell(mergedRegion.getFirstColumn());
                    if (firstCell != null) {
                        CellStyle style = sheet.getWorkbook().createCellStyle();
                        style.cloneStyleFrom(styleManager.getHeaderStyle());
                        style.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
                        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
                        firstCell.setCellStyle(style);
                    }
                }
            }
        }
    }
    
    /**
     * Creates grouped headers for IOEXB Behaviour Details sheet.
     */
    private void createIOEXBBehaviourGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position
            if (fieldName.equals("dpId") || fieldName.equals("dpName")) {
                // First 2 columns blank
                groupedCell.setCellValue("");
            } else if (fieldName.equals("behavInput1")) {
                // "Input Reset Information" - merge behavInput1 through typeIoexb (9 cells total: columns 3-11)
                groupedCell.setCellValue("Input Reset Information");
                
                Integer typeIoexbIdx = fieldIndexMap.get("typeIoexb");
                
                if (typeIoexbIdx != null) {
                    // Merge from behavInput1 to typeIoexb
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, typeIoexbIdx));
                }
            } else if (fieldName.equals("behavOutput1")) {
                // "Output Reset Information" - merge behavOutput1 through typeIoexbOut (9 cells total: columns 12-20)
                groupedCell.setCellValue("Output Reset Information");
                
                Integer typeIoexbOutIdx = fieldIndexMap.get("typeIoexbOut");
                
                if (typeIoexbOutIdx != null) {
                    // Merge from behavOutput1 to typeIoexbOut
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, typeIoexbOutIdx));
                }
            } else if (fieldName.equals("isCoopReset")) {
                // "Co-operative Reset Information" - merge isCoopReset through resetTimeout (4 cells total)
                groupedCell.setCellValue("Co-operative Reset Information");
                
                Integer resetTimeoutIdx = fieldIndexMap.get("resetTimeout");
                
                if (resetTimeoutIdx != null) {
                    // Merge from isCoopReset to resetTimeout
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, resetTimeoutIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates grouped headers for Supervisor Details sheet.
     */
    private void createSupervisorGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position
            if (fieldName.equals("supName") || fieldName.equals("dpId") || fieldName.equals("dpName")) {
                // First 3 columns blank
                groupedCell.setCellValue("");
            } else if (fieldName.equals("supByTs")) {
                // "Supervised by" - merge supByTs through logicType (7 cells total: columns 4-10)
                groupedCell.setCellValue("Supervised by");
                
                Integer supByTsDpIdIdx = fieldIndexMap.get("supByTsDpId");
                Integer supByTsDpNameIdx = fieldIndexMap.get("supByTsDpName");
                Integer supByTsFmaIdx = fieldIndexMap.get("supByTsFma");
                Integer timeOutIdx = fieldIndexMap.get("timeOut");
                Integer logicTypeIdx = fieldIndexMap.get("logicType");
                
                if (supByTsDpIdIdx != null && supByTsDpNameIdx != null && supByTsFmaIdx != null && 
                    timeOutIdx != null && logicTypeIdx != null && logicTypeIdx > i) {
                    // Merge from supByTs to logicType (7 cells total: columns 4-10)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, logicTypeIdx));
                }
            } else if (fieldName.equals("resetType")) {
                // "Reset Information" - merge resetType through resetTimer (5 cells total: columns 10-14)
                groupedCell.setCellValue("Reset Information");
                
                Integer resetDelayIdx = fieldIndexMap.get("resetDelay");
                Integer autoResetTypeIdx = fieldIndexMap.get("autoResetType");
                Integer resetTimerIdx = fieldIndexMap.get("resetTimer");
                
                if (resetDelayIdx != null && autoResetTypeIdx != null && resetTimerIdx != null && resetTimerIdx > i) {
                    // Merge from resetType to resetTimer (5 cells total: columns 10-14)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, resetTimerIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates grouped headers for Data Transmission Details sheet.
     */
    private void createDataTransmissionGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position
            if (fieldName.equals("dpId") || fieldName.equals("dpName")) {
                // First 2 columns blank
                groupedCell.setCellValue("");
            } else if (fieldName.equals("safetyLevelIn")) {
                // "Data Safety Level" - merge safetyLevelIn through safeOutFdbckQuad (4 cells total: columns 3-6)
                groupedCell.setCellValue("Data Safety Level");
                
                Integer safetyLevelOutIdx = fieldIndexMap.get("safetyLevelOut");
                Integer safeOutFdbckQuadIdx = fieldIndexMap.get("safeOutFdbckQuad");
                
                if (safetyLevelOutIdx != null && safeOutFdbckQuadIdx != null && safeOutFdbckQuadIdx > i) {
                    // Merge from safetyLevelIn to safeOutFdbckQuad (4 cells total: columns 3-6)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, safeOutFdbckQuadIdx));
                }
            } else if (fieldName.equals("sourceDpId")) {
                // "Output data transmission" - merge sourceDpId through position (6 cells total: columns 7-12)
                groupedCell.setCellValue("Output data transmission");
                
                Integer sourceDpNameIdx = fieldIndexMap.get("sourceDpName");
                Integer timeoutIdx = fieldIndexMap.get("timeout");
                Integer nmbrOutIdx = fieldIndexMap.get("nmbrOut");
                Integer positionIdx = fieldIndexMap.get("position");
                
                if (sourceDpNameIdx != null && timeoutIdx != null && nmbrOutIdx != null && positionIdx != null && positionIdx > i) {
                    // Merge from sourceDpId to position (6 cells total: columns 7-12)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, positionIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates grouped headers for Ethernet Details sheet.
     */
    private void createEthernetGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position
            if (fieldName.equals("comId")) {
                // First column blank
                groupedCell.setCellValue("");
            } else if (fieldName.equals("ipNw1")) {
                // "Own IP Address" - merge ipNw1 through subnetMask2 (4 cells total: columns 2-5)
                groupedCell.setCellValue("Own IP Address");
                
                Integer subnetMask1Idx = fieldIndexMap.get("subnetMask1");
                Integer ipNw2Idx = fieldIndexMap.get("ipNw2");
                Integer subnetMask2Idx = fieldIndexMap.get("subnetMask2");
                
                if (subnetMask1Idx != null && ipNw2Idx != null && subnetMask2Idx != null && subnetMask2Idx > i) {
                    // Merge from ipNw1 to subnetMask2 (4 cells total: columns 2-5)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, subnetMask2Idx));
                }
            } else if (fieldName.equals("destIpNw1")) {
                // "Destination" - merge destIpNw1 through destIpNw2 (2 cells total: columns 6-7)
                groupedCell.setCellValue("Destination");
                
                Integer destIpNw2Idx = fieldIndexMap.get("destIpNw2");
                
                if (destIpNw2Idx != null && destIpNw2Idx > i) {
                    // Merge from destIpNw1 to destIpNw2 (2 cells total: columns 6-7)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, destIpNw2Idx));
                }
            } else if (fieldName.equals("fwrdAcdToDpIds")) {
                // "Axle Counting Data Forwarding" - merge fwrdAcdToDpIds through interval (3 cells total: columns 8-10)
                groupedCell.setCellValue("Axle Counting Data Forwarding");
                
                Integer fwrdAcdToDpDtlsIdx = fieldIndexMap.get("fwrdAcdToDpDtls");
                Integer intervalIdx = fieldIndexMap.get("interval");
                
                if (fwrdAcdToDpDtlsIdx != null && intervalIdx != null && intervalIdx > i) {
                    // Merge from fwrdAcdToDpIds to interval (3 cells total: columns 8-10)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, intervalIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates grouped headers for CHC Details sheet.
     */
    private void createCHCGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position and apply conditional coloring
            if (fieldName.equals("dpId") || fieldName.equals("dpName")) {
                // Leave blank for dpId and dpName
                groupedCell.setCellValue("");
            } else if (fieldName.equals("tsName1")) {
                // "Controlled by Track Section" - merge tsName1 through fmaDtl1
                groupedCell.setCellValue("Controlled by Track Section");
                
                Integer timeout1Idx = fieldIndexMap.get("timeout1");
                Integer dpId1Idx = fieldIndexMap.get("dpId1");
                Integer dpName1Idx = fieldIndexMap.get("dpName1");
                Integer fmaDtl1Idx = fieldIndexMap.get("fmaDtl1");
                
                if (timeout1Idx != null && dpId1Idx != null && dpName1Idx != null && fmaDtl1Idx != null) {
                    // Merge from tsName1 to fmaDtl1 (5 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, fmaDtl1Idx));
                }
            } else if (fieldName.equals("tsName2")) {
                // "Controlled by Track Section" - merge tsName2 through fmaDtl2
                groupedCell.setCellValue("Controlled by Track Section");
                
                Integer timeout2Idx = fieldIndexMap.get("timeout2");
                Integer dpId2Idx = fieldIndexMap.get("dpId2");
                Integer dpName2Idx = fieldIndexMap.get("dpName2");
                Integer fmaDtl2Idx = fieldIndexMap.get("fmaDtl2");
                
                if (timeout2Idx != null && dpId2Idx != null && dpName2Idx != null && fmaDtl2Idx != null) {
                    // Merge from tsName2 to fmaDtl2 (5 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, fmaDtl2Idx));
                }
            } else if (fieldName.equals("interval")) {
                // "CHC CFG_ZP" - merge interval through partialCount
                groupedCell.setCellValue("CHC CFG_ZP");
                // Apply default header style for interval group
                groupedCell.setCellStyle(styleManager.getHeaderStyle());
                
                Integer supervisCountIdx = fieldIndexMap.get("supervisCount");
                Integer systemCountIdx = fieldIndexMap.get("systemCount");
                Integer partialCountIdx = fieldIndexMap.get("partialCount");
                
                if (supervisCountIdx != null && systemCountIdx != null && partialCountIdx != null) {
                    // Merge from interval to partialCount (4 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, partialCountIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates grouped headers for Track Section Details sheet.
     */
    private void createTrackSectionGroupedHeaders(Sheet sheet, Map<String, Integer> fieldIndexMap) {
        // Shift all existing rows down by 1 to make space for grouped header
        sheet.shiftRows(0, sheet.getLastRowNum(), 1);
        
        // Create grouped header row at position 0
        Row groupedHeader = sheet.createRow(0);
        
        // Create grouped header cells
        for (int i = 0; i < fieldIndexMap.size(); i++) {
            String fieldName = getFieldByIndex(fieldIndexMap, i);
            Cell groupedCell = groupedHeader.createCell(i);
            groupedCell.setCellStyle(styleManager.getHeaderStyle());
            
            // Set grouped header text based on field position and apply conditional coloring
            if (fieldName.equals("tsName")) {
                // Blank cell for tsName
                groupedCell.setCellValue("");
                // Apply default header style
                groupedCell.setCellStyle(styleManager.getHeaderStyle());
            } else if (fieldName.equals("dpId")) {
                // "Evaluating Counting Head" - merge dpId through fma
                groupedCell.setCellValue("Evaluating Counting Head");
                // Apply default header style for evaluating counting head group
                groupedCell.setCellStyle(styleManager.getHeaderStyle());
                
                Integer dpNameIdx = fieldIndexMap.get("dpName");
                Integer fmaIdx = fieldIndexMap.get("fma");
                
                if (dpNameIdx != null && fmaIdx != null && fmaIdx > i) {
                    // Merge from dpId to fma (3 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, fmaIdx));
                }
            } else if (fieldName.equals("chDpId")) {
                // "Counting Head" - merge chDpId through chSlctTimeout
                groupedCell.setCellValue("Counting Head (DIR_INV = 0)");
                
                Integer chDpNameIdx = fieldIndexMap.get("chDpName");
                Integer chSlctTimeoutIdx = fieldIndexMap.get("chSlctTimeout");
                
                if (chDpNameIdx != null && chSlctTimeoutIdx != null && chSlctTimeoutIdx > i) {
                    // Merge from chDpId to chSlctTimeout (3 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, chSlctTimeoutIdx));
                }
            } else if (fieldName.equals("iChDpId")) {
                // "Inverse Counting Head" - merge iChDpId through iChSlctTimeout
                groupedCell.setCellValue("Inverse Counting Head (DIR_INV = 1)");
                
                Integer iChDpNameIdx = fieldIndexMap.get("iChDpName");
                Integer iChSlctTimeoutIdx = fieldIndexMap.get("iChSlctTimeout");
                
                if (iChDpNameIdx != null && iChSlctTimeoutIdx != null && iChSlctTimeoutIdx > i) {
                    // Merge from iChDpId to iChSlctTimeout (3 cells total)
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, i, iChSlctTimeoutIdx));
                }
            } else {
                groupedCell.setCellValue("");
            }
        }
        
        // Apply center alignment to merged cells
        applyCenterAlignmentToMergedCells(sheet);
    }
    
    /**
     * Creates summary row for Validation Results sheet.
     */
    public void createValidationResultsSummary(Sheet sheet, List<?> dataList, Field[] fields) {
        // Shift existing header and data rows down by 1
        if (sheet.getLastRowNum() >= 0) {
            sheet.shiftRows(0, sheet.getLastRowNum() + 1, 1);
        }
        
        Row summaryRow = sheet.createRow(0);
        int passCount = 0;
        int failCount = 0;
        
        // Count passes and fails
        for (Object obj : dataList) {
            if (obj instanceof ValidationResult) {
                try {
                    Field statusField = obj.getClass().getDeclaredField("status");
                    statusField.setAccessible(true);
                    Object statusValue = statusField.get(obj);
                    if ("PASS".equalsIgnoreCase(String.valueOf(statusValue))) {
                        passCount++;
                    } else if ("FAIL".equalsIgnoreCase(String.valueOf(statusValue))) {
                        failCount++;
                    }
                } catch (Exception e) {
                    // Ignore counting errors
                }
            }
        }
        
        // Create summary cells with header theme styling
        for (int colIdx = 0; colIdx < fields.length; colIdx++) {
            Cell cell = summaryRow.createCell(colIdx);
            cell.setCellStyle(styleManager.getHeaderStyle());
            
            if (colIdx == 0) {
                cell.setCellValue("Validation Result");
            } else if (colIdx <= 5) { // Columns 1-5 (upto Actual Value) will be merged
                cell.setCellValue(""); // Empty cells for merging
            } else if (colIdx == fields.length - 1) { // Status column
                cell.setCellValue("Pass: " + passCount + ", Fail: " + failCount);
            } else {
                cell.setCellValue("");
            }
        }
        
        // Merge first 6 columns (0-5) for summary label and empty space
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        
        // Apply center alignment to the merged summary cell
        CellStyle summaryStyle = sheet.getWorkbook().createCellStyle();
        summaryStyle.cloneStyleFrom(styleManager.getHeaderStyle());
        summaryStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        summaryStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        
        // Get the first cell of the merged region and apply the center-aligned style
        Cell mergedCell = summaryRow.getCell(0);
        if (mergedCell != null) {
            mergedCell.setCellStyle(summaryStyle);
        }
    }
}
