package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * End-to-end parse of the frozen PDQ Ver14 workbook through {@link PdqParsingService}. Exercises the
 * Ver14-specific changes: the GS07 board version (-&gt; version-aware keys included), the project block
 * sourced from the CQ-IR PROJECT_NUMBER row, the control table's separate "Logic type" operator column
 * with the shifted DP table, and the empty DT sheet (headers + footer note) collapsing to {@code null}.
 */
class PdqVer14ParsingTest {

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
    void parsesVer14Workbook() throws Exception {
        PdqUploadResponse response;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/pdq-phase2-sample.xlsx")) {
            response = service.parse(in);
        }

        Map<String, Map<String, Object>> cqir = response.getCqIrParameters();

        // Header: Ver14 AEB board version is "GS07" -> GS06-and-above.
        assertEquals("GS07", response.getAebEquipmentVersion());

        // GS06+ -> version-aware keys emitted (all versionDefault "0").
        assertEquals("0", cqir.get("CFG_SECTION_OUT").get("TYPE_AUX1"));
        assertEquals("0", cqir.get("CFG_AXCNT").get("TYPE_IN1"));
        assertEquals("0", cqir.get("CFG_ZP").get("SUPERVIS_COUNT_LMT"));

        // A few CQ-IR scalars normalised from Ver14 values.
        assertEquals(Map.of("min", 1, "max", 4095), cqir.get("IDENTIFICATION"));
        assertEquals("0", cqir.get("CFG_OCC").get("OCC_EXT"));        // Ver14 OCC_EXT = 0
        assertEquals("2", cqir.get("CFG_ZP").get("INTERVAL"));        // 80 -> 2
        assertEquals(List.of("34", "61", "0", "0", "0", "0", "0", "0"),
                cqir.get("CFG_TIMEOUT").get("TIMEOUT_VALUE"));        // "340 & 610"
        assertEquals("26", cqir.get("CFG_SWITCH").get("SWITCH_GE"));  // 2600 -> 26
        assertEquals("1", cqir.get("CFG_RSR_TYPE").get("RSR_TYPE")); // "1: RSR 180" -> "1" (now PDQ-sourced)

        // Project block from the CQ-IR PROJECT_NUMBER row (Response "Yes" -> 1; blank Remarks -> "0").
        assertEquals("true", cqir.get("CFG_PROJECT_AEB").get("BLOCK_EXISTS"));
        assertEquals("0", cqir.get("CFG_PROJECT_AEB").get("PROJECT_NUMBER"));
        assertEquals("true", cqir.get("CFG_PROJECT_COM").get("BLOCK_EXISTS"));
        assertEquals("0", cqir.get("CFG_PROJECT_COM").get("PROJECT_NUMBER"));

        // Control table: 7 track sections, 8 DP rows.
        assertEquals(7, response.getControlTable().trackSections().size());
        assertEquals(8, response.getControlTable().dpTable().size());

        // 1AXT1: PHYSICAL -> MAIN; operands from col G, operator "OR" from the separate Logic-type col.
        TrackSection t0 = response.getControlTable().trackSections().get(0);
        assertEquals("1AXT1", t0.name());
        assertEquals("MAIN", t0.trackType());
        assertEquals("OR", t0.fadcAutoReset().op());
        assertEquals(List.of("1AXT2", "SUP1-AXT1"), t0.fadcAutoReset().operands());
        assertFalse(t0.autoResetByTimer());

        // 201AXT: NA -> null fadc; Auto reset by timer = Yes (now col I).
        TrackSection t2 = response.getControlTable().trackSections().get(2);
        assertEquals("201AXT", t2.name());
        assertNull(t2.fadcAutoReset());
        assertTrue(t2.autoResetByTimer());

        // SUP1-AXT1: VIRTUAL -> COMBINATION, AND.
        TrackSection t3 = response.getControlTable().trackSections().get(3);
        assertEquals("COMBINATION", t3.trackType());
        assertEquals("AND", t3.fadcAutoReset().op());
        assertEquals(List.of("1AXT1", "2AXT1"), t3.fadcAutoReset().operands());

        // DP table (cols K-N): DP1A ABOVE / E-CHC Yes; DP1B BELOW.
        DpTableRow dp0 = response.getControlTable().dpTable().get(0);
        assertEquals("DP1A", dp0.name());
        assertEquals("ABOVE THE RAIL", dp0.position());
        assertTrue(dp0.eChc());
        DpTableRow dp5 = response.getControlTable().dpTable().get(5);
        assertEquals("DP1B", dp5.name());
        assertEquals("BELOW THE RAIL", dp5.position());

        // DT sheet has only headers + a footer note -> null (note row skipped, not parsed).
        assertNull(response.getDataTransmission());
    }
}