package com.frauscher.ConfigurationValidationService.service.pdq;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the project block (CFG_PROJECT_AEB / CFG_PROJECT_COM content) from the CQ-IR
 * {@code PROJECT_NUMBER} row (frozen PDQ Ver14). The PDQ sheet no longer carries System Redundancy, so
 * block existence is now driven solely by that row's Response (YES/NO) and its Remarks column:
 * <ul>
 *   <li><b>YES</b> &rarr; {@code BLOCK_EXISTS = "true"}, {@code PROJECT_NUMBER} = the Remarks value
 *       (blank Remarks &rarr; {@code "0"}). PROJECT_NUMBER is the only field read from Remarks.</li>
 *   <li><b>NO</b> &rarr; {@code BLOCK_EXISTS = "false"}; the Remarks cell must be empty, else the PDQ is
 *       rejected as invalid.</li>
 * </ul>
 * Columns are located by the contract header labels (Configuration Word / Response / Remarks), so the
 * row/column positions and the hidden state of the Configuration Word column do not matter.
 */
@Component
@RequiredArgsConstructor
public class ProjectBlockResolver {

    private static final String PROJECT_NUMBER = "PROJECT_NUMBER";

    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    /** The resolved CFG_PROJECT_* content. */
    public record ProjectBlock(String blockExists, String projectNumber) {
    }

    public ProjectBlock resolve(Sheet cqIrSheet) {
        Columns cols = locateColumns(cqIrSheet);
        for (Row row : cqIrSheet) {
            if (row.getRowNum() <= cols.headerRow()) {
                continue;
            }
            if (PROJECT_NUMBER.equalsIgnoreCase(cellText(row, cols.configWordCol()))) {
                return toProjectBlock(cellText(row, cols.responseCol()), cellText(row, cols.remarksCol()));
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.PROJECT_BLOCK_INVALID,
                "CQ-IR sheet has no PROJECT_NUMBER row");
    }

    private ProjectBlock toProjectBlock(String response, String remarks) {
        String answer = leadingToken(response);
        if ("YES".equalsIgnoreCase(answer)) {
            return new ProjectBlock("true", remarks.isEmpty() ? "0" : remarks);
        }
        if ("NO".equalsIgnoreCase(answer)) {
            if (!remarks.isEmpty()) {
                throw new PdqInvalidException(PdqInvalidReason.PROJECT_BLOCK_INVALID,
                        "PROJECT_NUMBER Response is NO but Remarks is not empty: " + remarks);
            }
            return new ProjectBlock("false", "0");
        }
        throw new PdqInvalidException(PdqInvalidReason.INVALID_YES_NO,
                "PROJECT_NUMBER Response must be YES/NO: " + response);
    }

    private Columns locateColumns(Sheet sheet) {
        String cwLabel = contract.cqirConfigWordHeader();
        String respLabel = contract.cqirResponseHeader();
        String remLabel = contract.cqirRemarksHeader();
        for (Row row : sheet) {
            Integer cw = null;
            Integer resp = null;
            Integer rem = null;
            for (Cell cell : row) {
                String v = dataFormatter.formatCellValue(cell).strip();
                if (cwLabel.equalsIgnoreCase(v)) {
                    cw = cell.getColumnIndex();
                } else if (respLabel.equalsIgnoreCase(v)) {
                    resp = cell.getColumnIndex();
                } else if (remLabel.equalsIgnoreCase(v)) {
                    rem = cell.getColumnIndex();
                }
            }
            if (cw != null && resp != null && rem != null) {
                return new Columns(row.getRowNum(), cw, resp, rem);
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "CQ-IR sheet has no row with '" + cwLabel + "', '" + respLabel + "' and '" + remLabel + "' headers");
    }

    /** Value left of the first colon (the YES/NO answer), stripped. */
    private String leadingToken(String response) {
        String value = response.strip();
        int idx = value.indexOf(':');
        return ((idx >= 0) ? value.substring(0, idx) : value).strip();
    }

    private String cellText(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        return (cell == null) ? "" : dataFormatter.formatCellValue(cell).strip();
    }

    private record Columns(int headerRow, int configWordCol, int responseCol, int remarksCol) {
    }
}