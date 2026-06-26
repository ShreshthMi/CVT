package com.frauscher.ConfigurationValidationService.service.pdq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Parses the CQ-IR sheet into the block-grouped {@code cqIrParameters} fragment — {@code IDENTIFICATION}
 * plus the {@code CFG_*} blocks. (Project blocks {@code CFG_PROJECT_AEB}/{@code CFG_PROJECT_COM} are
 * added by the caller from PDQ-sheet header data.)
 *
 * <p>Drives off the <b>Configuration Word</b> column (design §6.2): a non-empty cell makes the row
 * parametric (normalize its Response); an empty cell makes it a meta row, skipped. Hidden rows are
 * skipped (workbook visibility is honoured). Output block and key order follows the canonical design
 * §6.4 / {@code UploadPDQResponse.json} order, independent of physical sheet row order.</p>
 */
@Component
@RequiredArgsConstructor
public class CqIrSheetParser {

    private static final String IDENTIFICATION = "IDENTIFICATION";

    /** Canonical block -&gt; ordered keys (design §6.4). Excludes IDENTIFICATION and project blocks. */
    private static final Map<String, List<String>> TEMPLATE = new LinkedHashMap<>();
    static {
        TEMPLATE.put("CFG_SECTION", List.of("COMM_FAIL", "BEHAV_GE", "CLR_TRACK", "RESET_IN"));
        TEMPLATE.put("CFG_RESET", List.of("RESET_OP_TIME", "RESET_LD_TIME"));
        TEMPLATE.put("CFG_SECTION_OUT", List.of("CLR_OCC", "AUX1_OUT", "AUX2_OUT", "AUX1_NO_NC", "AUX2_NO_NC", "TYPE_AUX1", "TYPE_AUX2"));
        TEMPLATE.put("CFG_AXCNT", List.of("BEHAV_INPUT1", "BEHAV_INPUT2", "BEHAV_IOEXB", "TYPE_IN1", "TYPE_IN2", "TYPE_IN3"));
        TEMPLATE.put("CFG_OCC", List.of("OCC_EXT", "OCC_DELAY"));
        TEMPLATE.put("CFG_ZP", List.of("INTERVAL", "SUPERVIS_COUNT", "SYSTEM_COUNT", "PARTIAL_COUNT", "SUPERVIS_COUNT_LMT"));
        TEMPLATE.put("CFG_BEHAV_TGGL", List.of("BEHAV_RESET", "BEHAV_SIMUL"));
        TEMPLATE.put("CFG_TIMEOUT", List.of("TIMEOUT_VALUE"));
        TEMPLATE.put("CFG_SWITCH", List.of("SWITCH_GE", "SWITCH_GSF", "PRERESET_ACT_TIME"));
        TEMPLATE.put("CFG_SUPERVIS_FMA1", List.of("RESET_TYPE", "RESET_DELAY"));
        TEMPLATE.put("CFG_SUPERVIS_FMA2", List.of("RESET_TYPE", "RESET_DELAY"));
        TEMPLATE.put("CFG_IP_SWITCH_TIME", List.of("IP_SWITCH_TIME"));
        TEMPLATE.put("CFG_RSR_TYPE", List.of("RSR_TYPE"));
    }

    /** Keys emitted only for GS06+, sourced from {@code versionDefault.*} (never the sheet). Design §6.4. */
    private static final Set<String> VERSION_AWARE =
            Set.of("TYPE_AUX1", "TYPE_AUX2", "TYPE_IN1", "TYPE_IN2", "TYPE_IN3", "SUPERVIS_COUNT_LMT");

    private final CqIrValueNormalizer normalizer;
    private final PdqMappingService mappingService;
    private final PdqWorkbookContract contract;
    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * @param sheet     the CQ-IR worksheet
     * @param gs06Plus  {@code true} for "GS06 and above" (include the version-aware group)
     * @return ordered {@code cqIrParameters} fragment: {@code IDENTIFICATION} + {@code CFG_*} blocks
     */
    public Map<String, Map<String, Object>> parse(Sheet sheet, boolean gs06Plus) {
        HeaderLocation header = locateHeader(sheet);

        Map<String, Object> flat = new LinkedHashMap<>();
        for (Row row : sheet) {
            if (row.getRowNum() <= header.rowNum() || row.getZeroHeight()) {
                continue; // header row and above, and hidden rows, are out of scope
            }
            String configWord = cellText(row, header.configWordCol());
            if (configWord.isEmpty()) {
                continue; // meta row
            }
            flat.put(configWord, normalizer.normalize(configWord, cellText(row, header.responseCol())));
        }

        return assemble(flat, gs06Plus);
    }

    private Map<String, Map<String, Object>> assemble(Map<String, Object> flat, boolean gs06Plus) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();

        // IDENTIFICATION is a top-level block whose content IS the {min,max} map.
        if (flat.get(IDENTIFICATION) instanceof Map<?, ?> idMap) {
            Map<String, Object> typed = new LinkedHashMap<>();
            idMap.forEach((k, v) -> typed.put(String.valueOf(k), v));
            out.put(IDENTIFICATION, typed);
        }

        for (Map.Entry<String, List<String>> block : TEMPLATE.entrySet()) {
            Map<String, Object> blockMap = new LinkedHashMap<>();
            for (String key : block.getValue()) {
                if (VERSION_AWARE.contains(key)) {
                    if (gs06Plus) {
                        blockMap.put(key, versionDefault(key));
                    }
                } else if (flat.containsKey(key)) {
                    blockMap.put(key, flat.get(key));
                }
            }
            out.put(block.getKey(), blockMap);
        }
        return out;
    }

    private String versionDefault(String key) {
        String value = mappingService.versionDefault(key);
        if (value == null) {
            throw new PdqInvalidException(PdqInvalidReason.MAPPING_LOOKUP_FAILED,
                    "No versionDefault configured for GS06+ key '" + key + "'");
        }
        return value;
    }

    private HeaderLocation locateHeader(Sheet sheet) {
        String configWordHeader = contract.cqirConfigWordHeader();
        String responseHeader = contract.cqirResponseHeader();
        for (Row row : sheet) {
            Integer configWordCol = null;
            Integer responseCol = null;
            for (Cell cell : row) {
                String v = dataFormatter.formatCellValue(cell).strip();
                if (configWordHeader.equalsIgnoreCase(v)) {
                    configWordCol = cell.getColumnIndex();
                } else if (responseHeader.equalsIgnoreCase(v)) {
                    responseCol = cell.getColumnIndex();
                }
            }
            if (configWordCol != null && responseCol != null) {
                return new HeaderLocation(row.getRowNum(), configWordCol, responseCol);
            }
        }
        throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING,
                "CQ-IR sheet has no row with both '" + configWordHeader + "' and '" + responseHeader + "' headers");
    }

    private String cellText(Row row, int col) {
        Cell cell = row.getCell(col);
        return (cell == null) ? "" : dataFormatter.formatCellValue(cell).strip();
    }

    private record HeaderLocation(int rowNum, int configWordCol, int responseCol) {
    }
}