package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * Parses the Control table sheet of the real fixture and asserts both sub-tables against the
 * design §6.5 rules (PHYSICAL→MAIN/VIRTUAL→COMBINATION, fadc logic tree, comma DPs, YES/NO, POSITION).
 */
class ControlTableParserTest {

    private ControlTableParser parser;

    @BeforeEach
    void setUp() {
        parser = new ControlTableParser(new PdqWorkbookContract());
    }

    /**
     * Reset Type is the sole source of the derived {@code CFG_SECTION.RESET_OUT}, so a row that names a
     * track section must carry one — a blank there makes the control table incomplete, not merely sparse.
     */
    @Test
    void rejectsANamedTrackSectionWithNoResetType() throws Exception {
        try (Workbook wb = PdqFixtures.loadWorkbook()) {
            Sheet sheet = wb.getSheet("Control table");
            blankResetTypeOfFirstTrackSection(sheet);

            PdqInvalidException thrown = assertThrows(PdqInvalidException.class, () -> parser.parse(sheet));

            assertEquals(PdqInvalidReason.MISSING_RESET_TYPE, thrown.getReason());
        }
    }

    /** Column E of the row naming 1AXT1 — located by text so it survives a fixture re-layout. */
    private void blankResetTypeOfFirstTrackSection(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if ("1AXT1".equals(formatter.formatCellValue(cell).strip())) {
                    row.getCell(cell.getColumnIndex() + 3).setBlank(); // B name -> E resetType
                    return;
                }
            }
        }
        throw new AssertionError("fixture has no 1AXT1 track section");
    }

    @Test
    void parsesFixtureControlTable() throws Exception {
        ControlTable ct;
        try (Workbook wb = PdqFixtures.loadWorkbook()) {
            ct = parser.parse(wb.getSheet("Control table"));
        }

        assertEquals(7, ct.trackSections().size());
        assertEquals(8, ct.dpTable().size());

        // First track section: PHYSICAL -> MAIN, OR logic tree, NO -> false.
        TrackSection t0 = ct.trackSections().get(0);
        assertEquals("1", t0.serialNo());
        assertEquals("1AXT1", t0.name());
        assertEquals(List.of("DP1A"), t0.dpIn());
        assertEquals(List.of("DP2A"), t0.dpOut());
        assertEquals("RESTRICTED RESET WITH LV", t0.resetType());
        assertEquals("MAIN", t0.trackType());
        assertEquals("OR", t0.fadcAutoReset().op());
        assertEquals(List.of("1AXT2", "SUP1-AXT1"), t0.fadcAutoReset().operands());
        assertFalse(t0.autoResetByTimer());

        // 201AXT: comma-split dpOut, NA -> null fadc, YES -> true.
        TrackSection t2 = ct.trackSections().get(2);
        assertEquals(List.of("DP4", "DP5"), t2.dpOut());
        assertNull(t2.fadcAutoReset());
        assertTrue(t2.autoResetByTimer());

        // SUP1-AXT1: VIRTUAL -> COMBINATION, AND logic tree.
        TrackSection t3 = ct.trackSections().get(3);
        assertEquals("COMBINATION", t3.trackType());
        assertEquals("AND", t3.fadcAutoReset().op());
        assertEquals(List.of("1AXT1", "2AXT1"), t3.fadcAutoReset().operands());

        // DP table: positions + E-CHC booleans.
        DpTableRow dp0 = ct.dpTable().get(0);
        assertEquals("DP1A", dp0.name());
        assertEquals("ABOVE THE RAIL", dp0.position());
        assertTrue(dp0.eChc());

        DpTableRow dp5 = ct.dpTable().get(5);
        assertEquals("DP1B", dp5.name());
        assertEquals("BELOW THE RAIL", dp5.position());
        assertTrue(dp5.eChc());
    }
}