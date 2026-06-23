package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;

/**
 * Unit tests for the scalar-bucket builder (VTF-335 M3 / v2-expectations-contract.md §4): the
 * IDENTIFICATION→ID transform, verbatim copy of the other cqIR blocks (incl. the parser-appended
 * CFG_PROJECT_* blocks), and the tpf merge (PDQ wins on overlap).
 */
class ScalarExpectationsBuilderTest {

    private final ScalarExpectationsBuilder builder = new ScalarExpectationsBuilder();

    @Test
    void identificationBecomesIdDotIdAndOtherBlocksCopyVerbatim() {
        Map<String, Map<String, Object>> cqIr = new LinkedHashMap<>();
        cqIr.put("IDENTIFICATION", m("min", 1, "max", 4095));
        cqIr.put("CFG_SECTION", m("COMM_FAIL", "0", "BEHAV_GE", "1"));
        cqIr.put("CFG_RSR_TYPE", m("RSR_TYPE", "1"));
        cqIr.put("CFG_PROJECT_AEB", m("BLOCK_EXISTS", "true", "PROJECT_NUMBER", "123"));

        Map<String, Map<String, Object>> scalar = builder.build(input(cqIr));

        assertFalse(scalar.containsKey("IDENTIFICATION"), "IDENTIFICATION must be rewritten to ID");
        assertEquals(m("min", 1, "max", 4095), scalar.get("ID").get("ID"));
        assertEquals("0", scalar.get("CFG_SECTION").get("COMM_FAIL"));
        assertEquals("1", scalar.get("CFG_RSR_TYPE").get("RSR_TYPE"));
        assertEquals("123", scalar.get("CFG_PROJECT_AEB").get("PROJECT_NUMBER"));
    }

    @Test
    void tpfBlocksAreMergedAndPdqWinsOnOverlap() {
        Map<String, Map<String, Object>> cqIr = new LinkedHashMap<>();
        cqIr.put("CFG_RSR_TYPE", m("RSR_TYPE", "1"));

        ValidationInputV2 in = input(cqIr);
        in.addTpfSection("CFG_TROLLEY_SUPP", m("TROLLEY_SUPP", "1"));   // tpf-only -> added
        in.addTpfSection("CFG_RSR_TYPE", m("RSR_TYPE", "1"));          // overlaps PDQ -> PDQ kept

        Map<String, Map<String, Object>> scalar = builder.build(in);

        assertEquals("1", scalar.get("CFG_TROLLEY_SUPP").get("TROLLEY_SUPP"));
        assertEquals("1", scalar.get("CFG_RSR_TYPE").get("RSR_TYPE"));
    }

    @Test
    void tpfSuppliesRsrTypeWhenPdqDoesNot() {
        Map<String, Map<String, Object>> cqIr = new LinkedHashMap<>();
        cqIr.put("CFG_SECTION", m("COMM_FAIL", "0"));

        ValidationInputV2 in = input(cqIr);
        in.addTpfSection("CFG_RSR_TYPE", m("RSR_TYPE", "3"));

        Map<String, Map<String, Object>> scalar = builder.build(in);

        assertEquals("3", scalar.get("CFG_RSR_TYPE").get("RSR_TYPE"));
    }

    @Test
    void nullCqIrParametersYieldsOnlyTpfBlocks() {
        ValidationInputV2 in = input(null);
        in.addTpfSection("CFG_TYPE_PRTCT", m("TYPE_PRTCT_CODE", "0xabc"));

        Map<String, Map<String, Object>> scalar = builder.build(in);

        assertTrue(scalar.containsKey("CFG_TYPE_PRTCT"));
        assertFalse(scalar.containsKey("ID"));
    }

    private ValidationInputV2 input(Map<String, Map<String, Object>> cqIr) {
        ValidationInputV2 in = new ValidationInputV2();
        in.setPdqData(PdqUploadResponse.builder().cqIrParameters(cqIr).build());
        return in;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
