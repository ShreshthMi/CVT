package com.frauscher.ConfigurationValidationService.validation.instanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.service.preprocessor.InstancedExpectation;

/**
 * Unit tests for the instanced occurrence-selection engine (v2-expectations-contract.md §5–§6): SINGLE
 * (mandatory + optional), BY_IDENTITY set-equality (missing → FAIL, extra → UNEXPECTED_OCCURRENCE,
 * order-independent, per-member value check), and POSITIONAL slot matching. Inputs are hand-built
 * {@link ParsedConfigFile}s + the {@link InstancedExpectation} factories the BE-05 builders emit.
 */
class InstancedExpectationEvaluatorTest {

    private final InstancedExpectationEvaluator evaluator =
            new InstancedExpectationEvaluator(new ForwardingDestinationResolver());

    // ---------- SINGLE ----------

    @Test
    void singleMandatoryPassesWhenValueMatchesAndFailsOtherwise() {
        ParsedConfigFile pass = file(10, false, block("CFG_AXCNT", 0, entry("BEHAV_INPUT3", "6")));
        ParsedConfigFile fail = file(11, false, block("CFG_AXCNT", 0, entry("BEHAV_INPUT3", "7")));

        List<InstancedExpectation> exp = List.of(
                InstancedExpectation.single(10, "CFG_AXCNT", "BEHAV_INPUT3", "6"),
                InstancedExpectation.single(11, "CFG_AXCNT", "BEHAV_INPUT3", "6"));

        List<ValidationResult> results = evaluator.evaluate(List.of(pass, fail), exp);

        assertEquals("PASS", statusFor(results, "CFG_AXCNT", "BEHAV_INPUT3", "C10"));
        assertEquals("FAIL", statusFor(results, "CFG_AXCNT", "BEHAV_INPUT3", "C11"));
    }

    @Test
    void singleMandatoryFailsWhenBlockAbsent() {
        ParsedConfigFile file = file(10, false, block("ID", 0, entry("ID", "10")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file),
                List.of(InstancedExpectation.single(10, "CFG_AXCNT", "BEHAV_INPUT3", "6")));

        assertEquals(1, results.size());
        assertEquals("FAIL", results.get(0).getStatus());
        assertEquals(ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND, results.get(0).getActualValue());
    }

    @Test
    void singleOptionalPassesWhenBlockAbsentAndDefaultEqualsExpected() {
        ParsedConfigFile file = file(200, true, block("ID", 0, entry("ID", "200")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file),
                List.of(InstancedExpectation.singleOptional(200, "CFG_IP_SWITCH", "IP_SWITCH", "0", "0")));

        assertEquals(1, results.size());
        assertEquals("PASS", results.get(0).getStatus());
        assertEquals(ValidationConstants.CONFIG_BLOCK_NOT_FOUND, results.get(0).getActualValue());
    }

    @Test
    void singleOptionalFailsWhenPresentButWrong() {
        ParsedConfigFile file = file(200, true, block("CFG_IP_SWITCH", 0, entry("IP_SWITCH", "1")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file),
                List.of(InstancedExpectation.singleOptional(200, "CFG_IP_SWITCH", "IP_SWITCH", "0", "0")));

        assertEquals("FAIL", results.get(0).getStatus());
        assertEquals("1", results.get(0).getActualValue());
    }

    // ---------- BY_IDENTITY ----------

