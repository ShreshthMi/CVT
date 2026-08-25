package com.frauscher.ConfigurationValidationService.validation.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.DataTransmissionDetail;
import com.frauscher.ConfigurationValidationService.model.EthernetDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBAcoDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBBehaviourDetail;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation.Kind;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.SupervisorDetail;
import com.frauscher.ConfigurationValidationService.model.TrackSectionDetail;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.extractors.ValueMappingService;
import com.frauscher.ConfigurationValidationService.service.preprocessor.InstancedExpectation;
import com.frauscher.ConfigurationValidationService.validation.instanced.ForwardingDestinationResolver;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedExpectationEvaluator;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedFinding;

/**
 * BE-07 verdict→cell join ({@link MismatchAnnotator}). Reproduces the cases of the FE response-contract
 * sample (FCVT-v2-Validation-Response-Contract.md §8): counting-head UNEXPECTED + MISSING (union-array
 * padding), counting-head SLCT_TIMEOUT VALUE (timeout display transform), supervisor LOGIC_TYPE VALUE,
 * ioexb-behaviour scalar VALUE, ACO positional VALUE, CHC slot UNEXPECTED, and forwarding UNEXPECTED +
 * MISSING re-resolved by index. A value-mapping stub echoes raw values so display assertions are exact and
 * decoupled from {@code value-mappings.properties}; the timeout transform reads real {@code CFG_TIMEOUT}
 * blocks.
 */
class MismatchAnnotatorTest {

    /** Echoes raw values (no @PostConstruct property load) so display assertions are deterministic. */
    private final ValueMappingService echo = new ValueMappingService() {
        @Override
        public String mapValue(String fieldKey, String value) {
            return value == null ? "" : value;
        }
    };
    private final ForwardingDestinationResolver resolver = new ForwardingDestinationResolver();
    private final MismatchAnnotator annotator = new MismatchAnnotator(echo, resolver);

    // ---------- counting heads: UNEXPECTED + MISSING (union arrays) ----------

    @Test
    void countingHeadUnexpectedAndMissingPadParallelArrays() {
        TrackSectionDetail row = TrackSectionDetail.builder()
                .tsName("1AXT1").fma("1").dpId("5").dpName("DP1A")
                .chDpId(list("5", "6")).chDpName(list("DP1A", "DP2B")).chSlctTimeout(list("0", "0"))
                .iChDpId(list()).iChDpName(list()).iChSlctTimeout(list())
                .build();
        ValidationSummary summary = new ValidationSummary();
        summary.setTrackSectionDetails(list(row));

        InstancedFinding unexpected = new InstancedFinding(vr("r1", "FAIL"), "CFG_ZP_FMA1", 5,
                Kind.UNEXPECTED, Map.of("ID", "6"), null, null, null, null, Map.of());
        InstancedFinding missing = new InstancedFinding(vr("r2", "FAIL"), "CFG_ZP_FMA1", 5,
                Kind.MISSING, Map.of("ID", "1"), null, null, null, null, Map.of("DIR_INV", "0"));

        annotator.annotate(summary, List.of(), List.of(unexpected, missing), List.of(dpFile(1, "DP2A")));

        // union arrays: a MISSING slot is appended and the three parallel arrays stay aligned.
        assertEquals(List.of("5", "6", ""), row.getChDpId());
        assertEquals(List.of("DP1A", "DP2B", ""), row.getChDpName());
        assertEquals(List.of("0", "0", ""), row.getChSlctTimeout());

        MismatchAnnotation a1 = byField(row.getMismatches(), "ch_dp_name", 1);
        assertEquals(Kind.UNEXPECTED, a1.getKind());
        assertNull(a1.getExpected());
        assertEquals("DP2B", a1.getActual());
        assertEquals("r1", a1.getResultId());

        MismatchAnnotation a2 = byField(row.getMismatches(), "ch_dp_name", 2);
        assertEquals(Kind.MISSING, a2.getKind());
        assertEquals("DP2A", a2.getExpected());
        assertNull(a2.getActual());
        assertEquals("r2", a2.getResultId());
    }

