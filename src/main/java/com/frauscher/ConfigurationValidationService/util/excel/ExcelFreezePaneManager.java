package com.frauscher.ConfigurationValidationService.util.excel;

import org.apache.poi.ss.usermodel.Sheet;

/**
 * Manages freeze pane functionality for Excel sheets.
 * Provides intelligent freezing based on sheet structure and header configuration.
 */
public class ExcelFreezePaneManager {
    
    /**
     * Applies freeze panes to a sheet based on its structure and content.
     * 
     * @param sheet The Excel sheet to apply freeze panes to
     * @param sheetName The name of the sheet for determining freeze strategy
     * @param hasGroupedHeaders Whether the sheet has grouped headers (two header rows)
     * @param hasSummaryRow Whether the sheet has a summary row
     */
    public static void applyFreezePanes(Sheet sheet, String sheetName, boolean hasGroupedHeaders, boolean hasSummaryRow) {
        int freezeRow = calculateFreezeRow(sheetName, hasGroupedHeaders, hasSummaryRow);
        
        // Create freeze pane - freeze columns left of column 0 (none) and rows above freezeRow
        sheet.createFreezePane(0, freezeRow, 0, freezeRow);
    }
    
    /**
     * Calculates the appropriate freeze row based on sheet structure.
     * 
     * @param sheetName The name of the sheet
     * @param hasGroupedHeaders Whether the sheet has grouped headers
     * @param hasSummaryRow Whether the sheet has a summary row
     * @return The row number to freeze below (0-based)
     */
    private static int calculateFreezeRow(String sheetName, boolean hasGroupedHeaders, boolean hasSummaryRow) {
        // Validation Results sheet has special handling
        if ("Validation Results".equals(sheetName)) {
            // Validation Results has summary row (0) and header row (1) - freeze below row 2
            return 2;
        }
        
        // Sheets with grouped headers have two header rows
        if (hasGroupedHeaders) {
            // Grouped header row (0) and regular header row (1) - freeze below row 2
            return 2;
        }
        
        // Sheets with summary row have one summary row + one header row
        if (hasSummaryRow) {
            // Summary row (0) and header row (1) - freeze below row 2
            return 2;
        }
        
        // Default: sheets with single header row - freeze below row 1
        return 1;
    }
    
    /**
     * Applies freeze panes based on sheet name patterns.
     * This method uses predefined sheet types to determine freeze strategy.
     * 
     * @param sheet The Excel sheet to apply freeze panes to
     * @param sheetName The name of the sheet
     */
    public static void applyFreezePanesBySheetName(Sheet sheet, String sheetName) {
        if ("Validation Results".equals(sheetName)) {
            // Freeze summary row (row 0) and header row (row 1) - freeze panes below row 2
            sheet.createFreezePane(0, 2, 0, 2);
        } else if (hasGroupedHeaders(sheetName)) {
            // These sheets have grouped header row (row 0) and regular header row (row 1) - freeze panes below row 2
            sheet.createFreezePane(0, 2, 0, 2);
        } else {
            // For other sheets with just one header row, freeze below row 1
            sheet.createFreezePane(0, 1, 0, 1);
        }
    }
    
    /**
     * Checks if a sheet type typically has grouped headers.
     * 
     * @param sheetName The name of the sheet to check
     * @return true if the sheet typically has grouped headers
     */
    private static boolean hasGroupedHeaders(String sheetName) {
        return "CHC Details".equals(sheetName) || 
               "Track Section Details".equals(sheetName) || 
               "Supervisor Details".equals(sheetName) || 
               "IOEXB Behaviour Details".equals(sheetName) || 
               "IOEXB ACO Details".equals(sheetName) || 
               "Data Transmission Details".equals(sheetName) || 
               "Ethernet Details".equals(sheetName);
    }
    
    /**
     * Applies freeze panes with custom configuration.
     * 
     * @param sheet The Excel sheet to apply freeze panes to
     * @param colSplit The number of columns to freeze (0 = no column freeze)
     * @param rowSplit The number of rows to freeze (0 = no row freeze)
     * @param leftmostColumn The leftmost visible column after freeze
     * @param topRow The topmost visible row after freeze
     */
    public static void applyCustomFreezePanes(Sheet sheet, int colSplit, int rowSplit, int leftmostColumn, int topRow) {
        sheet.createFreezePane(colSplit, rowSplit, leftmostColumn, topRow);
    }
    
    /**
     * Removes all freeze panes from a sheet.
     * 
     * @param sheet The Excel sheet to remove freeze panes from
     */
    public static void removeFreezePanes(Sheet sheet) {
        // Create freeze pane with 0,0 to remove existing freeze panes
        sheet.createFreezePane(0, 0, 0, 0);
    }
    
    /**
     * Gets information about the current freeze pane configuration.
     * 
     * @param sheet The Excel sheet to check
     * @return Array containing [colSplit, rowSplit, leftmostColumn, topRow] or null if no freeze panes
     */
    public static int[] getFreezePaneInfo(Sheet sheet) {
        try {
            org.apache.poi.ss.util.PaneInformation paneInfo = sheet.getPaneInformation();
            if (paneInfo != null) {
                return new int[] {
                    paneInfo.getVerticalSplitPosition(),   // colSplit
                    paneInfo.getHorizontalSplitPosition(), // rowSplit
                    paneInfo.getVerticalSplitLeftColumn(), // leftmostColumn
                    paneInfo.getHorizontalSplitTopRow()   // topRow
                };
            }
        } catch (Exception e) {
            // Ignore exceptions and return null
        }
        return null;
    }
    
    /**
     * Checks if a sheet has any freeze panes applied.
     * 
     * @param sheet The Excel sheet to check
     * @return true if freeze panes are applied, false otherwise
     */
    public static boolean hasFreezePanes(Sheet sheet) {
        return getFreezePaneInfo(sheet) != null;
    }
    
    /**
     * Applies freeze panes optimized for different screen sizes.
     * 
     * @param sheet The Excel sheet to apply freeze panes to
     * @param sheetName The name of the sheet
     * @param screenSize The target screen size ("small", "medium", "large")
     */
    public static void applyOptimizedFreezePanes(Sheet sheet, String sheetName, String screenSize) {
        int freezeRow = calculateFreezeRow(sheetName, hasGroupedHeaders(sheetName), "Validation Results".equals(sheetName));
        
        switch (screenSize.toLowerCase()) {
            case "small":
                // For small screens, freeze fewer rows to maximize visible data
                sheet.createFreezePane(0, Math.min(freezeRow, 1), 0, Math.min(freezeRow, 1));
                break;
            case "medium":
                // For medium screens, use standard freeze configuration
                sheet.createFreezePane(0, freezeRow, 0, freezeRow);
                break;
            case "large":
                // For large screens, can afford to freeze more rows
                sheet.createFreezePane(0, freezeRow, 0, freezeRow);
                break;
            default:
                // Default to standard configuration
                sheet.createFreezePane(0, freezeRow, 0, freezeRow);
                break;
        }
    }
}
