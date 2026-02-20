package com.frauscher.ConfigurationValidationService.util.excel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Handles sheet creation and configuration for Excel workbooks.
 * Manages sheet creation, naming, and basic setup operations.
 */
public class ExcelSheetBuilder {
    
    private final ExcelHeaderProcessor headerProcessor;
    private final ExcelDataProcessor dataProcessor;
    
    public ExcelSheetBuilder(ExcelHeaderProcessor headerProcessor,
                           ExcelDataProcessor dataProcessor) {
        this.headerProcessor = headerProcessor;
        this.dataProcessor = dataProcessor;
    }
    
    /**
     * Creates a new sheet with the given name in the workbook.
     * 
     * @param workbook The workbook to create the sheet in
     * @param sheetName The name for the new sheet
     * @return The created sheet
     */
    public Sheet createSheet(Workbook workbook, String sheetName) {
        return workbook.createSheet(sheetName);
    }
    
    /**
     * Builds a complete sheet with headers, data, and formatting.
     * 
     * @param workbook The workbook to create the sheet in
     * @param sheetName The name for the sheet
     * @param dataList The data to populate the sheet with
     * @param fields The fields to use for columns
     * @param hasGroupedHeaders Whether the sheet should have grouped headers
     * @param hasSummaryRow Whether the sheet should have a summary row
     * @return The fully built sheet
     */
    public Sheet buildCompleteSheet(Workbook workbook, String sheetName, List<?> dataList, 
                                   Field[] fields, boolean hasGroupedHeaders, boolean hasSummaryRow) {
        // Create the sheet
        Sheet sheet = createSheet(workbook, sheetName);
        
        // Determine starting row based on sheet structure
        int startRow = 0;
        
        // Create headers first (they will be shifted if summary row is needed)
        headerProcessor.createHeaders(sheet, fields, sheetName);
        
        // Add summary row if needed (for Validation Results) - this will shift headers down
        if (hasSummaryRow && "Validation Results".equals(sheetName)) {
            headerProcessor.createValidationResultsSummary(sheet, dataList, fields);
            startRow = 2; // Data starts after summary row (row 0) and header row (row 1)
        }
        
        // Create grouped headers if needed
        if (hasGroupedHeaders) {
            headerProcessor.createGroupedHeaders(sheet, sheetName, fields);
            if (hasSummaryRow && "Validation Results".equals(sheetName)) {
                startRow = 3; // Data starts after summary row, grouped header row, and regular header row
            } else {
                startRow = 2; // Data starts after grouped header row and regular header row
            }
        } else if (!hasSummaryRow) {
            startRow = 1; // Data starts after regular header row only
        }
        
        // Create data rows
        if (dataList != null && !dataList.isEmpty()) {
            dataProcessor.createDataRows(sheet, dataList, fields, sheetName, startRow);
        }
        
        // Apply freeze panes
        ExcelFreezePaneManager.applyFreezePanes(sheet, sheetName, hasGroupedHeaders, hasSummaryRow);
        
        // Auto-size columns
        dataProcessor.autoSizeColumns(sheet, fields.length);
        
        // Apply data validation if needed
        dataProcessor.applyDataValidation(sheet, fields, sheetName);
        
        return sheet;
    }
    
    /**
     * Creates a Validation Results sheet with special formatting.
     * 
     * @param workbook The workbook to create the sheet in
     * @param validationResults The validation results data
     * @param fields The fields to use for columns
     * @return The created Validation Results sheet
     */
    public Sheet buildValidationResultsSheet(Workbook workbook, List<?> validationResults, Field[] fields) {
        return buildCompleteSheet(workbook, "Validation Results", validationResults, fields, false, true);
    }
    
    /**
     * Creates a sheet with grouped headers.
     * 
     * @param workbook The workbook to create the sheet in
     * @param sheetName The name for the sheet
     * @param dataList The data to populate the sheet with
     * @param fields The fields to use for columns
     * @return The created sheet with grouped headers
     */
    public Sheet buildGroupedHeaderSheet(Workbook workbook, String sheetName, List<?> dataList, Field[] fields) {
        return buildCompleteSheet(workbook, sheetName, dataList, fields, true, false);
    }
    
    /**
     * Creates a standard sheet with simple headers.
     * 
     * @param workbook The workbook to create the sheet in
     * @param sheetName The name for the sheet
     * @param dataList The data to populate the sheet with
     * @param fields The fields to use for columns
     * @return The created standard sheet
     */
    public Sheet buildStandardSheet(Workbook workbook, String sheetName, List<?> dataList, Field[] fields) {
        return buildCompleteSheet(workbook, sheetName, dataList, fields, false, false);
    }
    