    @Test
    void countingHeadSlctTimeoutValueUsesTimeoutDisplay() {
        TrackSectionDetail row = TrackSectionDetail.builder()
                .tsName("1AXT1").fma("1").dpId("5").dpName("DP1A")
                .chDpId(list("5")).chDpName(list("DP1A")).chSlctTimeout(list("620"))
                .iChDpId(list()).iChDpName(list()).iChSlctTimeout(list())
                .build();
        ValidationSummary summary = new ValidationSummary();
        summary.setTrackSectionDetails(list(row));

        ParsedConfigFile file = new ParsedConfigFile("C5", new ArrayList<>(List.of(
                block("ID", 0, entryC("ID", "5", "DP1A")),
                block("CFG_TIMEOUT", 0, entry("TIMEOUT_VALUE", "62")),
                block("CFG_TIMEOUT", 1, entry("TIMEOUT_VALUE", "10")))),
                true, false, false, false, 5);

        InstancedFinding value = new InstancedFinding(vr("r3", "FAIL"), "CFG_ZP_FMA1", 5,
                Kind.VALUE, Map.of("ID", "5"), null, "SLCT_TIMEOUT", "0", "1", Map.of("DIR_INV", "0"));

        annotator.annotate(summary, List.of(), List.of(value), List.of(file));

        MismatchAnnotation a = byField(row.getMismatches(), "ch_slct_timeout", 0);
        assertEquals(Kind.VALUE, a.getKind());
        assertEquals("620", a.getExpected()); // CFG_TIMEOUT[0]=62 ×10
        assertEquals("100", a.getActual());    // CFG_TIMEOUT[1]=10 ×10
        assertEquals("r3", a.getResultId());
    }

    // ---------- supervisor LOGIC_TYPE VALUE (member index by ID+SECTION) ----------

    @Test
    void supervisorLogicTypeValueAtMemberIndex() {
        SupervisorDetail row = SupervisorDetail.builder()
                .supName("SUP1-AXT1").dpId("5").dpName("DP1A")
                .supByTs(list("2AXT1")).supByTsDpId(list("1")).supByTsDpName(list("DP2A")).supByTsFma(list("1"))
                .timeOut(list("620")).logicType(list("1"))
                .build();
        ValidationSummary summary = new ValidationSummary();
        summary.setSupervisorDetail(list(row));

        InstancedFinding value = new InstancedFinding(vr("r20", "FAIL"), "CFG_SUPERVIS_FMA2", 5,
                Kind.VALUE, Map.of("ID", "1", "SECTION", "0"), null, "LOGIC_TYPE", "0", "1", Map.of());

        annotator.annotate(summary, List.of(), List.of(value), List.of());

        MismatchAnnotation a = byField(row.getMismatches(), "logic_type", 0);
        assertEquals(Kind.VALUE, a.getKind());
        assertEquals("0", a.getExpected());
        assertEquals("1", a.getActual());
        assertEquals("r20", a.getResultId());
    }

    // ---------- ioexb behaviour: scalar BEHAV_INPUT3 VALUE ----------

    @Test
    void behavInput3ScalarValue() {
        IOEXBBehaviourDetail row = IOEXBBehaviourDetail.builder().dpId("1").dpName("DP2A").behavInput3("7").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbBehaviourDetails(list(row));

        InstancedFinding value = new InstancedFinding(vr("r10", "FAIL"), "CFG_AXCNT", 1,
                Kind.VALUE, Map.of(), null, "BEHAV_INPUT3", "6", "7", Map.of());

        annotator.annotate(summary, List.of(), List.of(value), List.of());

        MismatchAnnotation a = byField(row.getMismatches(), "behav_input3", null);
        assertEquals(Kind.VALUE, a.getKind());
        assertEquals("6", a.getExpected());
        assertEquals("7", a.getActual());
        assertEquals("r10", a.getResultId());
    }

    // ---------- ACO positional VALUE: SECTION → fma_1_2 (SECTION+1 display) ----------

    @Test
    void acoSectionValueMapsToFmaColumn() {
        IOEXBAcoDetail row = IOEXBAcoDetail.builder().dpId("1").dpName("DP2A").acoFma1("2AXT1").fma12("1").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(row));

