package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * Unit tests for the CHC derivation (VTF-335 M5 #4 / v2-expectations-contract.md §5.4): signed
 * sensor-set main-vs-combination detection, middle/boundary classification, the 2/1/0 CFG_CONTROL
 * emission, derived BEHAV_INPUT3 (6/7), combination exclusion, and the >2-tracks case (VTF-360:
 * recorded + skipped per DP, not thrown).
 */
class ControlExpectationsBuilderTest {

    private final ControlExpectationsBuilder builder = new ControlExpectationsBuilder();
    private final BaselineInconsistencies problems = new BaselineInconsistencies();

    private List<InstancedExpectation> build(ComAebMap fct, ControlTable ct) {
        return builder.build(ct, BaselineIndex.build(fct, ct, problems), problems);
    }

    /**
     * Linear layout: main 1AXT1 (+DP1A,−DP2A), main 2AXT1 (+DP2A,−DP3A), combination SUP1
     * (+DP1A,−DP3A = 1AXT1+2AXT1). 1AXT1 eval'd by DP10 (chain 0), 2AXT1 by DP20 (chain 1).
     * DP2A is the middle; DP1A/DP3A boundaries (eChc YES / NO).
     */
    @Test
    void emitsMiddleAndBoundaryAndExcludesCombination() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DP1A", "1"),
                        aeb("DP2A", "2"),
                        aeb("DP3A", "3"),
                        aeb("DP10", "10", fma("1AXT1", "0", "10"), fma("SUP1", "1", "10"))),
                chain("COMB", "200",
                        aeb("DP20", "20", fma("2AXT1", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(
                        track("1AXT1", List.of("DP1A"), List.of("DP2A"), "MAIN"),
                        track("2AXT1", List.of("DP2A"), List.of("DP3A"), "MAIN"),
                        track("SUP1", List.of("DP1A"), List.of("DP3A"), "COMBINATION")),
                List.of(dp("DP1A", true), dp("DP2A", false), dp("DP3A", false)));

        List<InstancedExpectation> out = build(fct, ct);

        // Middle DP2A (fileId 2): 2 CFG_CONTROL blocks (unordered) + BEHAV_INPUT3 "6".
        assertEquals("0", control(out, 2, Map.of("ID", "10", "SECTION", "0"))); // ref 1AXT1, same chain
        assertEquals("1", control(out, 2, Map.of("ID", "20", "SECTION", "0"))); // ref 2AXT1, other chain
        assertEquals("6", behav(out, 2));

        // Boundary DP1A (eChc=YES, fileId 1): 1 block + BEHAV_INPUT3 "7". (SUP1 excluded, else DP1A would
        // have 2 plus-owners and throw.)
        assertEquals("0", control(out, 1, Map.of("ID", "10", "SECTION", "0")));
        assertEquals("7", behav(out, 1));

        // Boundary DP3A (eChc=NO, fileId 3): 0 blocks + BEHAV_INPUT3 "6".
        assertEquals("6", behav(out, 3));
        assertEquals(0, controlCount(out, 3));

        assertEquals(3, controlCount(out), "3 CFG_CONTROL blocks total (2 middle + 1 boundary-YES)");
    }

    @Test
    void middleDpSharedByMoreThanTwoMainsIsRecordedAndSkipped() {
        // DP_X is −out of A, +in of B, +in of C → middle with 3 main tracks; the OTHER DPs still derive.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPX", "9"),
                        aeb("DPP", "1"), aeb("DPQ", "2"), aeb("DPR", "3"),
                        aeb("DPA", "10", fma("A", "0", "10")),
                        aeb("DPB", "20", fma("B", "0", "20")),
                        aeb("DPC", "30", fma("C", "0", "30")))));
        ControlTable ct = new ControlTable(
                List.of(
                        track("A", List.of("DPP"), List.of("DPX"), "MAIN"),
                        track("B", List.of("DPX"), List.of("DPQ"), "MAIN"),
                        track("C", List.of("DPX"), List.of("DPR"), "MAIN")),
                List.of(dp("DPX", false), dp("DPP", false), dp("DPQ", false), dp("DPR", false)));

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals(List.of("Middle counting-head DP 'DPX' is shared by more than 2 main tracks"),
                problems.items());
        assertEquals(0, controlCount(out, 9), "the unclassifiable DP emits nothing");
        assertEquals("6", behav(out, 1), "boundary DPP still derives");
    }

    @Test
    void junctionEChcDpIsRecordedAndSkipped() {
        // AD01A-style same-sign junction: +in of two mains with eChc=YES (real ABS topology).
        // Transitional until VTF-361 derives it: recorded + skipped, siblings unaffected.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPJ", "9"), aeb("DPQ", "2"), aeb("DPR", "3"),
                        aeb("DPA", "10", fma("A", "0", "10")),
                        aeb("DPB", "20", fma("B", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(
                        track("A", List.of("DPJ"), List.of("DPQ"), "MAIN"),
                        track("B", List.of("DPJ"), List.of("DPR"), "MAIN")),
                List.of(dp("DPJ", true), dp("DPQ", false), dp("DPR", false)));

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals(List.of(
                "Boundary counting-head DP 'DPJ' (eChc=YES) is not on exactly one main track"),
                problems.items());
        assertEquals(0, controlCount(out, 9));
        assertTrue(out.stream().anyMatch(e -> e.fileId() == 2), "boundary DPQ still derives");
    }

    // --- builders ---

    private Chain chain(String comName, String comId, FctAeb... aebs) {
        return new Chain(new FctCom(comId, comName), false, List.of(aebs));
    }

    private FctAeb aeb(String dpName, String dpId, EvaluatedFma... fmas) {
        return new FctAeb(dpId, dpName, List.of(fmas), List.of(), 0);
    }

    private EvaluatedFma fma(String name, String fmaId, String dpId) {
        return new EvaluatedFma(name, fmaId, dpId);
    }

    private TrackSection track(String name, List<String> dpIn, List<String> dpOut, String trackType) {
        return new TrackSection("1", name, dpIn, dpOut, "", trackType, null, false);
    }

    private DpTableRow dp(String name, boolean eChc) {
        return new DpTableRow("1", name, "ABOVE THE RAIL", eChc);
    }

    private String control(List<InstancedExpectation> out, int fileId, Map<String, String> linkedId) {
        return out.stream()
                .filter(e -> e.matchMode() == MatchMode.BY_IDENTITY
                        && "CFG_CONTROL".equals(e.block())
                        && e.fileId() == fileId
                        && linkedId.equals(e.linkedId())
                        && "SLCT_TIMEOUT".equals(e.key()))
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no CFG_CONTROL for fileId " + fileId + " " + linkedId));
    }

    private long controlCount(List<InstancedExpectation> out, int fileId) {
        return out.stream().filter(e -> "CFG_CONTROL".equals(e.block()) && e.fileId() == fileId).count();
    }

    private long controlCount(List<InstancedExpectation> out) {
        return out.stream().filter(e -> "CFG_CONTROL".equals(e.block())).count();
    }

    private String behav(List<InstancedExpectation> out, int fileId) {
        return out.stream()
                .filter(e -> e.matchMode() == MatchMode.SINGLE
                        && "CFG_AXCNT".equals(e.block())
                        && e.fileId() == fileId
                        && "BEHAV_INPUT3".equals(e.key()))
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no BEHAV_INPUT3 for fileId " + fileId));
    }
}
