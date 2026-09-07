package com.frauscher.ConfigurationValidationService.service.pdq;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.FadcAutoReset;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Parses the Control table sheet into {@link ControlTable} (design §6.5): two side-by-side sub-tables
 * — Track Sections (cols A–I) and the DP table (cols K–N), separated by the empty boundary col J.
 * The header row is located by the "Track Section Name" label; column positions come from the
 * {@code pdq-workbook.properties} contract. Each sub-table is read independently (they can differ in
 * length).
 *
 * <p><b>Location banners.</b> One workbook now covers a whole project, which may span several
 * stations, and two stations may hold track sections (and DPs) with identical names. A row whose name
 * cell reads {@code LOCATION - <code>} is a banner: it carries no S.No and tags every following row of
 * that sub-table with that station code, until the next banner. A row is classified in this
 * precedence:</p>
 * <ol>
 *   <li><b>blank</b> across this sub-table's own columns — contributes nothing here. It ends the
 *       sub-table only after two consecutive rows blank across the <i>whole</i> table (both sub-tables'
 *       columns); a row blank here but carrying the other sub-table's data or banner is stepped over.
 *       Both halves of that matter: the sub-tables differ in length, so this one runs dry several rows
 *       before the table does — counting those as terminator rows would stop the walk before the next
 *       banner and silently drop every location below it — while measuring blankness across the whole
 *       row instead would make the other sub-table's banner look like a footer and stop the walk just
 *       as dead. One blank row is tolerated as the spacer between two locations;</li>
 *   <li><b>banner</b> — sets the location in force;</li>
 *   <li><b>data</b> — an integer S.No, tagged with the location in force;</li>
 *   <li><b>otherwise</b> — before any data it is skipped (the IN/OUT sub-header); after, it ends the
 *       sub-table (a note under the table).</li>
 * </ol>
 *
 * <p>A workbook carrying no banners parses exactly as it did before, every location null.</p>
 */
@Component
@RequiredArgsConstructor
public class ControlTableParser {

    private static final int MAX_FADC_OPERANDS = 8;
    /** Consecutive blank rows that end a sub-table. One is tolerated as a between-locations spacer. */
    private static final int BLANK_RUN_ENDS_TABLE = 2;

    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    public ControlTable parse(Sheet sheet) {
        int headerRow = locateHeaderRow(sheet);
        Map<String, Integer> ts = contract.controlTableTrackSectionColumns();
        Map<String, Integer> dp = contract.controlTableDpTableColumns();
        String prefix = contract.controlTableLocationPrefix().strip();
        String separator = contract.controlTableLocationSeparator().strip();
        // Every column the table occupies, across BOTH sub-tables — the span a fully blank row must clear.
        List<Integer> tableColumns = new ArrayList<>(ts.values());
        tableColumns.addAll(dp.values());

        Set<String> trackBanners = new LinkedHashSet<>();
        Set<String> dpBanners = new LinkedHashSet<>();

        List<TrackSection> trackSections = scan(sheet, headerRow, ts, tableColumns, prefix, separator,
                "track sections", trackBanners, (row, location) -> trackSection(row, ts, location));
        if (trackSections.isEmpty()) {
            throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING, "Control table has no data rows");
        }
        List<DpTableRow> dpTable = scan(sheet, headerRow, dp, tableColumns, prefix, separator,
                "DP table", dpBanners, (row, location) -> new DpTableRow(
                        cellText(row, dp.get("serialNo")),
                        cellText(row, dp.get("name")),
                        position(cellText(row, dp.get("position"))),
                        yesNo(cellText(row, dp.get("eChc"))),
                        location));