        ParsedConfigFile file = new ParsedConfigFile("C1", new ArrayList<>(List.of(
                block("CFG_SECTION_OUT", 0, entryC("CFG_SECTION_OUT", "9", "2AXT1")))),
                false, true, false, false, 1);

        InstancedFinding value = new InstancedFinding(vr("r6", "FAIL"), "CFG_SECTION_OUT", 1,
                Kind.VALUE, Map.of(), 0, "SECTION", "0", "1", Map.of());

        annotator.annotate(summary, List.of(), List.of(value), List.of(file));

        MismatchAnnotation a = byField(row.getMismatches(), "fma_1_2", null);
        assertEquals(Kind.VALUE, a.getKind());
        assertEquals("1", a.getExpected()); // SECTION 0 + 1
        assertEquals("2", a.getActual());   // SECTION 1 + 1
    }

    // ---------- CHC: an extra control → UNEXPECTED on the matching _1/_2 slot ----------

    @Test
    void chcUnexpectedControlOnSlot() {
        CHCDetail row = CHCDetail.builder().dpId("1").dpName("DP2A")
                .dpId1("5").dpName1("DP1A").fmaDtl1("1")
                .dpId2("").dpName2("").fmaDtl2("")
                .build();
        ValidationSummary summary = new ValidationSummary();
        summary.setChcDetails(list(row));

        InstancedFinding unexpected = new InstancedFinding(vr("r15", "FAIL"), "CFG_CONTROL", 1,
                Kind.UNEXPECTED, Map.of("ID", "5", "SECTION", "0"), null, null, null, null, Map.of());

        annotator.annotate(summary, List.of(), List.of(unexpected), List.of());

        MismatchAnnotation a = byField(row.getMismatches(), "dp_name_1", null);
        assertEquals(Kind.UNEXPECTED, a.getKind());
        assertEquals("DP1A", a.getActual());
        assertEquals("r15", a.getResultId());
    }

    // ---------- data transmission: CFG_DATA_OUT SLCT_TIMEOUT VALUE → timeout column ----------

    @Test
    void dataTransmissionSlctTimeoutValue() {
        DataTransmissionDetail row = DataTransmissionDetail.builder()
                .dpId("5").dpName("DP1A").sourceDpId("6").sourceDpName("DP2B").timeout("620").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setDataTransmissionDetail(list(row));

        ParsedConfigFile file = new ParsedConfigFile("C5", new ArrayList<>(List.of(
                block("CFG_TIMEOUT", 0, entry("TIMEOUT_VALUE", "62")),
                block("CFG_TIMEOUT", 1, entry("TIMEOUT_VALUE", "10")))),
                false, false, true, false, 5);

        InstancedFinding value = new InstancedFinding(vr("r40", "FAIL"), "CFG_DATA_OUT", 5,
                Kind.VALUE, Map.of("ID", "6"), null, "SLCT_TIMEOUT", "0", "1", Map.of());

        annotator.annotate(summary, List.of(), List.of(value), List.of(file));

        MismatchAnnotation a = byField(row.getMismatches(), "timeout", null);
        assertEquals(Kind.VALUE, a.getKind());
        assertEquals("620", a.getExpected()); // CFG_TIMEOUT[0]=62 ×10
        assertEquals("100", a.getActual());   // CFG_TIMEOUT[1]=10 ×10
        assertEquals("r40", a.getResultId());
    }

    // ---------- forwarding: UNEXPECTED (re-resolved index) + MISSING (appended) ----------

    @Test
    void forwardingUnexpectedAndMissing() {
        // home COM 100 forwards CAN_TX_ID 5 and 2, both to COM 200 (socket 0 → NW1 +32 / NW2 +48).
        ParsedConfigFile home = com(100, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.54"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.54"),
                fwrd(0, "5"), fwrd(0, "2"));
        ParsedConfigFile destCom = com(200, "10.1.106.54", "10.2.106.54");
        ParsedConfigFile dp1 = dpFile(1, "DP2A");

        // Expected: 5→200 (present, PASS) and 1→200 (absent, MISSING). Actual 2→200 is the extra.
        List<InstancedExpectation> expectations = List.of(
                InstancedExpectation.byIdentity(100, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "5", "DEST_COM", "200"), "DEST_COM", "200"),
                InstancedExpectation.byIdentity(100, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "1", "DEST_COM", "200"), "DEST_COM", "200"));

        List<ParsedConfigFile> files = List.of(home, destCom, dp1);
        List<InstancedFinding> findings = new InstancedExpectationEvaluator(resolver)
                .evaluateAnnotated(files, expectations);
        idAll(findings); // mimic the service assigning result ids

        EthernetDetail row = EthernetDetail.builder().com("COM100").id("100")
                .fwrdAcdToDpIds(list("5", "2")).fwrdAcdToDpDtls(list("DP1A", "DP3A"))
                .build();
        ValidationSummary summary = new ValidationSummary();
        summary.setEthernetDetails(list(row));

        annotator.annotate(summary, List.of(), findings, files);

        assertEquals(List.of("5", "2", ""), row.getFwrdAcdToDpIds());
        assertEquals(List.of("DP1A", "DP3A", ""), row.getFwrdAcdToDpDtls());

        MismatchAnnotation unexpected = byField(row.getMismatches(), "fwrd_acd_to_dp_dtls", 1);
        assertEquals(Kind.UNEXPECTED, unexpected.getKind());
        assertEquals("DP3A", unexpected.getActual());

        MismatchAnnotation missing = byField(row.getMismatches(), "fwrd_acd_to_dp_dtls", 2);
        assertEquals(Kind.MISSING, missing.getKind());
        assertEquals("DP2A", missing.getExpected()); // dp id 1 → name DP2A
    }

    // ---------- clean run adds nothing ----------

    @Test
    void cleanRunLeavesRowsUnannotated() {
        TrackSectionDetail row = TrackSectionDetail.builder()
                .tsName("1AXT1").fma("1").dpId("5").chDpId(list("5")).chDpName(list("DP1A")).chSlctTimeout(list("0"))
                .iChDpId(list()).iChDpName(list()).iChSlctTimeout(list()).build();
        ValidationSummary summary = new ValidationSummary();
        summary.setTrackSectionDetails(list(row));

        // a PASS finding (kind null) is not annotatable.
        InstancedFinding pass = new InstancedFinding(vr("r0", "PASS"), "CFG_ZP_FMA1", 5,
                null, Map.of("ID", "5"), null, "SLCT_TIMEOUT", "0", "0", Map.of());

        annotator.annotate(summary, List.of(), List.of(pass), List.of());

        assertNull(row.getMismatches());
    }

    // ---------- scalar routes: file-gated, engine-verdict, display-form ----------

    /**
     * A scalar ValidationResult names only a file, so before the gate existed annotateAcoScalar compared
     * the expected value against every ACO row in the project and stamped one file's result_id onto other
     * DPs' rows. DP2 below is wrong in the same column and must stay untouched.
     */
    @Test
    void acoScalarAnnotatesOnlyItsOwnFile() {
        IOEXBAcoDetail mine = IOEXBAcoDetail.builder().dpId("1").dpName("DP2A").aux1Out("3").build();
        IOEXBAcoDetail other = IOEXBAcoDetail.builder().dpId("2").dpName("DP3A").aux1Out("9").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(mine, other));

        annotator.annotate(summary,
                List.of(scalar("r0", "C1.ADC", "CFG_SECTION_OUT", "AUX1_OUT", "5", "3")),
                List.of(), List.of(dpFile(1, "DP2A"), dpFile(2, "DP3A")));

        MismatchAnnotation a = byField(mine.getMismatches(), "aux1_out", null);
        assertEquals("5", a.getExpected());
        assertEquals("3", a.getActual());
        assertEquals("r0", a.getResultId());
        assertNull(other.getMismatches(), "another DP's row must not carry this file's verdict");
    }

    /** A Set.toString() payload from the Optional* rules must not reach the tooltip as bracket form. */
    @Test
    void acoScalarMapsAMultiValuedExpectedPerToken() {
        IOEXBAcoDetail row = IOEXBAcoDetail.builder().dpId("1").dpName("DP2A").aux1Out("3").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(row));

        annotator.annotate(summary,
                List.of(scalar("r1", "C1.ADC", "CFG_SECTION_OUT", "AUX1_OUT", "[3, 4]", "3")),
                List.of(), List.of(dpFile(1, "DP2A")));

        assertEquals("3, 4", byField(row.getMismatches(), "aux1_out", null).getExpected());
    }

    /** A FAIL on a file with no ACO row (CFG_AXCNT-only) annotates nothing at all -- results-only. */
    @Test
    void acoScalarWithNoRowForItsFileAnnotatesNothing() {
        IOEXBAcoDetail other = IOEXBAcoDetail.builder().dpId("2").dpName("DP3A").aux1Out("9").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(other));

        annotator.annotate(summary,
                List.of(scalar("r2", "C1.ADC", "CFG_SECTION_OUT", "AUX1_OUT", "5", "3")),
                List.of(), List.of(dpFile(1, "DP2A"), dpFile(2, "DP3A")));

        assertNull(other.getMismatches());
    }

    /** UAT #2: a supervisor RESET_TYPE verdict must land on the row for its own FMA. */
    @Test
    void supervisorResetTypeScalarLandsOnItsFmaRow() {
        SupervisorDetail fma1 = SupervisorDetail.builder().dpId("1").dpName("DP2A").resetType("3").build();
        SupervisorDetail fma2 = SupervisorDetail.builder().dpId("1").dpName("DP2A").resetType("1").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setSupervisorDetail(list(fma1, fma2));

        annotator.annotate(summary,
                List.of(scalar("r3", "C1.ADC", "CFG_SUPERVIS_FMA2", "RESET_TYPE", "3", "1")),
                List.of(), List.of(supervisorFile(1, "CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2")));

        MismatchAnnotation a = byField(fma2.getMismatches(), "reset_type", null);
        assertEquals("3", a.getExpected());
        assertEquals("1", a.getActual());
        assertNull(fma1.getMismatches(), "the FMA1 row must not carry an FMA2 verdict");
    }

    /** Only one FMA block present means one row; the other FMA's result has no cell and stays quiet. */
    @Test
    void supervisorScalarHandlesASingleFmaBlock() {
        SupervisorDetail only = SupervisorDetail.builder().dpId("1").dpName("DP2A").resetDelay("4").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setSupervisorDetail(list(only));

        annotator.annotate(summary,
                List.of(scalar("r4", "C1.ADC", "CFG_SUPERVIS_FMA2", "RESET_DELAY", "2", "4"),
                        scalar("r5", "C1.ADC", "CFG_SUPERVIS_FMA1", "RESET_DELAY", "2", "4")),
                List.of(), List.of(supervisorFile(1, "CFG_SUPERVIS_FMA2")));

        assertEquals(1, only.getMismatches().size(), "the absent FMA1 must not also annotate this row");
        assertEquals("r4", byField(only.getMismatches(), "reset_delay", null).getResultId());
    }

    /**
     * UAT #5: the four CFG_ZP counts are validated but their only display home is chc_details. The
     * annotation takes the engine's verdict verbatim -- CHCExtractorService stores these three counts raw
     * while value-mappings defines labels for them, so a display re-compare would flag every row.
     */
    @Test
    void chcCountScalarUsesTheEngineVerdict() {
        CHCDetail mine = CHCDetail.builder().dpId("1").dpName("DP2A").supervisCount("2").build();
        CHCDetail other = CHCDetail.builder().dpId("2").dpName("DP3A").supervisCount("2").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setChcDetails(list(mine, other));

        annotator.annotate(summary,
                List.of(scalar("r6", "C1.ADC", "CFG_ZP", "SUPERVIS_COUNT", "5", "2")),
                List.of(), List.of(dpFile(1, "DP2A"), dpFile(2, "DP3A")));

        MismatchAnnotation a = byField(mine.getMismatches(), "supervis_count", null);
        assertEquals("5", a.getExpected());
        assertEquals("2", a.getActual());
        assertNull(other.getMismatches(), "an identical value on another DP must not be flagged");
    }

    /** A scalar block with no detail column of its own is still dropped silently. */
    @Test
    void unroutedScalarBlockLeavesRowsUnannotated() {
        CHCDetail row = CHCDetail.builder().dpId("1").dpName("DP2A").supervisCount("2").build();
        ValidationSummary summary = new ValidationSummary();
        summary.setChcDetails(list(row));

        annotator.annotate(summary,
                List.of(scalar("r7", "C1.ADC", "CFG_RSR_TYPE", "RSR_TYPE", "1", "2")),
                List.of(), List.of(dpFile(1, "DP2A")));

        assertNull(row.getMismatches());
    }

    // ---------- ACO positional: one row per CFG_SECTION_OUT block, joined by slot ----------

    /**
     * Reproduces the real dp 19 layout from the captured production package: an AEB whose four
     * CFG_SECTION_OUT blocks include a card driving {@code F98T} from both of its outputs. Every block now
     * has its own row, so slot 3 annotates the fourth row -- previously the extractor deduped it away and
     * the finding was painted onto slot 2's row, marking a cell whose value is correct.
     */
    @Test
    void acoPositionalFindingLandsOnItsOwnSlotRow() {
        IOEXBAcoDetail r0 = acoRow(0, "2T");
        IOEXBAcoDetail r1 = acoRow(1, "125T");
        IOEXBAcoDetail r2 = acoRow(2, "F98T");
        IOEXBAcoDetail r3 = acoRow(3, "F98T");
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(r0, r1, r2, r3));

        annotator.annotate(summary, List.of(),
                List.of(acoSectionFinding("r20", 3)), List.of(acoFile("2T", "125T", "F98T", "F98T")));

        assertEquals("r20", byField(r3.getMismatches(), "fma_1_2", null).getResultId());
        assertNull(r0.getMismatches());
        assertNull(r1.getMismatches());
        assertNull(r2.getMismatches(), "the card's other output must not be marked");
    }

    /** The two outputs of one card are separate rows, so a finding on the first leaves the second clean. */
    @Test
    void acoFindingOnTheFirstOutputOfACardLeavesTheSecond() {
        IOEXBAcoDetail r0 = acoRow(0, "2T");
        IOEXBAcoDetail r1 = acoRow(1, "125T");
        IOEXBAcoDetail r2 = acoRow(2, "F98T");
        IOEXBAcoDetail r3 = acoRow(3, "F98T");
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(r0, r1, r2, r3));

        annotator.annotate(summary, List.of(),
                List.of(acoSectionFinding("r21", 2)), List.of(acoFile("2T", "125T", "F98T", "F98T")));

        assertEquals("r21", byField(r2.getMismatches(), "fma_1_2", null).getResultId());
        assertNull(r3.getMismatches());
    }

    /** A slot past the configured blocks has no cell; the result stays in validation_results only. */
    @Test
    void acoSlotBeyondTheConfiguredBlocksAnnotatesNothing() {
        IOEXBAcoDetail r0 = acoRow(0, "2T");
        IOEXBAcoDetail r1 = acoRow(1, "125T");
        ValidationSummary summary = new ValidationSummary();
        summary.setIoexbAcoDetails(list(r0, r1));

        annotator.annotate(summary, List.of(),
                List.of(acoSectionFinding("r22", 5)), List.of(acoFile("2T", "125T")));

        assertNull(r0.getMismatches());
        assertNull(r1.getMismatches());
    }

    // ---------- helpers ----------

    private MismatchAnnotation byField(List<MismatchAnnotation> list, String field, Integer index) {
        assertTrue(list != null, "expected _mismatches but row had none");
        return list.stream()
                .filter(a -> field.equals(a.getField()) && java.util.Objects.equals(index, a.getIndex()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no annotation for " + field + "[" + index + "] in " + list));
    }

    /** One ioexb_aco_details row: one per CFG_SECTION_OUT block, carrying its slot. */
    private IOEXBAcoDetail acoRow(int slot, String acoFma1) {
        return IOEXBAcoDetail.builder().dpId("1").dpName("DP2A")
                .slot(String.valueOf(slot)).acoFma1(acoFma1).fma12("1").build();
    }

    /** An ACO host file whose CFG_SECTION_OUT blocks carry the given header comments, in order. */
    private ParsedConfigFile acoFile(String... comments) {
        List<ConfigBlock> blocks = new ArrayList<>();
        for (int i = 0; i < comments.length; i++) {
            blocks.add(block("CFG_SECTION_OUT", i, entryC("CFG_SECTION_OUT", "9", comments[i])));
        }
        return new ParsedConfigFile("C1.ADC", blocks, false, true, false, false, 1);
    }

    private InstancedFinding acoSectionFinding(String id, int position) {
        return new InstancedFinding(vr(id, "FAIL"), "CFG_SECTION_OUT", 1,
                Kind.VALUE, Map.of(), position, "SECTION", "0", "1", Map.of());
    }

    /** A scalar-bucket result: the engine records the file by name only, never by id. */
    private ValidationResult scalar(String id, String fileName, String block, String entry,
            String expected, String actual) {
        ValidationResult r = new ValidationResult(fileName, "InputMatch", block, entry, expected, actual, "FAIL");
        r.setId(id);
        return r;
    }

    /** A DP file carrying the named supervisor blocks, in the order given. */
    private ParsedConfigFile supervisorFile(int id, String... supervisBlocks) {
        List<ConfigBlock> blocks = new ArrayList<>();
        blocks.add(block("ID", 0, entryC("ID", String.valueOf(id), "DP" + id)));
        for (int i = 0; i < supervisBlocks.length; i++) {
            blocks.add(block(supervisBlocks[i], i + 1, entry("RESET_TYPE", "1")));
        }
        return new ParsedConfigFile("C" + id + ".ADC", blocks, true, false, false, false, id);
    }

    private ValidationResult vr(String id, String status) {
        ValidationResult r = new ValidationResult("C.ADC", "IdentitySetMatch", "B", "k", "e", "a", status);
        r.setId(id);
        return r;
    }

    private void idAll(List<InstancedFinding> findings) {
        for (int i = 0; i < findings.size(); i++) {
            findings.get(i).result().setId("f" + i);
        }
    }

    @SafeVarargs
    private <T> List<T> list(T... items) {
        return new ArrayList<>(List.of(items));
    }

    private ParsedConfigFile dpFile(int id, String name) {
        return new ParsedConfigFile("C" + id + ".ADC", new ArrayList<>(List.of(
                block("ID", 0, entryC("ID", String.valueOf(id), name)))),
                false, false, false, false, id);
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

    // ---- COM ADC builders (verified C0391 layout; mirrors ForwardingValidationTest) ----

    private ParsedConfigFile com(int id, String ipNw1, String ipNw2, ConfigBlock... extra) {
        List<ConfigBlock> blocks = new ArrayList<>();
        blocks.add(ipBlock("CFG_MY_IP_NW1", "1", "MY_IP_NW1_B", ipNw1));
        blocks.add(ipBlock("CFG_MY_IP_NW2", "2", "MY_IP_NW2_B", ipNw2));
        for (ConfigBlock b : extra) {
            blocks.add(b);
        }
        return new ParsedConfigFile("C" + id, blocks, false, false, false, true, id);
    }

    private ConfigBlock dest(String name, String bytePrefix, int header, String ip) {
        return ipBlock(name, String.valueOf(header), bytePrefix, ip);
    }

    private ConfigBlock ipBlock(String name, String headerValue, String bytePrefix, String ip) {
        String[] octets = ip.split("\\.");
        ConfigBlock block = new ConfigBlock();
        block.setName(name);
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(name, 8, headerValue, null));
        for (int i = 0; i < 4; i++) {
            entries.add(new ConfigEntry(bytePrefix + (i + 1), 8, octets[i], null));
        }
        block.setEntries(entries);
        return block;
    }

    private ConfigBlock fwrd(int socket, String canTxId) {
        ConfigBlock block = new ConfigBlock();
        block.setName("CFG_FWRD_ACD");
        block.setEntries(new ArrayList<>(List.of(
                new ConfigEntry("CFG_FWRD_ACD", 8, "9", null),
                new ConfigEntry("INT_ID_DEST", 4, String.valueOf(socket), null),
                new ConfigEntry("CAN_TX_ID", 12, canTxId, null))));
        return block;
    }
}
