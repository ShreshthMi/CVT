package com.frauscher.ConfigurationValidationService.service.pdq;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Reads the PDQ sheet's header values into a {@link PdqHeader}:
 * <ul>
 *   <li><b>projectCode</b> — anchored on the "Project Code" label, read from the cell to its right
 *       (merges resolved); blank or absent -&gt; {@code "0"}.</li>
 *   <li><b>System Redundancy</b> (Sl. No. 1.08) — {@code Single -> "true"}, {@code Dual -> "false"} (BLOCK_EXISTS).</li>
 *   <li><b>AEB Equipment Version</b> (Sl. No. 1.09) — the version string + the GS06+ flag.</li>
 * </ul>
 * Rows/columns are located by content (the contract labels), never fixed positions.
 */
@Component
@RequiredArgsConstructor
public class PdqSheetHeaderParser {

    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    public PdqHeader parse(Sheet sheet) {
        String projectCode = readProjectCode(sheet);

        StructuredColumns cols = locateStructuredColumns(sheet);
        String redundancy = readBySlNo(sheet, cols, contract.systemRedundancySlNo());
        String version = readBySlNo(sheet, cols, contract.aebVersionSlNo());

        boolean gs06Plus = version.toUpperCase().contains("GS06");
        return new PdqHeader(projectCode, version, gs06Plus, toBlockExists(redundancy));
    }

    private String readProjectCode(Sheet sheet) {
        String label = contract.projectCodeLabel().toLowerCase();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (dataFormatter.formatCellValue(cell).strip().toLowerCase().startsWith(label)) {
                    String value = readResolved(sheet, row.getRowNum(), cell.getColumnIndex() + 1);
                    return value.isEmpty() ? "0" : value;
                }
            }
        }
        return "0"; // label not found -> treat as blank
    }

    private StructuredColumns locateStructuredColumns(Sheet sheet) {
        String slNoHeader = contract.pdqSlNoHeader();
        String responseHeader = contract.pdqResponseHeader();
        for (Row row : sheet) {
            Integer slNoCol = null;
            Integer responseCol = null;
            for (Cell cell : row) {
                String v = dataFormatter.formatCellValue(cell).strip();
                if (slNoHeader.equalsIgnoreCase(v)) {
                    slNoCol = cell.getColumnIndex();
                } else if (responseHeader.equalsIgnoreCase(v)) {
                    responseCol = cell.getColumnIndex();
                }
            }
            if (slNoCol != null && responseCol != null) {
                return new StructuredColumns(slNoCol, responseCol);
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "PDQ sheet has no '" + slNoHeader + "'/'" + responseHeader + "' header row");
    }

    private String readBySlNo(Sheet sheet, StructuredColumns cols, String slNo) {
        for (Row row : sheet) {
            Cell key = row.getCell(cols.slNoCol());
            if (key != null && dataFormatter.formatCellValue(key).strip().equals(slNo)) {
                return readResolved(sheet, row.getRowNum(), cols.responseCol());
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "PDQ sheet has no Sl. No. '" + slNo + "' row");
    }

    private String toBlockExists(String redundancy) {
        if ("Single".equalsIgnoreCase(redundancy)) {
            return "true";
        }
        if ("Dual".equalsIgnoreCase(redundancy)) {
            return "false";
        }
        throw new PdqInvalidException(PdqInvalidReason.MAPPING_LOOKUP_FAILED,
                "Unexpected System Redundancy value (expected Single/Dual): '" + redundancy + "'");
    }

    /** Reads a cell, following a merged region to its top-left if the target is merged. */
    private String readResolved(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                rowIdx = region.getFirstRow();
                colIdx = region.getFirstColumn();
                break;
            }
        }
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(colIdx);
        return (cell == null) ? "" : dataFormatter.formatCellValue(cell).strip();
    }

    private record StructuredColumns(int slNoCol, int responseCol) {
    }
}