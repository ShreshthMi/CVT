package com.frauscher.ConfigurationValidationService.service.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.SupervisorDetail;

/**
 * The detail tables show {@code SECTION} 1-based, and three extractors derived that with a bare
 * {@code Integer.parseInt}. Since {@code extractValueFromBlock} returns {@code ""} for an absent entry, a
 * {@code CFG_CONTROL} or {@code CFG_SUPERVIS_FMA*} block without a {@code SECTION} threw inside
 * {@code SummaryService.generateSummary} and failed the entire validate request with a 500 — one malformed
 * block taking down a whole package. They now share
 * {@link com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil#fmaFromSection(String)},
 * which was already the shape {@code IOEXBAcoExtractorService} used.
 */
class SectionFallbackTest {

    /** Echoes raw values: {@link ValueMappingService} loads its properties in a private @PostConstruct. */
    private final ValueMappingService echo = new ValueMappingService() {
        @Override
        public String mapValue(String fieldKey, String value) {
            return value == null ? "" : value;
        }
    };

    private final CHCExtractorService chc = extractor(new CHCExtractorService());
    private final SupervisorExtractorService supervisor = extractor(new SupervisorExtractorService());

    // ---------- CHC Details ----------

    @Test
    void chcSurvivesAControlBlockWithNoSection() {
        ParsedConfigFile file = chcFile(control(0, entry("ID", "7")));

        List<CHCDetail> rows = assertDoesNotThrow(() -> chc.extractCHCDetails(list(file)));

        assertEquals(1, rows.size());
        assertEquals("", rows.get(0).getFmaDtl1(), "an absent SECTION degrades to the column's empty default");
    }

    @Test
    void chcPassesANonNumericSectionThrough() {
        ParsedConfigFile file = chcFile(control(0, entry("ID", "7"), entry("SECTION", "N/A")));

        assertEquals("N/A", chc.extractCHCDetails(list(file)).get(0).getFmaDtl1());
    }

    /** Valid data must be untouched: SECTION is still displayed 1-based. */
    @Test
    void chcStillIncrementsAValidSection() {
        ParsedConfigFile file = chcFile(
                control(0, entry("ID", "7"), entry("SECTION", "0")),
                control(1, entry("ID", "9"), entry("SECTION", "1")));

        CHCDetail row = chc.extractCHCDetails(list(file)).get(0);
        assertEquals("1", row.getFmaDtl1());
        assertEquals("2", row.getFmaDtl2());
    }

    // ---------- Supervisor Details ----------

    /**
     * sup_by_ts_fma is one of several parallel arrays the annotator addresses by index, so a bad SECTION
     * has to still contribute an element — dropping it would silently shift every later member's cell.
     */
    @Test
    void supervisorKeepsItsParallelArraysAlignedWhenASectionIsMissing() {
        ParsedConfigFile file = supervisorFile(
                supervis(0, entry("ID", "5"), entry("SECTION", "0")),
                supervis(1, entry("ID", "6")));

        List<SupervisorDetail> rows = assertDoesNotThrow(() -> supervisor.extractSupervisorDetails(list(file)));

        SupervisorDetail row = rows.get(0);
        assertEquals(List.of("1", ""), row.getSupByTsFma());
        assertEquals(row.getSupByTsDpId().size(), row.getSupByTsFma().size(), "arrays must stay aligned");
    }

    @Test
    void supervisorPassesANonNumericSectionThrough() {
        ParsedConfigFile file = supervisorFile(supervis(0, entry("ID", "5"), entry("SECTION", "x")));

        assertEquals(List.of("x"), supervisor.extractSupervisorDetails(list(file)).get(0).getSupByTsFma());
    }

    // ---------- fixtures ----------

    private <T> T extractor(T service) {
        ReflectionTestUtils.setField(service, "valueMappingService", echo);
        return service;
    }

    /** CFG_ZP is required for a CHC row; CFG_CONTROL supplies the slots. */
    private ParsedConfigFile chcFile(ConfigBlock... controls) {
        List<ConfigBlock> blocks = new ArrayList<>();
        blocks.add(block("ID", 0, entryC("ID", "1", "DP1A")));
        blocks.add(block("CFG_ZP", 0, entry("INTERVAL", "1"), entry("SUPERVIS_COUNT", "2"),
                entry("SYSTEM_COUNT", "3"), entry("PARTIAL_COUNT", "4")));
        blocks.addAll(List.of(controls));
        return new ParsedConfigFile("C1.ADC", blocks, true, false, false, false, 1);
    }

    private ParsedConfigFile supervisorFile(ConfigBlock... supervisBlocks) {
        List<ConfigBlock> blocks = new ArrayList<>();
        blocks.add(block("ID", 0, entryC("ID", "1", "DP1A")));
        blocks.addAll(List.of(supervisBlocks));
        return new ParsedConfigFile("C1.ADC", blocks, true, false, false, false, 1);
    }

    private ConfigBlock control(int blockIndex, ConfigEntry... entries) {
        return block("CFG_CONTROL", blockIndex, entries);
    }

    private ConfigBlock supervis(int blockIndex, ConfigEntry... entries) {
        return block("CFG_SUPERVIS_FMA1", blockIndex, entries);
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

    private ConfigEntry entryC(String key, String value, String comment) {
        return new ConfigEntry(key, 0, value, comment);
    }

    @SafeVarargs
    private <T> List<T> list(T... items) {
        return new ArrayList<>(List.of(items));
    }
}
