package com.frauscher.ConfigurationValidationService.service.pdq;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates PDQ workbook parsing for BE-01: opens the {@code .xlsx} read-only from the upload
 * stream (in-memory, no spool, no file lock), reads the PDQ-sheet header and the CQ-IR sheet, and
 * assembles the {@link PdqUploadResponse}, including the Control table and the conditional Data
 * Transmission sub-sheet ({@code dataTransmission} is {@code null} when that sheet is absent or carries
 * only headers).
 */
@Service
@RequiredArgsConstructor
public class PdqParsingService {

    private final PdqWorkbookContract contract;
    private final PdqSheetHeaderParser headerParser;
    private final CqIrSheetParser cqIrSheetParser;
    private final ControlTableParser controlTableParser;
    private final DataTransmissionParser dataTransmissionParser;
    private final ProjectBlockResolver projectBlockResolver;

    public PdqUploadResponse parse(InputStream xlsx) {
        try (Workbook workbook = WorkbookFactory.create(xlsx)) {
            Sheet pdqSheet = requireSheet(workbook, contract.pdqSheet());
            Sheet cqIrSheet = requireSheet(workbook, contract.cqirSheet());
            Sheet controlTableSheet = requireSheet(workbook, contract.controlTableSheet());
            Sheet dataTransmissionSheet = workbook.getSheet(contract.dataTransmissionSheet()); // conditional

            PdqHeader header = headerParser.parse(pdqSheet);
            Map<String, Map<String, Object>> cqIrParameters =
                    cqIrSheetParser.parse(cqIrSheet, header.gs06Plus());
            appendProjectBlocks(cqIrParameters, projectBlockResolver.resolve(cqIrSheet));

            return PdqUploadResponse.builder()
                    .projectCode(header.projectCode())
                    .aebEquipmentVersion(header.aebEquipmentVersion())
                    .cqIrParameters(cqIrParameters)
                    .controlTable(controlTableParser.parse(controlTableSheet))
                    .dataTransmission(dataTransmissionParser.parse(dataTransmissionSheet))
                    .build();
        } catch (IOException e) {
            throw new PdqInvalidException(PdqInvalidReason.WORKBOOK_UNREADABLE,
                    "Could not read the workbook: " + e.getMessage());
        }
    }

    private Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new PdqInvalidException(PdqInvalidReason.SHEET_MISSING, "Missing mandatory sheet: " + name);
        }
        return sheet;
    }

    /** CFG_PROJECT_AEB and CFG_PROJECT_COM carry the same BLOCK_EXISTS + PROJECT_NUMBER (from the CQ-IR PROJECT_NUMBER row). */
    private void appendProjectBlocks(Map<String, Map<String, Object>> cqIrParameters, ProjectBlockResolver.ProjectBlock project) {
        cqIrParameters.put("CFG_PROJECT_AEB", projectBlock(project));
        cqIrParameters.put("CFG_PROJECT_COM", projectBlock(project));
    }

    private Map<String, Object> projectBlock(ProjectBlockResolver.ProjectBlock project) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("BLOCK_EXISTS", project.blockExists());
        block.put("PROJECT_NUMBER", project.projectNumber());
        return block;
    }
}