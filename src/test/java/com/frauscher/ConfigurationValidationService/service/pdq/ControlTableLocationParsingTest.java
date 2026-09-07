package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * Location-banner parsing for the Control table. A workbook now covers a whole project, so a
 * {@code LOCATION - <code>} banner row tags every track/DP row beneath it until the next banner, and two
 * locations may carry identical track and DP names.
 */
class ControlTableLocationParsingTest {

    private static final String SHEET = "Control table";

    private ControlTableParser parser;

    @BeforeEach
    void setUp() {
        parser = new ControlTableParser(new PdqWorkbookContract());
    }

    @Test
    void tagsEveryRowWithTheLocationBannerAboveIt() throws Exception {
        ControlTable ct = parseTwoLocation();

        assertEquals(14, ct.trackSections().size(), "7 track sections per location");
        assertEquals(16, ct.dpTable().size(), "8 DP rows per location");

        assertEquals(List.of("STN1", "STN1", "STN1", "STN1", "STN1", "STN1", "STN1",
                        "STN2", "STN2", "STN2", "STN2", "STN2", "STN2", "STN2"),
                ct.trackSections().stream().map(TrackSection::location).toList());
        assertEquals(8, ct.dpTable().stream().filter(d -> "STN1".equals(d.location())).count());
        assertEquals(8, ct.dpTable().stream().filter(d -> "STN2".equals(d.location())).count());
    }

    /**
     * The regression this fixture exists for. The track sub-table is 7 rows per location and the DP
     * sub-table 8, so with the single blank spacer between locations, the track columns are blank on two
     * consecutive rows while the table itself is still going. Terminating on that would drop STN2 in
     * silence — a wrong answer, not an error — so the terminator spans both sub-tables' columns.
     */
    @Test
    void readsTheSecondLocationThoughItsOwnColumnsRunDryFirst() throws Exception {
        ControlTable ct = parseTwoLocation();

        List<String> stn2 = ct.trackSections().stream()
                .filter(t -> "STN2".equals(t.location()))
                .map(TrackSection::name)
                .toList();

        assertEquals(List.of("1AXT1", "2AXT1", "201AXT", "SUP1-AXT1", "1AXT2", "2AXT2", "SUP1-AXT2"), stn2);
    }

    /** The point of the tag: the same name at both locations is two distinct rows, not a duplicate. */
    @Test
    void keepsIdenticallyNamedTracksAtDifferentLocationsApart() throws Exception {
        ControlTable ct = parseTwoLocation();

        List<TrackSection> named1AXT1 = ct.trackSections().stream()
                .filter(t -> "1AXT1".equals(t.name()))
                .toList();

        assertEquals(2, named1AXT1.size());
        assertEquals(Set.of("STN1", "STN2"),
                named1AXT1.stream().map(TrackSection::location).collect(Collectors.toSet()));

        List<DpTableRow> namedDp1A = ct.dpTable().stream()
                .filter(d -> "DP1A".equals(d.name()))
                .toList();
        assertEquals(2, namedDp1A.size());
        assertEquals(Set.of("STN1", "STN2"),
                namedDp1A.stream().map(DpTableRow::location).collect(Collectors.toSet()));
    }

    /** S.No restarts per location, so it is no longer unique across the sheet. */
    @Test
    void serialNumbersRestartWithinEachLocation() throws Exception {
        ControlTable ct = parseTwoLocation();

        assertEquals("1", ct.trackSections().get(0).serialNo());
        assertEquals("1", ct.trackSections().get(7).serialNo());
        assertEquals("STN2", ct.trackSections().get(7).location());
    }

    /** A duplicate name inside ONE location is still a duplicate — the parser records both rows. */
    @Test
    void keepsBothRowsWhenANameRepeatsWithinOneLocation() throws Exception {
        ControlTable ct;
        try (Workbook wb = PdqFixtures.loadTwoLocationDupWorkbook()) {
            ct = parser.parse(wb.getSheet(SHEET));
        }

        assertEquals(8, ct.trackSections().stream().filter(t -> "STN1".equals(t.location())).count());
        assertEquals(2, ct.trackSections().stream()
                .filter(t -> "STN1".equals(t.location()) && "1AXT1".equals(t.name()))
                .count(), "the gate, not the parser, is what rejects this");
    }

    /** A workbook with no banners is unchanged: every location null. */
    @Test
    void leavesLocationNullWhenTheSheetCarriesNoBanners() throws Exception {
        ControlTable ct;
        try (Workbook wb = PdqFixtures.loadWorkbook()) {
            ct = parser.parse(wb.getSheet(SHEET));
        }

        assertEquals(7, ct.trackSections().size());
        assertEquals(8, ct.dpTable().size());
        assertTrue(ct.trackSections().stream().allMatch(t -> t.location() == null));
        assertTrue(ct.dpTable().stream().allMatch(d -> d.location() == null));
        assertNull(ct.trackSections().get(0).location());
    }

