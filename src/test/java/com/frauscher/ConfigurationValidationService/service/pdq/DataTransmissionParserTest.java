package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.pdq.DataSafetyLevel;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataTransmission;
import com.frauscher.ConfigurationValidationService.dto.pdq.OutputDataTransmission;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * Parses the Data Transmission Inputs sheet (design §6.6). The populated case asserts both
 * sub-tables and the string-valued, range-validated numeric fields against the dedicated DTIO
 * fixture; the empty case asserts graceful handling against the primary fixture's blank sheet.
 */
class DataTransmissionParserTest {

    private DataTransmissionParser parser;

    @BeforeEach
    void setUp() {
        parser = new DataTransmissionParser(new PdqWorkbookContract());
    }

    @Test
    void parsesPopulatedDataTransmission() throws Exception {
        DataTransmission dt;
        try (Workbook wb = PdqFixtures.loadDtioWorkbook()) {
            dt = parser.parse(wb.getSheet("Data transmission Inputs"));
        }

        assertNotNull(dt);
        assertEquals(2, dt.dataSafetyLevels().size());
        assertEquals(2, dt.outputDataTransmission().size());

        DataSafetyLevel safety = dt.dataSafetyLevels().get(0);
        assertEquals("DP1A", safety.dpName());
        assertEquals("3", safety.safetyLevelIn());
        assertEquals("3", safety.safetyLevelOut());
        assertEquals("0", safety.safeOutFdbckQuad());

        OutputDataTransmission output = dt.outputDataTransmission().get(0);
        assertEquals("DP4A", output.sourceDpName());
        assertEquals("15", output.nmbrOut());
        assertEquals("31", output.position());
    }

    @Test
    void returnNullWhenDataTransmissionHasNoDataRows() throws Exception {
        DataTransmission dt;
        try (Workbook wb = PdqFixtures.loadWorkbook()) {
            dt = parser.parse(wb.getSheet("Data transmission Inputs"));
        }

        assertNull(dt);

    }
}