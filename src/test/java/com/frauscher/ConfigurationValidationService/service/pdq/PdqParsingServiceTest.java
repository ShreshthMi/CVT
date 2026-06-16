package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * End-to-end parse of the fully-populated (Ver14, GS07) fixture workbook through
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
                new DataTransmissionParser(contract),
                new ProjectBlockResolver(contract));
    }

    @Test
    void parsesFullFixture() throws Exception {
        PdqUploadResponse response;
        try (InputStream in = PdqFixtures.openDtioWorkbook()) {
            response = service.parse(in);
        }

        // Header: Ver14 fixture is GS07 (GS06-and-above).
        assertEquals("GS07", response.getAebEquipmentVersion());
        assertNotNull(response.getProjectCode());

        Map<String, Map<String, Object>> cqir = response.getCqIrParameters();
        Map<String, Object> aeb = cqir.get("CFG_PROJECT_AEB");
        Map<String, Object> com = cqir.get("CFG_PROJECT_COM");

        // PROJECT_NUMBER row Response "Yes" -> BLOCK_EXISTS "true"; PROJECT_NUMBER from Remarks (blank -> "0").
        assertEquals("true", aeb.get("BLOCK_EXISTS"));
        assertEquals("true", com.get("BLOCK_EXISTS"));
        assertEquals("0", aeb.get("PROJECT_NUMBER"));
        assertEquals("0", com.get("PROJECT_NUMBER"));

        // GS07 -> version-aware group included (versionDefault "0").
        assertEquals("0", cqir.get("CFG_ZP").get("SUPERVIS_COUNT_LMT"));
        assertEquals("0", cqir.get("CFG_SECTION_OUT").get("TYPE_AUX1"));

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