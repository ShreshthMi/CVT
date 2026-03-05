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
    private final CellStyle failRowStyle1;
    private final CellStyle failRowStyle2;
    private final CellStyle stringStyle;

    public ExcelStyleManager(Workbook wb) {
        this.workbook = wb;
        this.stringStyle = createStringStyle();
        this.headerStyle = createHeaderStyle();
        this.dataRowStyle1 = createDataStyle(new byte[]{(byte)0xF2, (byte)0xF2, (byte)0xF2}, IndexedColors.BLACK.getIndex()); // #F2F2F2 light grey + black font
        this.dataRowStyle2 = createDataStyle(new byte[]{(byte)0xE6, (byte)0xE6, (byte)0xE6}, IndexedColors.BLACK.getIndex()); // #E6E6E6 slightly darker grey + black font
        this.failRowStyle1 = createDataStyle(new byte[]{(byte)0x4D, (byte)0x4D, (byte)0x4D}, IndexedColors.WHITE.getIndex()); // #4D4D4D dark grey + white font
        this.failRowStyle2 = createDataStyle(new byte[]{(byte)0x5A, (byte)0x5A, (byte)0x5A}, IndexedColors.WHITE.getIndex()); // #5A5A5A dark grey + white font
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
     * Creates header style with light grey background, black bold text, and borders.
     */
    private CellStyle createHeaderStyle() {
        CellStyle headerStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        headerStyle.setDataFormat(format.getFormat("@"));
        headerStyle.setWrapText(true);
        headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;

            XSSFColor borderColor = new XSSFColor(new byte[]{(byte)0xC0, (byte)0xC0, (byte)0xC0}, null);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setTopBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setBottomBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setLeftBorderColor(borderColor);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)headerStyle).setRightBorderColor(borderColor);

            // Background color #D9D9D9 (light grey, slightly darker than data rows)
            XSSFColor bgColor = new XSSFColor(new byte[]{(byte)0xD9, (byte)0xD9, (byte)0xD9}, null);
            headerStyle.setFillForegroundColor(bgColor);
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // Font: Calibri, size 11, bold, black color
            XSSFFont headerFont = xssfWb.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short)11);
            headerFont.setColor(IndexedColors.BLACK.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
        }

        return headerStyle;
    }

    /**
     * Creates data row style with specified background color, borders, and black font.
     */
    private CellStyle createDataStyle(byte[] rgbColor, short fontColorIndex) {
        CellStyle dataStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        dataStyle.setDataFormat(format.getFormat("@"));
        dataStyle.setWrapText(true);
        dataStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        dataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        dataStyle.setIndention((short)1);

        if (workbook instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) workbook;

            XSSFColor borderColor = new XSSFColor(new byte[]{(byte)0xC0, (byte)0xC0, (byte)0xC0}, null);
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
            font.setColor(fontColorIndex);
            dataStyle.setFont(font);
        }

        return dataStyle;
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
            font.setBold(true);
            XSSFColor color = new XSSFColor(new byte[]{r, g, b}, null);
            font.setColor(color);
            return font;
        }
        return workbook.createFont();
    }

    // Getter methods for styles
    public CellStyle getHeaderStyle() { return headerStyle; }
    public CellStyle getDataRowStyle1() { return dataRowStyle1; }
    public CellStyle getDataRowStyle2() { return dataRowStyle2; }
    public CellStyle getGreenTextStyle1() { return dataRowStyle1; }
    public CellStyle getGreenTextStyle2() { return dataRowStyle2; }
    public CellStyle getYellowTextStyle1() { return dataRowStyle1; }
    public CellStyle getYellowTextStyle2() { return dataRowStyle2; }
    public CellStyle getFailRowStyle1() { return failRowStyle1; }
    public CellStyle getFailRowStyle2() { return failRowStyle2; }
    public CellStyle getStringStyle() { return stringStyle; }

    /**
     * Gets data row style based on row index for alternating colors.
     */
    public CellStyle getDataRowStyle(int rowIndex) {
        return (rowIndex % 2 == 0) ? dataRowStyle1 : dataRowStyle2;
    }

    /**
     * Gets fail row style based on row index for alternating dark grey.
     */
    public CellStyle getFailRowStyle(int rowIndex) {
        return (rowIndex % 2 == 0) ? failRowStyle1 : failRowStyle2;
    }

    /**
     * Gets colored text style based on row index and color.
     * All colors now return the standard data row style (black font on light grey).
     */
    public CellStyle getColoredTextStyle(int rowIndex, String color) {
        return getDataRowStyle(rowIndex);
    }

    /**
     * Gets cell style for any column. Returns standard data row style (light grey + black font).
     */
    public CellStyle getCellStyleForColumn(String headerName, String fieldName, int rowIndex, String sheetName) {
        return getDataRowStyle(rowIndex);
    }
}