    @Test
    void byIdentityAllMembersMatchOrderIndependent() {
        // Two heads on one DP file, occurrences in REVERSE order of emission.
        ParsedConfigFile file = file(500, false,
                block("CFG_ZP_FMA1", 0, entry("ID", "856"), entry("DIR_INV", "1"), entry("SLCT_TIMEOUT", "0")),
                block("CFG_ZP_FMA1", 1, entry("ID", "855"), entry("DIR_INV", "0"), entry("SLCT_TIMEOUT", "1")));

        List<InstancedExpectation> exp = List.of(
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "0"),
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "SLCT_TIMEOUT", "1"),
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "856"), "DIR_INV", "1"),
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "856"), "SLCT_TIMEOUT", "0"));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), exp);

        assertEquals(4, results.size());
        assertTrue(results.stream().allMatch(r -> "PASS".equals(r.getStatus())), () -> results.toString());
    }

    @Test
    void byIdentityMissingMemberFails() {
        ParsedConfigFile file = file(500, false,
                block("CFG_ZP_FMA1", 0, entry("ID", "855"), entry("DIR_INV", "0"), entry("SLCT_TIMEOUT", "1")));

        List<InstancedExpectation> exp = List.of(
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "0"),
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "856"), "DIR_INV", "1"));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), exp);

        ValidationResult missing = results.stream()
                .filter(r -> ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND.equals(r.getActualValue()))
                .findFirst().orElseThrow();
        assertEquals("FAIL", missing.getStatus());
        assertTrue(missing.getEntryKey().contains("856"), missing.getEntryKey());
    }

    @Test
    void byIdentityExtraOccurrenceIsFlagged() {
        ParsedConfigFile file = file(500, false,
                block("CFG_ZP_FMA1", 0, entry("ID", "855"), entry("DIR_INV", "0"), entry("SLCT_TIMEOUT", "1")),
                block("CFG_ZP_FMA1", 1, entry("ID", "999"), entry("DIR_INV", "0"), entry("SLCT_TIMEOUT", "0")));

        List<InstancedExpectation> exp = List.of(
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "0"),
                InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "SLCT_TIMEOUT", "1"));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), exp);

        long extras = results.stream()
                .filter(r -> ValidationConstants.UNEXPECTED_OCCURRENCE.equals(r.getActualValue()))
                .count();
        assertEquals(1, extras);
        assertTrue(results.stream().anyMatch(r -> r.getEntryKey().contains("999")));
    }

    @Test
    void byIdentityWrongValueOnMatchedMemberFails() {
        ParsedConfigFile file = file(500, false,
                block("CFG_ZP_FMA1", 0, entry("ID", "855"), entry("DIR_INV", "1"), entry("SLCT_TIMEOUT", "1")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file),
                List.of(InstancedExpectation.byIdentity(500, "CFG_ZP_FMA1", Map.of("ID", "855"), "DIR_INV", "0")));

        assertEquals(1, results.size());
        assertEquals("FAIL", results.get(0).getStatus());
        assertEquals("1", results.get(0).getActualValue());
    }

    @Test
    void byIdentityCompositeKeyMatchesOnIdAndSection() {
        ParsedConfigFile file = file(700, false,
                block("CFG_SUPERVIS_FMA1", 0, entry("ID", "100"), entry("SECTION", "0"), entry("LOGIC_TYPE", "0")),
                block("CFG_SUPERVIS_FMA1", 1, entry("ID", "100"), entry("SECTION", "1"), entry("LOGIC_TYPE", "9")));

        // Expect the (ID=100, SECTION=0) member only; the (ID=100, SECTION=1) occurrence is an extra.
        List<ValidationResult> results = evaluator.evaluate(List.of(file),
                List.of(InstancedExpectation.byIdentity(
                        700, "CFG_SUPERVIS_FMA1", Map.of("ID", "100", "SECTION", "0"), "LOGIC_TYPE", "0")));

        assertEquals("PASS", statusContaining(results, "LOGIC_TYPE"));
        assertEquals(1, results.stream()
                .filter(r -> ValidationConstants.UNEXPECTED_OCCURRENCE.equals(r.getActualValue())).count());
    }

    // ---------- POSITIONAL ----------

    @Test
    void positionalMatchesInOrder() {
        ParsedConfigFile file = file(800, false,
                block("CFG_SECTION_OUT", 0, entry("ID", "10"), entry("SECTION", "0"), entry("SLCT_TIMEOUT", "0")),
                block("CFG_SECTION_OUT", 1, entry("ID", "20"), entry("SECTION", "1"), entry("SLCT_TIMEOUT", "0")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), acoPair());

        assertTrue(results.stream().allMatch(r -> "PASS".equals(r.getStatus())), () -> results.toString());
    }

    @Test
    void positionalResequencedConfigFails() {
        // Same pairs as expected but swapped between slots — a re-sequenced config must fail.
        ParsedConfigFile file = file(800, false,
                block("CFG_SECTION_OUT", 0, entry("ID", "20"), entry("SECTION", "1"), entry("SLCT_TIMEOUT", "0")),
                block("CFG_SECTION_OUT", 1, entry("ID", "10"), entry("SECTION", "0"), entry("SLCT_TIMEOUT", "0")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), acoPair());

        assertTrue(results.stream().anyMatch(r -> "FAIL".equals(r.getStatus())), () -> results.toString());
    }

    @Test
    void positionalMissingSlotFails() {
        ParsedConfigFile file = file(800, false,
                block("CFG_SECTION_OUT", 0, entry("ID", "10"), entry("SECTION", "0"), entry("SLCT_TIMEOUT", "0")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), acoPair());

        assertTrue(results.stream()
                .anyMatch(r -> ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND.equals(r.getActualValue())));
    }

    @Test
    void positionalExtraOccurrenceIsFlagged() {
        ParsedConfigFile file = file(800, false,
                block("CFG_SECTION_OUT", 0, entry("ID", "10"), entry("SECTION", "0"), entry("SLCT_TIMEOUT", "0")),
                block("CFG_SECTION_OUT", 1, entry("ID", "20"), entry("SECTION", "1"), entry("SLCT_TIMEOUT", "0")),
                block("CFG_SECTION_OUT", 2, entry("ID", "30"), entry("SECTION", "0"), entry("SLCT_TIMEOUT", "0")));

        List<ValidationResult> results = evaluator.evaluate(List.of(file), acoPair());

        assertEquals(1, results.stream()
                .filter(r -> ValidationConstants.UNEXPECTED_OCCURRENCE.equals(r.getActualValue())).count());
    }

    // ---------- routing ----------

    @Test
    void missingFileIsSkipped() {
        ParsedConfigFile other = file(1, false, block("ID", 0, entry("ID", "1")));

        List<ValidationResult> results = evaluator.evaluate(List.of(other),
                List.of(InstancedExpectation.single(999, "CFG_AXCNT", "BEHAV_INPUT3", "6")));

        assertTrue(results.isEmpty());
    }

    @Test
    void nullOrEmptyExpectationsYieldNoResults() {
        ParsedConfigFile file = file(1, false, block("ID", 0, entry("ID", "1")));
        assertTrue(evaluator.evaluate(List.of(file), null).isEmpty());
        assertTrue(evaluator.evaluate(List.of(file), List.of()).isEmpty());
    }

    // ---------- helpers ----------

    private List<InstancedExpectation> acoPair() {
        return List.of(
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 0, "ID", "10"),
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 0, "SECTION", "0"),
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 0, "SLCT_TIMEOUT", "0"),
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 1, "ID", "20"),
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 1, "SECTION", "1"),
                InstancedExpectation.positional(800, "CFG_SECTION_OUT", 1, "SLCT_TIMEOUT", "0"));
    }

    private String statusFor(List<ValidationResult> results, String block, String entry, String fileTag) {
        return results.stream()
                .filter(r -> block.equals(r.getBlockName()) && entry.equals(r.getEntryKey())
                        && fileTag.equals(r.getFileName()))
                .map(ValidationResult::getStatus)
                .findFirst().orElseThrow();
    }

    private String statusContaining(List<ValidationResult> results, String entryKeyPart) {
        return results.stream()
                .filter(r -> r.getEntryKey() != null && r.getEntryKey().contains(entryKeyPart))
                .map(ValidationResult::getStatus)
                .findFirst().orElseThrow();
    }

    private ParsedConfigFile file(int id, boolean comDetails, ConfigBlock... blocks) {
        List<ConfigBlock> list = new ArrayList<>(List.of(blocks));
        return new ParsedConfigFile("C" + id, list, false, false, false, comDetails, id);
    }

    private ConfigBlock block(String name, int blockIndex, ConfigEntry... entries) {
        ConfigBlock b = new ConfigBlock();
        b.setName(name);
        b.setBlockIndex(blockIndex);
        b.setSequenceNumber(blockIndex);
        b.setEntries(new ArrayList<>(List.of(entries)));
        return b;
    }

    private ConfigEntry entry(String key, String value) {
        return new ConfigEntry(key, 0, value, null);
    }
}
