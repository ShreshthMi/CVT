package com.frauscher.ConfigurationValidationService.util.excel;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * Manages Excel styling including fonts, colors, and cell styles.
 * Provides reusable styling components for consistent Excel formatting.
 */
public class ExcelStyleManager {
    
    private final Workbook workbook;
    private final CellStyle headerStyle;
    private final CellStyle dataRowStyle1;
    private final CellStyle dataRowStyle2;
    private final CellStyle greenTextStyle1;
    private final CellStyle greenTextStyle2;
    private final CellStyle yellowTextStyle1;
    private final CellStyle yellowTextStyle2;
    private final CellStyle whiteTextStyle1;
    private final CellStyle whiteTextStyle2;
    private final CellStyle stringStyle;
    
    public ExcelStyleManager(Workbook wb) {
        this.workbook = wb;
        this.stringStyle = createStringStyle();
        this.headerStyle = createHeaderStyle();
        this.dataRowStyle1 = createDataStyle(new byte[]{(byte)0x10, (byte)0x48, (byte)0x61}); // #104861
        this.dataRowStyle2 = createDataStyle(new byte[]{(byte)0x20, (byte)0x68, (byte)0x81}); // #206881
        this.greenTextStyle1 = createColoredTextStyle(dataRowStyle1, new byte[]{(byte)0x41, (byte)0xD9, (byte)0x74}); // #41D974
        this.greenTextStyle2 = createColoredTextStyle(dataRowStyle2, new byte[]{(byte)0x41, (byte)0xD9, (byte)0x74}); // #41D974
        this.yellowTextStyle1 = createColoredTextStyle(dataRowStyle1, new byte[]{(byte)0xFF, (byte)0xFF, (byte)0x00}); // #FFFF00
        this.yellowTextStyle2 = createColoredTextStyle(dataRowStyle2, new byte[]{(byte)0xFF, (byte)0xFF, (byte)0x00}); // #FFFF00
        this.whiteTextStyle1 = createColoredTextStyle(dataRowStyle1, new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF}); // #FFFFFF
        this.whiteTextStyle2 = createColoredTextStyle(dataRowStyle2, new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF}); // #FFFFFF
    }
    
    /**
     * Creates a string cell style to prevent Excel auto-formatting.
     */
    private CellStyle createStringStyle() {
        CellStyle stringStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        stringStyle.setDataFormat(format.getFormat("@"));
        stringStyle.setWrapText(true);
        stringStyle.setLocked(false);
        stringStyle.setHidden(false);
        stringStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        stringStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        return stringStyle;
    }
    
    /**
     * Creates header style with dark blue background, white text, and borders.
     */
    private CellStyle createHeaderStyle() {
        CellStyle headerStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        headerStyle.setDataFormat(format.getFormat("@"));
        headerStyle.setWrapText(true);
        headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        
        // Set header colors and borders
        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;
            
            // Create border with color #E8E8E8 and line weight MEDIUM
            XSSFColor borderColor = new XSSFColor(new byte[]{(byte)0xE8, (byte)0xE8, (byte)0xE8}, null);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setTopBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setBottomBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setLeftBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setRightBorderColor(borderColor);
            
            // Background color #0B3040 (dark blue)
            XSSFColor bgColor = new XSSFColor(new byte[]{(byte)11, (byte)48, (byte)64}, null);
            headerStyle.setFillForegroundColor(bgColor);
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            
            // Font: Calibri, size 11, bold, white color
            XSSFFont headerFont = xssfWb.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short)11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
        }
        
        return headerStyle;
    }
    
    /**
     * Creates data row style with specified background color, borders, and indentation.
     */
    private CellStyle createDataStyle(byte[] rgbColor) {
        CellStyle dataStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        dataStyle.setDataFormat(format.getFormat("@"));
        dataStyle.setWrapText(true);
        dataStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        dataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        dataStyle.setIndention((short)1); // Add indent of 1
        
        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;
            
            // Create border with color #E8E8E8 and line weight MEDIUM
            XSSFColor borderColor = new XSSFColor(new byte[]{(byte)0xE8, (byte)0xE8, (byte)0xE8}, null);
            dataStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            dataStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            dataStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            dataStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)dataStyle).setTopBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)dataStyle).setBottomBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)dataStyle).setLeftBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)dataStyle).setRightBorderColor(borderColor);
            
            XSSFColor bgColor = new XSSFColor(rgbColor, null);
            dataStyle.setFillForegroundColor(bgColor);
            dataStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            
            XSSFFont font = xssfWb.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short)11);
            font.setColor(IndexedColors.WHITE.getIndex());
            dataStyle.setFont(font);
        }
        
        return dataStyle;
    }
    
    /**
     * Creates colored text style based on a base style.
     */
    private CellStyle createColoredTextStyle(CellStyle baseStyle, byte[] fontColor) {
        CellStyle coloredStyle = workbook.createCellStyle();
        coloredStyle.cloneStyleFrom(baseStyle);
        
        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;
            XSSFFont font = xssfWb.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short)11);
            XSSFColor color = new XSSFColor(fontColor, null);
            font.setColor(color);
            coloredStyle.setFont(font);
        }
        
        return coloredStyle;
    }
    
    /**
     * Creates a font with specified RGB color.
     */
    public Font createFontWithColor(byte r, byte g, byte b) {
        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;
            XSSFFont font = xssfWb.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short)11);
            font.setBold(true); // Ensure font is bold
            XSSFColor color = new XSSFColor(new byte[]{r, g, b}, null);
            font.setColor(color);
            return font;
        }
        return workbook.createFont();
    }
    
    // Getter methods for different styles
    public CellStyle getHeaderStyle() { return headerStyle; }
    public CellStyle getDataRowStyle1() { return dataRowStyle1; }
    public CellStyle getDataRowStyle2() { return dataRowStyle2; }
    public CellStyle getGreenTextStyle1() { return greenTextStyle1; }
    public CellStyle getGreenTextStyle2() { return greenTextStyle2; }
    public CellStyle getYellowTextStyle1() { return yellowTextStyle1; }
    public CellStyle getYellowTextStyle2() { return yellowTextStyle2; }
    public CellStyle getStringStyle() { return stringStyle; }
    
    /**
     * Gets data row style based on row index for alternating colors.
     */
    public CellStyle getDataRowStyle(int rowIndex) {
        return (rowIndex % 2 == 0) ? dataRowStyle1 : dataRowStyle2;
    }
    
    /**
     * Gets colored text style based on row index and color.
     */
    public CellStyle getColoredTextStyle(int rowIndex, String color) {
        boolean isEvenRow = rowIndex % 2 == 0;
        switch (color.toLowerCase()) {
            case "green":
                return isEvenRow ? greenTextStyle1 : greenTextStyle2;
            case "yellow":
                return isEvenRow ? yellowTextStyle1 : yellowTextStyle2;
            case "white":
                return isEvenRow ? whiteTextStyle1 : whiteTextStyle2;
            default:
                return getDataRowStyle(rowIndex);
        }
    }
    
    /**
     * Gets cell style with conditional text coloring based on column and sheet.
     * This method applies the same logic as the original ExcelSummaryUtil.
     */
    public CellStyle getCellStyleForColumn(String headerName, String fieldName, int rowIndex, String sheetName) {
        // Check for Track Section Details - Counting Head fields (green text #41D974)
        if ("Track Section Details".equals(sheetName) && 
            (fieldName.equals("chDpId") || fieldName.equals("chDpName") || 
             fieldName.equals("chSlctTimeout"))) {
            return getColoredTextStyle(rowIndex, "green");
        }
        
        // Check for Track Section Details - Inverse Counting Head fields (yellow text #FFFF00)
        if ("Track Section Details".equals(sheetName) && 
            (fieldName.equals("iChDpId") || fieldName.equals("iChDpName") || 
             fieldName.equals("iChSlctTimeout"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Check for CHCDetail columns - tsName1 to fmaDtl1 (green text #41D974)
        if ("CHC Details".equals(sheetName) && 
            (fieldName.equals("tsName1") || fieldName.equals("timeout1") || 
             fieldName.equals("dpId1") || fieldName.equals("dpName1") || 
             fieldName.equals("fmaDtl1"))) {
            return getColoredTextStyle(rowIndex, "green");
        }
        
        // Check for CHCDetail columns - tsName2 to fmaDtl2 (yellow text #FFFF00)
        if ("CHC Details".equals(sheetName) && 
            (fieldName.equals("tsName2") || fieldName.equals("timeout2") || 
             fieldName.equals("dpId2") || fieldName.equals("dpName2") || 
             fieldName.equals("fmaDtl2"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Check for Supervisor Details - Supervised by fields (yellow text #FFFF00)
        if ("Supervisor Details".equals(sheetName) && 
            (fieldName.equals("supByTs") || fieldName.equals("supByTsDpId") || 
             fieldName.equals("supByTsDpName") || fieldName.equals("supByTsFma") ||
             fieldName.equals("timeOut") || fieldName.equals("logicType"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Check for Ethernet Details - "Own IP Address" group fields (yellow text #FFFF00)
        if ("Ethernet Details".equals(sheetName) && 
            (fieldName.equals("ipNw1") || fieldName.equals("subnetMask1") || 
             fieldName.equals("ipNw2") || fieldName.equals("subnetMask2"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Check for Ethernet Details - "Destination" group fields (green text #41D974)
        if ("Ethernet Details".equals(sheetName) && 
            (fieldName.equals("destIpNw1") || fieldName.equals("destIpNw2"))) {
            return getColoredTextStyle(rowIndex, "green");
        }
        
        // Check for IOEXB Behaviour Details - BEHAV_INPUT and TYPE_IN columns and BEHAV_IOEXB and TYPE_IOEXB (yellow text #FFFF00)
        if ("IOEXB Behaviour Details".equals(sheetName) && 
            (fieldName.equals("behavInput1") || fieldName.equals("typeIn1") ||
             fieldName.equals("behavInput2") || fieldName.equals("typeIn2") ||
             fieldName.equals("behavInput3") || fieldName.equals("typeIn3") ||
             fieldName.equals("behavIoexb") || fieldName.equals("typeIoexb"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Check for IOEXB Behaviour Details - CO-OP Reset Applied?, Reset Type, Control Type, Reset Timeout (white text #FFFFFF)
        if ("IOEXB Behaviour Details".equals(sheetName) && 
            (fieldName.equals("isCoopReset") || fieldName.equals("coopResetType") ||
             fieldName.equals("coopControlType") || fieldName.equals("resetTimeout"))) {
            return getColoredTextStyle(rowIndex, "white");
        }
        
        // Check for Data Transmission Details - Data Safety Level fields (green text #41D974)
        if ("Data Transmission Details".equals(sheetName) && 
            (fieldName.equals("safetyLevelIn") || fieldName.equals("safetyLevelOut") || 
             fieldName.equals("safeOutFdbckQuad"))) {
            return getColoredTextStyle(rowIndex, "green");
        }
        
        // Check for Data Transmission Details - Output data transmission fields (yellow text #FFFF00)
        if ("Data Transmission Details".equals(sheetName) && 
            (fieldName.equals("sourceDpId") || fieldName.equals("sourceDpName") || 
             fieldName.equals("timeout") || fieldName.equals("nmbrOut") ||
             fieldName.equals("position"))) {
            return getColoredTextStyle(rowIndex, "yellow");
        }
        
        // Default style for all other columns
        return getDataRowStyle(rowIndex);
    }
}
