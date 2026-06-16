package com.frauscher.ConfigurationValidationService.service.pdq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * — Track Sections (cols A–H) and the DP table (cols J–M), separated by the empty boundary col I.
 * The header row is located by the "Track Section Name" label; column positions come from the
 * {@code pdq-workbook.properties} contract. Each sub-table is read independently (they can differ in
 * length) until its serial-number column runs out.
 */
@Component
@RequiredArgsConstructor
public class ControlTableParser {

    private static final int MAX_FADC_OPERANDS = 8;

    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    public ControlTable parse(Sheet sheet) {
        int headerRow = locateHeaderRow(sheet);
        Map<String, Integer> ts = contract.controlTableTrackSectionColumns();
        Map<String, Integer> dp = contract.controlTableDpTableColumns();
        int dataStart = findDataStart(sheet, headerRow, ts.get("serialNo"));

        return new ControlTable(
                parseTrackSections(sheet, dataStart, ts),
                parseDpTable(sheet, dataStart, dp));
    }

    private List<TrackSection> parseTrackSections(Sheet sheet, int dataStart, Map<String, Integer> c) {
        List<TrackSection> out = new ArrayList<>();
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String serial = cellText(row, c.get("serialNo"));
            if (!isInteger(serial)) {
                break;
            }
            out.add(new TrackSection(
                    serial,
                    cellText(row, c.get("name")),
                    splitComma(cellText(row, c.get("dpIn"))),
                    splitComma(cellText(row, c.get("dpOut"))),
                    cellText(row, c.get("resetType")),
                    trackType(cellText(row, c.get("trackOutput"))),
                    fadcAutoReset(cellText(row, c.get("fadcAutoReset")), cellText(row, c.get("logicType"))),
                    yesNo(cellText(row, c.get("autoResetByTimer")))));
        }
        return out;
    }

    private List<DpTableRow> parseDpTable(Sheet sheet, int dataStart, Map<String, Integer> c) {
        List<DpTableRow> out = new ArrayList<>();
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String serial = cellText(row, c.get("serialNo"));
            if (!isInteger(serial)) {
                break;
            }
            out.add(new DpTableRow(
                    serial,
                    cellText(row, c.get("name")),
                    position(cellText(row, c.get("position"))),
                    yesNo(cellText(row, c.get("eChc")))));
        }
        return out;
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

    private int findDataStart(Sheet sheet, int headerRow, int serialCol) {
        for (Row row : sheet) {
            if (row.getRowNum() > headerRow && isInteger(cellText(row, serialCol))) {
                return row.getRowNum();
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING, "Control table has no data rows");
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