    /**
     * Creates multiple sheets based on a data map.
     * 
     * @param workbook The workbook to create sheets in
     * @param sheetDataMap Map of sheet names to data lists
     * @param sheetFieldsMap Map of sheet names to field arrays
     * @param sheetConfigMap Map of sheet names to configuration (grouped headers, summary row, etc.)
     */
    public void buildMultipleSheets(Workbook workbook, Map<String, List<?>> sheetDataMap,
                                   Map<String, Field[]> sheetFieldsMap,
                                   Map<String, SheetConfig> sheetConfigMap) {
        for (Map.Entry<String, List<?>> entry : sheetDataMap.entrySet()) {
            String sheetName = entry.getKey();
            List<?> dataList = entry.getValue();
            Field[] fields = sheetFieldsMap.get(sheetName);
            SheetConfig config = sheetConfigMap.getOrDefault(sheetName, new SheetConfig());
            
            buildCompleteSheet(workbook, sheetName, dataList, fields, config.hasGroupedHeaders, config.hasSummaryRow);
        }
    }
    
    /**
     * Applies final formatting to all sheets in a workbook.
     * 
     * @param workbook The workbook containing sheets to format
     */
    public void applyFinalFormatting(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            // Adjust row heights based on content
            dataProcessor.adjustRowHeights(sheet, 0, sheet.getLastRowNum());
            
            // Apply sheet-specific final formatting
            applySheetSpecificFormatting(sheet, sheetName);
        }
    }
    
    /**
     * Applies sheet-specific final formatting.
     * 
     * @param sheet The sheet to format
     * @param sheetName The name of the sheet
     */
    private void applySheetSpecificFormatting(Sheet sheet, String sheetName) {
        // Apply any sheet-specific formatting here
        // For example, special column widths, print settings, etc.
        
        switch (sheetName) {
            case "Validation Results":
                // Set specific column widths for Validation Results
                if (sheet.getColumnWidth(0) < 3000) {
                    sheet.setColumnWidth(0, 3000); // Rule Name column
                }
                if (sheet.getColumnWidth(1) < 4000) {
                    sheet.setColumnWidth(1, 4000); // Description column
                }
                break;
            case "CHC Details":
                // Set specific column widths for CHC Details
                break;
            // Add other sheet-specific formatting as needed
        }
    }
    
    /**
     * Configuration class for sheet building options.
     */
    public static class SheetConfig {
        private boolean hasGroupedHeaders = false;
        private boolean hasSummaryRow = false;
        private boolean hasDataValidation = false;
        private boolean autoSizeColumns = true;
        
        public SheetConfig() {}
        
        public SheetConfig(boolean hasGroupedHeaders, boolean hasSummaryRow) {
            this.hasGroupedHeaders = hasGroupedHeaders;
            this.hasSummaryRow = hasSummaryRow;
        }
        
        // Getters and setters
        public boolean hasGroupedHeaders() { return hasGroupedHeaders; }
        public void setHasGroupedHeaders(boolean hasGroupedHeaders) { this.hasGroupedHeaders = hasGroupedHeaders; }
        
        public boolean hasSummaryRow() { return hasSummaryRow; }
        public void setHasSummaryRow(boolean hasSummaryRow) { this.hasSummaryRow = hasSummaryRow; }
        
        public boolean hasDataValidation() { return hasDataValidation; }
        public void setHasDataValidation(boolean hasDataValidation) { this.hasDataValidation = hasDataValidation; }
        
        public boolean autoSizeColumns() { return autoSizeColumns; }
        public void setAutoSizeColumns(boolean autoSizeColumns) { this.autoSizeColumns = autoSizeColumns; }
    }
    
    /**
     * Creates a workbook with optimized settings for performance.
     * 
     * @return A new XSSFWorkbook with optimized settings
     */
    public static Workbook createOptimizedWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Set workbook properties for better performance
        // (Note: Some of these might not be available in all POI versions)
        
        return workbook;
    }
    
    /**
     * Validates sheet name and ensures it's valid for Excel.
     * 
     * @param sheetName The sheet name to validate
     * @return A valid sheet name (original if valid, modified if invalid)
     */
    public static String validateSheetName(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            return "Sheet";
        }
        
        // Excel sheet names cannot be longer than 31 characters
        if (sheetName.length() > 31) {
            sheetName = sheetName.substring(0, 31);
        }
        
        // Remove invalid characters: \ / ? * [ ] 
        sheetName = sheetName.replaceAll("[\\\\/\\?\\*\\[\\]]", "");
        
        return sheetName.isEmpty() ? "Sheet" : sheetName;
    }
}