    /**
     * A cell opening with the banner prefix but missing its separator is rejected rather than read as a
     * track name — an unrecognised banner would end the sub-table and drop every location below it.
     */
    @Test
    void rejectsABannerWithNoSeparator() throws Exception {
        assertEquals(PdqInvalidReason.MALFORMED_LOCATION_BANNER,
                reasonAfterRewritingFirstBanner("LOCATION STN1"));
    }

    @Test
    void rejectsABannerNamingNoStationCode() throws Exception {
        assertEquals(PdqInvalidReason.MALFORMED_LOCATION_BANNER,
                reasonAfterRewritingFirstBanner("LOCATION -"));
    }

    @Test
    void rejectsTheSameLocationBanneredTwice() throws Exception {
        assertEquals(PdqInvalidReason.DUPLICATE_LOCATION,
                reasonAfterRewritingBanner("LOCATION - STN2", "LOCATION - STN1"));
    }

    /** A name merely starting with the prefix is data, not a banner. */
    @Test
    void treatsALongerWordOpeningWithThePrefixAsATrackName() throws Exception {
        ControlTable ct;
        try (Workbook wb = PdqFixtures.loadTwoLocationWorkbook()) {
            Sheet sheet = wb.getSheet(SHEET);
            rewriteCell(sheet, "1AXT1", "LOCATIONAL-1");
            ct = parser.parse(sheet);
        }

        assertTrue(ct.trackSections().stream().anyMatch(t -> "LOCATIONAL-1".equals(t.name())),
                "LOCATIONAL-1 is a track section, not a LOCATION banner");
        assertEquals(14, ct.trackSections().size());
    }

    /**
     * The banner has to appear in BOTH name columns. Written into only one, it tags that sub-table and
     * leaves the other's rows untagged — and an untagged row is not an error downstream, it is a row that
     * answers <i>any</i> location. Both locations' DP rows would then overwrite each other and hand
     * whichever survived to both, carrying POSITION, and so DIR_INV, with it.
     */
    @Test
    void rejectsABannerWrittenIntoOnlyOneSubTable() throws Exception {
        try (Workbook wb = PdqFixtures.loadTwoLocationWorkbook()) {
            Sheet sheet = wb.getSheet(SHEET);
            blankDpTableCopyOf(sheet, "LOCATION - STN2");

            PdqInvalidException thrown = assertThrows(PdqInvalidException.class, () -> parser.parse(sheet));

            assertEquals(PdqInvalidReason.LOCATION_BANNERS_INCONSISTENT, thrown.getReason());
        }
    }

    /** Blanks the SECOND cell holding {@code text} — the DP sub-table's copy of the banner. */
    private void blankDpTableCopyOf(Sheet sheet, String text) {
        DataFormatter formatter = new DataFormatter();
        boolean seenFirst = false;
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (text.equals(formatter.formatCellValue(cell).strip())) {
                    if (seenFirst) {
                        cell.setBlank();
                        return;
                    }
                    seenFirst = true;
                }
            }
        }
        throw new AssertionError("fixture has fewer than two cells reading " + text);
    }

    private ControlTable parseTwoLocation() throws Exception {
        try (Workbook wb = PdqFixtures.loadTwoLocationWorkbook()) {
            return parser.parse(wb.getSheet(SHEET));
        }
    }

    private PdqInvalidReason reasonAfterRewritingFirstBanner(String replacement) throws Exception {
        return reasonAfterRewritingBanner("LOCATION - STN1", replacement);
    }

    private PdqInvalidReason reasonAfterRewritingBanner(String from, String to) throws Exception {
        try (Workbook wb = PdqFixtures.loadTwoLocationWorkbook()) {
            Sheet sheet = wb.getSheet(SHEET);
            rewriteCell(sheet, from, to);
            PdqInvalidException thrown = assertThrows(PdqInvalidException.class, () -> parser.parse(sheet));
            return thrown.getReason();
        }
    }

    /** Rewrites the first cell holding {@code from} — located by text, so a re-layout does not break it. */
    private void rewriteCell(Sheet sheet, String from, String to) {
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (from.equals(formatter.formatCellValue(cell).strip())) {
                    cell.setCellValue(to);
                    return;
                }
            }
        }
        throw new AssertionError("fixture has no cell reading " + from);
    }
}
