package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * Parses the CQ-IR sheet of the Ver14 fixture with {@code gs06Plus=false}, so the version-aware
 * group is omitted, and asserts the block-grouped output against the design §6.4 shape.
 */
class CqIrSheetParserTest {

    private CqIrSheetParser parser;

    @BeforeEach
    void setUp() {
        PdqMappingService mapping = new PdqMappingService();
        parser = new CqIrSheetParser(new CqIrValueNormalizer(mapping), mapping, new PdqWorkbookContract());
    }

    @Test
    void parsesFixtureCqIrAsGs05() throws Exception {
        try (Workbook wb = PdqFixtures.loadWorkbook()) {
            Sheet sheet = wb.getSheet("CQ-IR");
            Map<String, Map<String, Object>> r = parser.parse(sheet, /* gs06Plus = */ false);

            // IDENTIFICATION is a top-level block carrying the {min,max} map (integers).
            assertEquals(Map.of("min", 1, "max", 4095), r.get("IDENTIFICATION"));

            // Scalar blocks (values normalized: colon-split, step-divided, INTERVAL-mapped).
            assertEquals(Map.of("COMM_FAIL", "0", "BEHAV_GE", "1", "CLR_TRACK", "0", "RESET_IN", "5"),
                    r.get("CFG_SECTION"));
            assertEquals(Map.of("RESET_OP_TIME", "50", "RESET_LD_TIME", "1"), r.get("CFG_RESET"));
            assertEquals(Map.of("OCC_EXT", "0", "OCC_DELAY", "0"), r.get("CFG_OCC"));
            assertEquals(Map.of("INTERVAL", "2", "SUPERVIS_COUNT", "2", "SYSTEM_COUNT", "2", "PARTIAL_COUNT", "1"),
                    r.get("CFG_ZP"));
            assertEquals(Map.of("BEHAV_RESET", "7", "BEHAV_SIMUL", "0"), r.get("CFG_BEHAV_TGGL"));
            assertEquals(Map.of("SWITCH_GE", "26", "SWITCH_GSF", "0", "PRERESET_ACT_TIME", "180"),
                    r.get("CFG_SWITCH"));

            // TIMEOUT_VALUE is the padded array.
            assertEquals(List.of("34", "61", "0", "0", "0", "0", "0", "0"),
                    r.get("CFG_TIMEOUT").get("TIMEOUT_VALUE"));

            // RESET_TYPE / RESET_DELAY written identically into both FMA blocks.
            assertEquals(Map.of("RESET_TYPE", "3", "RESET_DELAY", "10"), r.get("CFG_SUPERVIS_FMA1"));
            assertEquals(Map.of("RESET_TYPE", "3", "RESET_DELAY", "10"), r.get("CFG_SUPERVIS_FMA2"));

            // gs06Plus=false -> version-aware keys omitted.
            assertEquals(Map.of("CLR_OCC", "0", "AUX1_OUT", "3", "AUX2_OUT", "3", "AUX1_NO_NC", "0", "AUX2_NO_NC", "0"),
                    r.get("CFG_SECTION_OUT"));
            assertEquals(Map.of("BEHAV_INPUT1", "6", "BEHAV_INPUT2", "6", "BEHAV_IOEXB", "7"), r.get("CFG_AXCNT"));
            assertFalse(r.get("CFG_ZP").containsKey("SUPERVIS_COUNT_LMT"));
            assertFalse(r.get("CFG_SECTION_OUT").containsKey("TYPE_AUX1"));
            assertFalse(r.get("CFG_AXCNT").containsKey("TYPE_IN1"));

            // RSR_TYPE is now PDQ-sourced and emitted as its own block ("1: RSR 180" -> "1").
            assertEquals(Map.of("RSR_TYPE", "1"), r.get("CFG_RSR_TYPE"));

            // Canonical block order (IDENTIFICATION first; project blocks added later by the caller).
            assertEquals(List.of("IDENTIFICATION", "CFG_SECTION", "CFG_RESET", "CFG_SECTION_OUT", "CFG_AXCNT",
                            "CFG_OCC", "CFG_ZP", "CFG_BEHAV_TGGL", "CFG_TIMEOUT", "CFG_SWITCH",
                            "CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2", "CFG_IP_SWITCH_TIME", "CFG_RSR_TYPE"),
                    new ArrayList<>(r.keySet()));
        }
    }
}