package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * End-to-end parse of the fully-populated fixture workbook (GS05, Single redundancy) through
 * {@link PdqParsingService}. Asserts the header/project-block invariants and the BE-02
 * control-table / data-transmission state, and dumps the full response to
 * {@code build/pdq-result.json} for eyeballing.
 */
class PdqParsingServiceTest {

    private PdqParsingService service;

    @BeforeEach
    void setUp() {
        PdqMappingService mapping = new PdqMappingService();
        PdqWorkbookContract contract = new PdqWorkbookContract();
        service = new PdqParsingService(
                contract,
                new PdqSheetHeaderParser(contract),
                new CqIrSheetParser(new CqIrValueNormalizer(mapping), mapping, contract),
                new ControlTableParser(contract),
                new DataTransmissionParser(contract));
    }

    @Test
    void parsesFullFixture() throws Exception {
        PdqUploadResponse response;
        try (InputStream in = PdqFixtures.openDtioWorkbook()) {
            response = service.parse(in);
        }

        // Header: fixture is GS05 / Single.
        assertEquals("GS05 and below", response.getAebEquipmentVersion());
        assertNotNull(response.getProjectCode());

        Map<String, Map<String, Object>> cqir = response.getCqIrParameters();
        Map<String, Object> aeb = cqir.get("CFG_PROJECT_AEB");
        Map<String, Object> com = cqir.get("CFG_PROJECT_COM");

        // Single -> BLOCK_EXISTS "true"; PROJECT_NUMBER == projectCode in both blocks.
        assertEquals("true", aeb.get("BLOCK_EXISTS"));
        assertEquals("true", com.get("BLOCK_EXISTS"));
        assertEquals(response.getProjectCode(), aeb.get("PROJECT_NUMBER"));
        assertEquals(response.getProjectCode(), com.get("PROJECT_NUMBER"));

        // GS05 -> version-aware group omitted.
        assertFalse(cqir.get("CFG_ZP").containsKey("SUPERVIS_COUNT_LMT"));
        assertFalse(cqir.get("CFG_SECTION_OUT").containsKey("TYPE_AUX1"));

        // BE-02: control table + data transmission both populated in the fully-populated fixture.
        assertNotNull(response.getControlTable());
        assertEquals(7, response.getControlTable().trackSections().size());
        assertEquals(8, response.getControlTable().dpTable().size());
        assertNotNull(response.getDataTransmission());
        assertEquals(2, response.getDataTransmission().dataSafetyLevels().size());
        assertEquals(2, response.getDataTransmission().outputDataTransmission().size());

        // Dump for inspection.
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(new File("build/pdq-result.json"), response);
    }
}