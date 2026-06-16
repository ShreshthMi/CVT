package com.frauscher.ConfigurationValidationService.service.pdq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.pdq.DataSafetyLevel;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataTransmission;
import com.frauscher.ConfigurationValidationService.dto.pdq.OutputDataTransmission;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Parses the Data Transmission Inputs sheet into {@link DataTransmission} (design §6.6) — two
 * side-by-side sub-tables, each located by its column header labels (from the workbook contract).
 * Numeric fields are range-validated and emitted as strings. Returns {@code null} when the sheet is
 * absent, or carries headers only with no data rows (the project has no DT — a valid state).
 */
@Component
@RequiredArgsConstructor
public class DataTransmissionParser {

    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    public DataTransmission parse(Sheet sheet) {
        if (sheet == null) {
            return null;
        }
        Map<String, String> safetyLabels = contract.dtDataSafetyLevelColumns();
        Map<String, String> outputLabels = contract.dtOutputDataTransmissionColumns();

        Row header = locateHeaderRow(sheet, safetyLabels.get("dpName"));
        Map<String, Integer> safetyCols = resolveColumns(header, safetyLabels);
        Map<String, Integer> outputCols = resolveColumns(header, outputLabels);

        List<DataSafetyLevel> levels = parseSafetyLevels(sheet, header.getRowNum() + 1, safetyCols);
        List<OutputDataTransmission> outputs = parseOutputs(sheet, header.getRowNum() + 1, outputCols);

        if (levels.isEmpty() && outputs.isEmpty()) {
            return null; // headers only -> no DT for this project (valid)
        }
        return new DataTransmission(levels, outputs);
    }

    private List<DataSafetyLevel> parseSafetyLevels(Sheet sheet, int dataStart, Map<String, Integer> c) {
        List<DataSafetyLevel> out = new ArrayList<>();
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String dpName = cellText(row, c.get("dpName"));
            if (dpName.isEmpty() || dpName.startsWith("*")) {
                continue; // empty row or a footer note (e.g. "*Note : ...")
            }
            out.add(new DataSafetyLevel(
                    dpName,
                    intInRange(cellText(row, c.get("safetyLevelIn")), 0, 3, "SAFETY_LEVEL_IN"),
                    intInRange(cellText(row, c.get("safetyLevelOut")), 0, 3, "SAFETY_LEVEL_OUT"),
                    intInRange(cellText(row, c.get("safeOutFdbckQuad")), 0, 1, "SAFE_OUT_FDBCK_QUAD")));
        }
        return out;
    }

    private List<OutputDataTransmission> parseOutputs(Sheet sheet, int dataStart, Map<String, Integer> c) {
        List<OutputDataTransmission> out = new ArrayList<>();
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String sourceDpName = cellText(row, c.get("sourceDpName"));
            if (sourceDpName.isEmpty() || sourceDpName.startsWith("*")) {
                continue; // empty row or a footer note
            }
            out.add(new OutputDataTransmission(
                    sourceDpName,
                    intInRange(cellText(row, c.get("nmbrOut")), 0, 15, "NMBR_OUT"),
                    intInRange(cellText(row, c.get("position")), 0, 31, "POSITION")));
        }
        return out;
    }

    private Row locateHeaderRow(Sheet sheet, String dpNameLabel) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (dpNameLabel.equalsIgnoreCase(dataFormatter.formatCellValue(cell).strip())) {
                    return row;
                }
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "Data Transmission sheet has no '" + dpNameLabel + "' header row");
    }

    private Map<String, Integer> resolveColumns(Row header, Map<String, String> fieldToLabel) {
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fieldToLabel.entrySet()) {
            cols.put(entry.getKey(), findColumn(header, entry.getValue()));
        }
        return cols;
    }

    private int findColumn(Row header, String label) {
        for (Cell cell : header) {
            if (label.equalsIgnoreCase(dataFormatter.formatCellValue(cell).strip())) {
                return cell.getColumnIndex();
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "Data Transmission sheet has no '" + label + "' column header");
    }

    private String intInRange(String value, int min, int max, String field) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new PdqInvalidException(PdqInvalidReason.NON_NUMERIC_VALUE, field + " is not numeric: " + value);
        }
        if (parsed < min || parsed > max) {
            throw new PdqInvalidException(PdqInvalidReason.RANGE_INVALID,
                    field + " out of range [" + min + ".." + max + "]: " + value);
        }
        return Integer.toString(parsed);
    }

    private String cellText(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        return (cell == null) ? "" : dataFormatter.formatCellValue(cell).strip();
    }
}