        requireMatchingBanners(trackBanners, dpBanners);
        return new ControlTable(trackSections, dpTable);
    }

    /**
     * Walks one sub-table from just under the header, classifying each row and handing the data ones to
     * {@code mapper} together with the location in force. Both sub-tables share this walk; they differ
     * only in their column map and their row shape.
     */
    private <T> List<T> scan(
            Sheet sheet,
            int headerRow,
            Map<String, Integer> columns,
            List<Integer> tableColumns,
            String prefix,
            String separator,
            String what,
            Set<String> banneredOut,
            BiFunction<Row, String, T> mapper) {

        List<Integer> ownColumns = new ArrayList<>(columns.values());
        int serialColumn = columns.get("serialNo");
        int nameColumn = columns.get("name");

        List<T> out = new ArrayList<>();
        Set<String> bannered = new LinkedHashSet<>();
        String location = null;
        boolean started = false;
        boolean rowsAboveFirstBanner = false;
        int blankRun = 0;

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);

            if (isBlankAcross(row, ownColumns)) {
                // Blank here, but the row may still carry the OTHER sub-table's data or its banner — the
                // two differ in length, so this sub-table runs dry well before the table does. Only a row
                // blank across the whole table counts toward the terminator; the rest are stepped over so
                // the walk still reaches the next banner below them.
                if (isBlankAcross(row, tableColumns)) {
                    if (started && ++blankRun >= BLANK_RUN_ENDS_TABLE) {
                        break;
                    }
                } else {
                    blankRun = 0;
                }
                continue;
            }
            blankRun = 0;

            String code = locationCode(cellText(row, nameColumn), prefix, separator);
            if (code != null) {
                if (!bannered.add(code.toUpperCase())) {
                    throw new PdqInvalidException(PdqInvalidReason.DUPLICATE_LOCATION,
                            "Control table " + what + " banners location " + code + " more than once");
                }
                location = code;
                started = true;
                continue;
            }

            if (!isInteger(cellText(row, serialColumn))) {
                if (!started) {
                    continue; // sub-header between the header and the first data row (the IN/OUT row)
                }
                break; // a note or footer sitting directly under the table
            }
            started = true;
            if (location == null) {
                rowsAboveFirstBanner = true;
            }
            out.add(mapper.apply(row, location));
        }

        banneredOut.addAll(bannered);
        if (!bannered.isEmpty() && rowsAboveFirstBanner) {
            throw new PdqInvalidException(PdqInvalidReason.UNTAGGED_ROW_IN_TAGGED_TABLE,
                    "Control table " + what + " carries LOCATION banners but has rows above the first one");
        }
        return out;
    }

    /**
     * Both sub-tables must be bannered alike. Each is scanned independently off its own name column, so a
     * banner written into only one of them tags that one and leaves the other's rows untagged — and an
     * untagged row is not an error anywhere downstream, it is a row that matches <i>any</i> location. Two
     * locations' DP rows would then overwrite each other and hand whichever survived to both locations,
     * taking POSITION (and so {@code DIR_INV}) with it. Nothing else would notice.
     */
    private void requireMatchingBanners(Set<String> trackBanners, Set<String> dpBanners) {
        Set<String> tracks = upperCased(trackBanners);
        Set<String> dps = upperCased(dpBanners);
        if (tracks.equals(dps)) {
            return;
        }
        throw new PdqInvalidException(PdqInvalidReason.LOCATION_BANNERS_INCONSISTENT,
                "Control table sub-tables carry different LOCATION banners: track sections " + tracks
                        + " but DP table " + dps + " — each location must be bannered in both");
    }

    private Set<String> upperCased(Set<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            out.add(value.toUpperCase());
        }
        return out;
    }

    private TrackSection trackSection(Row row, Map<String, Integer> c, String location) {
        String name = cellText(row, c.get("name"));
        return new TrackSection(
                cellText(row, c.get("serialNo")),
                name,
                splitComma(cellText(row, c.get("dpIn"))),
                splitComma(cellText(row, c.get("dpOut"))),
                resetType(name, cellText(row, c.get("resetType"))),
                trackType(cellText(row, c.get("trackOutput"))),
                fadcAutoReset(cellText(row, c.get("fadcAutoReset")), cellText(row, c.get("logicType"))),
                yesNo(cellText(row, c.get("autoResetByTimer"))),
                location);
    }

    /**
     * The station code of a {@code LOCATION - <code>} banner cell, or null when the cell holds an
     * ordinary name. The prefix must match as a whole word, so a track section legitimately called
     * {@code LOCATIONAL-1} stays data.
     *
     * <p>A cell that opens with the prefix but is not well-formed is <b>rejected</b> rather than read as
     * data: an unrecognised banner would fall through to the "ends the sub-table" branch and silently
     * drop every location below it.</p>
     */
    private String locationCode(String nameCell, String prefix, String separator) {
        String value = nameCell.strip();
        if (value.length() < prefix.length() || !value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String rest = value.substring(prefix.length());
        if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) {
            return null; // the prefix is only the opening of a longer word — an ordinary name
        }
        rest = rest.strip();
        if (!rest.startsWith(separator)) {
            throw new PdqInvalidException(PdqInvalidReason.MALFORMED_LOCATION_BANNER,
                    "Control table location banner " + value + " has no " + separator + " separator");
        }
        String code = rest.substring(separator.length()).strip();
        if (code.isEmpty()) {
            throw new PdqInvalidException(PdqInvalidReason.MALFORMED_LOCATION_BANNER,
                    "Control table location banner " + value + " names no station code");
        }
        return code;
    }

    private boolean isBlankAcross(Row row, List<Integer> columns) {
        if (row == null) {
            return true;
        }
        for (int column : columns) {
            if (!cellText(row, column).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reset Type is passed through verbatim (input-artefacts-pdq-workbook.wiki, no catalog enforced), but a
     * row that names a track section must carry one: it is the sole source of the derived
     * {@code CFG_SECTION.RESET_OUT}, so a blank leaves that AEB's check with nothing to validate against.
     * A blank on an unnamed row is just an empty row and is left alone.
     */
    private String resetType(String trackSectionName, String value) {
        if (!trackSectionName.isBlank() && value.isBlank()) {
            throw new PdqInvalidException(PdqInvalidReason.MISSING_RESET_TYPE,
                    "Control table track section '" + trackSectionName + "' has no Reset Type");
        }
        return value;
    }

    private String trackType(String value) {
        if ("PHYSICAL".equalsIgnoreCase(value)) {
            return "MAIN";
        }
        if ("VIRTUAL".equalsIgnoreCase(value)) {
            return "COMBINATION";
        }
        throw new PdqInvalidException(PdqInvalidReason.UNSUPPORTED_TRACK_OUTPUT,
                "Track Output must be PHYSICAL/VIRTUAL: " + value);
    }

    private String position(String value) {
        if ("ABOVE THE RAIL".equalsIgnoreCase(value) || "BELOW THE RAIL".equalsIgnoreCase(value)) {
            return value.toUpperCase();
        }
        throw new PdqInvalidException(PdqInvalidReason.UNSUPPORTED_POSITION,
                "DP POSITION must be ABOVE/BELOW THE RAIL: " + value);
    }

    private boolean yesNo(String value) {
        if ("YES".equalsIgnoreCase(value)) {
            return true;
        }
        if ("NO".equalsIgnoreCase(value)) {
            return false;
        }
        throw new PdqInvalidException(PdqInvalidReason.INVALID_YES_NO, "Expected YES/NO: " + value);
    }

    /**
     * Frozen PDQ Ver14: the operands live in the "FAdC - FAdC Auto reset" column (comma-separated) and
     * the operator in the separate "Logic type" column. A blank/NA operands cell -&gt; no auto-reset.
     */
    private FadcAutoReset fadcAutoReset(String operandsRaw, String operatorRaw) {
        String operandsCell = operandsRaw.strip();
        if (operandsCell.isEmpty() || operandsCell.equalsIgnoreCase("NA")) {
            return null;
        }
        List<String> operands = splitComma(operandsCell);
        if (operands.size() > MAX_FADC_OPERANDS) {
            throw new PdqInvalidException(PdqInvalidReason.RANGE_INVALID,
                    "fadcAutoReset has more than " + MAX_FADC_OPERANDS + " operands: " + operandsCell);
        }
        String operator = operatorRaw.strip();
        if (operator.isEmpty()) {
            return new FadcAutoReset(null, operands); // single operand, no operator
        }
        if (!"OR".equalsIgnoreCase(operator) && !"AND".equalsIgnoreCase(operator)) {
            throw new PdqInvalidException(PdqInvalidReason.UNSUPPORTED_LOGIC_TYPE,
                    "Logic type must be OR/AND: " + operator);
        }
        return new FadcAutoReset(operator.toUpperCase(), operands);
    }

    private int locateHeaderRow(Sheet sheet) {
        String label = contract.controlTableHeaderLabel();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (label.equalsIgnoreCase(dataFormatter.formatCellValue(cell).strip())) {
                    return row.getRowNum();
                }
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "Control table has no '" + label + "' header");
    }

    private List<String> splitComma(String value) {
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value.strip());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String cellText(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        return (cell == null) ? "" : dataFormatter.formatCellValue(cell).strip();
    }
}
