package com.frauscher.ConfigurationValidationService.validation.instanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.service.preprocessor.InstancedExpectation;
import com.frauscher.ConfigurationValidationService.util.CfgParserUtil;

/**
 * Integration check that the real ADC parser ({@link CfgParserUtil}) produces blocks/entries in the
 * shape the {@link InstancedExpectationEvaluator} relies on for Option-B occurrence selection: the
 * block name comes from its first entry's key, repeated blocks get a {@code blockIndex}, and the
 * identity/value entries ({@code ID}, {@code DIR_INV}, {@code SLCT_TIMEOUT}) are plain entries. The
 * other tests hand-build {@code ParsedConfigFile}s; this one parses real ADC text end-to-end.
 */
class ParsedAdcInstancedEvaluatorTest {

    private static final String DP_ADC = """
            [CONFIG]
            ID 12:855 //DP_TEST
            [CONFIG]
            CFG_ZP_FMA1 7:1 //1AXT1
            DIR_INV 1:0
            SLCT_TIMEOUT 3:1
            ID 12:855 //self head
            [CONFIG]
            CFG_ZP_FMA1 7:1 //1AXT1
            DIR_INV 1:1
            SLCT_TIMEOUT 3:0
            ID 12:856 //head B
            """;

    private final InstancedExpectationEvaluator evaluator =
            new InstancedExpectationEvaluator(new ForwardingDestinationResolver());

    @Test
    void countingHeadsParsedFromAdcAllPass() {
        ParsedConfigFile file = parse("C0855_00.ADC", DP_ADC);
        assertEquals(855, file.getId());

        List<ValidationResult> results = evaluator.evaluate(List.of(file), List.of(
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "0"),
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "855"), "SLCT_TIMEOUT", "1"),
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "856"), "DIR_INV", "1"),
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "856"), "SLCT_TIMEOUT", "0")));

        assertEquals(4, results.size());
        assertTrue(results.stream().allMatch(r -> "PASS".equals(r.getStatus())), () -> results.toString());
    }

    @Test
    void wrongValueParsedFromAdcFails() {
        ParsedConfigFile file = parse("C0855_00.ADC", DP_ADC);

        // Both heads expected (so neither is an extra); head 855's DIR_INV is wrong (ADC=0, expect 1).
        List<ValidationResult> results = evaluator.evaluate(List.of(file), List.of(
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "1"),
                InstancedExpectation.byIdentity(855, "CFG_ZP_FMA1", Map.of("ID", "856"), "DIR_INV", "1")));

        ValidationResult head855 = results.stream()
                .filter(r -> r.getEntryKey().contains("855")).findFirst().orElseThrow();
        assertEquals("FAIL", head855.getStatus());
        assertEquals("0", head855.getActualValue());
        assertTrue(results.stream()
                .anyMatch(r -> r.getEntryKey().contains("856") && "PASS".equals(r.getStatus())));
    }

    private ParsedConfigFile parse(String name, String content) {
        return CfgParserUtil.parse(new MockMultipartFile("file", name, "text/plain", content.getBytes()));
    }
}
