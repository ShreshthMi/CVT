package com.frauscher.ConfigurationValidationService.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.model.ValidationSummary;

/**
 * The exported workbook side of BE-07. {@link com.frauscher.ConfigurationValidationService.validation
 * .annotation.MismatchAnnotator} computed per-cell annotations correctly, but nothing under {@code util/} or
 * {@code util/excel/} ever read them: {@code applyDataStyling} styled a row only on the Validation Results
 * sheet, so no detail sheet was ever highlighted for any block — which is what UAT reported against CHC
 * Details. Separately {@code getOrderedFields} appended every declared field not named in
 * {@code @JsonPropertyOrder}, so {@code mismatches} rendered as a raw object string in a trailing column.
 */
class ExcelSummaryUtilTest {

    private static final String VALUE_FILL = "FFC7CE";
    private static final String UNEXPECTED_FILL = "DDEBF7";

    @Test
    void mismatchesNeverRenderAsAColumn() throws Exception {
        try (XSSFWorkbook wb = export(ReportDownloadRoundTripTest.annotatedSummary())) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String text = cell.toString();
                        assertFalse(text.contains("MismatchAnnotation"),
                                "raw annotation object leaked into " + sheet.getSheetName() + ": " + text);
                    }
                }
            }
        }
    }

    @Test
    void annotatedCellIsStyledByItsKind() throws Exception {
        try (XSSFWorkbook wb = export(ReportDownloadRoundTripTest.annotatedSummary())) {
            Sheet chc = wb.getSheet("CHC Details");
            assertNotNull(chc, "CHC Details sheet must exist");

            // timeout_1 carries a VALUE annotation, dp_name_2 an UNEXPECTED one; both are on the same row.
            assertEquals(VALUE_FILL, fillOf(findCell(chc, "1200")), "timeout_1 should carry the VALUE style");
            assertEquals(UNEXPECTED_FILL, fillOf(findCell(chc, "DP4B")),
                    "dp_name_2 should carry the UNEXPECTED style even though its expected is null");
        }
    }

    /** A cell with no annotation keeps the ordinary alternating-grey data style. */
    @Test
    void cleanCellIsNotHighlighted() throws Exception {
        try (XSSFWorkbook wb = export(ReportDownloadRoundTripTest.annotatedSummary())) {
            String fill = fillOf(findCell(wb.getSheet("CHC Details"), "DP2A"));
            assertTrue(fill == null || (!VALUE_FILL.equals(fill) && !UNEXPECTED_FILL.equals(fill)),
                    "an unannotated cell must not be highlighted, was " + fill);
        }
    }

    private static XSSFWorkbook export(ValidationSummary summary) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(ExcelSummaryUtil.generate(summary)));
    }

    private static Cell findCell(Sheet sheet, String text) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (text.equals(cell.toString())) {
                    return cell;
                }
            }
        }
        throw new AssertionError("no cell holding \"" + text + "\" on " + sheet.getSheetName());
    }

    /** The cell's solid fill as an RRGGBB hex string, or {@code null} when it has none. */
    private static String fillOf(Cell cell) {
        XSSFColor color = ((XSSFCellStyle) cell.getCellStyle()).getFillForegroundColorColor();
        if (color == null || color.getARGBHex() == null) {
            return null;
        }
        String argb = color.getARGBHex();
        return argb.length() == 8 ? argb.substring(2) : argb;
    }
}
