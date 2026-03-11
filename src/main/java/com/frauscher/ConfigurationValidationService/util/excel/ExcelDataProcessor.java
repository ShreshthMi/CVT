package com.frauscher.ConfigurationValidationService.util.excel;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;

/**
 * Creates and formats data rows in Excel sheets.
 */
public class ExcelDataProcessor {
    
    private final ExcelStyleManager styleManager;
    
    public ExcelDataProcessor(ExcelStyleManager styleManager) {
        this.styleManager = styleManager;
    }
    
    /**
     * Builds data rows from the provided data list.
     */
    public void createDataRows(Sheet sheet, List<?> dataList, Field[] fields, String sheetName, int startRow) {
        int rowIdx = startRow;
        
        for (Object obj : dataList) {
            Row dataRow = sheet.createRow(rowIdx);
            
            for (int colIdx = 0; colIdx < fields.length; colIdx++) {
                Field field = fields[colIdx];
                field.setAccessible(true);
                
                try {
                    Object value = field.get(obj);
                    Cell cell = dataRow.createCell(colIdx);
                    
                    // Set cell value and apply styling
                    setCellValue(cell, value);
                    applyDataStyling(cell, obj, field, sheetName, rowIdx);
                    
                } catch (IllegalAccessException e) {
                    // Handle access exception - create empty cell
                    Cell cell = dataRow.createCell(colIdx);
                    cell.setCellValue("");
                    cell.setCellStyle(styleManager.getStringStyle());
                }
            }
            
            rowIdx++;
        }
    }
    
    /**
     * Sets the cell value based on the object type.
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof List) {
            // Handle List fields by joining with newline separator to match original ExcelSummaryUtil
            List<?> listValue = (List<?>) value;
            if (!listValue.isEmpty()) {
                // Convert all items to strings and join with newline
                String joinedValue = String.join("\n", listValue.stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
                cell.setCellValue(joinedValue);
            } else {
                cell.setCellValue("");
            }
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
    
    /**
     * Styles data cells based on field values and sheet type.
     */
    private void applyDataStyling(Cell cell, Object obj, Field field, String sheetName, int rowIdx) {
        try {
            // Apply fail row styling for Validation Results sheet
            if ("Validation Results".equals(sheetName) && obj instanceof ValidationResult) {
                Field statusField = obj.getClass().getDeclaredField("status");
                statusField.setAccessible(true);
                Object statusValue = statusField.get(obj);
                if ("FAIL".equalsIgnoreCase(String.valueOf(statusValue))) {
                    cell.setCellStyle(styleManager.getFailRowStyle(rowIdx));
                    return;
                }
            }

            cell.setCellStyle(styleManager.getDataRowStyle(rowIdx));
        } catch (Exception e) {
            cell.setCellStyle(styleManager.getDataRowStyle(rowIdx));
        }
    }

    /**
     * Auto-sizes columns to fit content and adjusts row heights.
     */
    public void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int colIdx = 0; colIdx < columnCount; colIdx++) {
            sheet.autoSizeColumn(colIdx);
            
            // Add a small buffer to prevent truncation (matching original ExcelSummaryUtil exactly)
            int currentWidth = sheet.getColumnWidth(colIdx);
            sheet.setColumnWidth(colIdx, currentWidth + 500); // Add buffer space
        }
    }
    
    /**
     * Adjusts row heights based on content.
     */
    public void adjustRowHeights(Sheet sheet, int startRow, int endRow) {
        for (int rowIdx = startRow; rowIdx <= endRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null) {
                // Calculate appropriate height based on cell content (matching original ExcelSummaryUtil)
                float maxHeight = 15; // Minimum height
                
                for (Cell cell : row) {
                    if (cell != null) {
                        try {
                            String content = "";
                            // Check cell type before getting string value
                            if (cell.getCellType() == CellType.STRING) {
                                content = cell.getStringCellValue();
                            } else if (cell.getCellType() == CellType.BOOLEAN) {
                                content = Boolean.toString(cell.getBooleanCellValue());
                            } else if (cell.getCellType() == CellType.NUMERIC) {
                                content = Double.toString(cell.getNumericCellValue());
                            }
                            
                            if (content != null && !content.isEmpty()) {
                                // Count newlines to determine if multi-line content
                                int lineCount = content.split("\n").length;
                                
                                // Calculate height based on line count
                                // Each line needs approximately 15 points
                                float cellHeight = lineCount * 15;
                                
                                // Add some buffer for wrapped text (check if content is long)
                                if (content.length() > 50 && lineCount == 1) {
                                    cellHeight = 20; // Extra height for potentially wrapped long text
                                }
                                
                                maxHeight = Math.max(maxHeight, cellHeight);
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
                
                // Set the calculated height with small buffer
                row.setHeightInPoints(maxHeight + 2);
            }
        }
    }
    
    /**
     * Applies data validation and formatting rules to cells.
     */
    public void applyDataValidation(Sheet sheet, Field[] fields, String sheetName) {
        // This method can be extended to add data validation rules
        // For example, dropdown lists, numeric ranges, etc.
        
        // Example: Add validation for specific sheets
        if ("Validation Results".equals(sheetName)) {
            // Add validation for status column if needed
            // Implementation details omitted for brevity
        }
    }
    
    /**
     * Creates summary statistics for data rows.
     */
    public void createSummaryStatistics(Sheet sheet, List<?> dataList, Field[] fields, String sheetName) {
        // This method can be extended to create summary rows
        // such as counts, averages, totals, etc.
        
        // Example: Create summary row at the end of data
        if (dataList != null && !dataList.isEmpty()) {
            int summaryRowIdx = sheet.getLastRowNum() + 1;
            Row summaryRow = sheet.createRow(summaryRowIdx);
            
            // Add summary content based on sheet type
            switch (sheetName) {
                case "Validation Results":
                    createValidationResultsSummary(summaryRow, dataList, fields);
                    break;
                // Add other sheet types as needed
            }
        }
    }
    
    /**
     * Creates summary statistics for Validation Results.
     */
    private void createValidationResultsSummary(Row summaryRow, List<?> dataList, Field[] fields) {
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
        
        // Create summary cells with header theme styling (matching original)
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
        
        // Note: The actual merging and center alignment should be handled by the calling method
        // This matches the original ExcelSummaryUtil approach where merging is done after cell creation
    }
}
