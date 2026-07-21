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
 * Unit tests for the counting-head derivation (VTF-335 M5 #1 / v2-expectations-contract.md §5.1):
 * one BY_IDENTITY DIR_INV+SLCT_TIMEOUT pair per head, every EvaluatedFma (incl. combination tracks),
 * and the §3.3 malformed-baseline cases (VTF-360: recorded + skipped per head, not thrown).
 */
class CountingHeadExpectationsBuilderTest {

    private final CountingHeadExpectationsBuilder builder = new CountingHeadExpectationsBuilder();
    private final BaselineInconsistencies problems = new BaselineInconsistencies();

    private List<InstancedExpectation> build(ComAebMap fct, ControlTable ct) {
        return builder.build(fct, BaselineIndex.build(fct, ct, problems), problems);
    }

    @Test
    void emitsDirInvAndSlctTimeoutPerHead() {
        // DPA(10) evaluates TRK1 as FMA1; heads DPB(11, in, same chain) + DPC(20, out, other chain).
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("TRK1", "0", "10")),
                        aeb("DPB", "11"),
                        aeb("DPA2", "12")),
                chain("COMB", "200",
                        aeb("DPC", "20"))));
        ControlTable ct = new ControlTable(
                List.of(track("TRK1", List.of("DPB"), List.of("DPC"))),
                List.of(dp("DPB", "ABOVE THE RAIL"), dp("DPC", "BELOW THE RAIL")));

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals(4, out.size(), "2 heads x (DIR_INV + SLCT_TIMEOUT)");
        // DPB: in (isOut=false) + ABOVE (isBelow=false) -> DIR_INV 0; same chain -> SLCT_TIMEOUT 0.
        assertEquals("0", value(out, "CFG_ZP_FMA1", "11", "DIR_INV"));
        assertEquals("0", value(out, "CFG_ZP_FMA1", "11", "SLCT_TIMEOUT"));
        // DPC: out (isOut=true) + BELOW (isBelow=true) -> DIR_INV 0; other chain -> SLCT_TIMEOUT 1.
        assertEquals("0", value(out, "CFG_ZP_FMA1", "20", "DIR_INV"));
        assertEquals("1", value(out, "CFG_ZP_FMA1", "20", "SLCT_TIMEOUT"));
    }

    @Test
    void dirInvIsOneWhenDirectionAndRailDisagree() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("TRK1", "1", "10")),
                        aeb("DPB", "11"))));
        // DPB in dpIn (IN) but BELOW THE RAIL -> disagree -> DIR_INV 1. FMA2 (fmaId 1) -> CFG_ZP_FMA2.
        ControlTable ct = new ControlTable(
                List.of(track("TRK1", List.of("DPB"), List.of())),
                List.of(dp("DPB", "BELOW THE RAIL")));

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals("1", value(out, "CFG_ZP_FMA2", "11", "DIR_INV"));
    }

    @Test
    void emitsForCombinationTrackToo() {
        // A combination/supervisor FMA is still an EvaluatedFma with sensors -> gets CFG_ZP_FMA.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("SUP1", "0", "10")),
                        aeb("DPB", "11"))));
        ControlTable ct = new ControlTable(
                List.of(track("SUP1", List.of("DPB"), List.of())),
                List.of(dp("DPB", "ABOVE THE RAIL")));

        List<InstancedExpectation> out = build(fct, ct);

        assertTrue(out.stream().anyMatch(e -> e.block().equals("CFG_ZP_FMA1")
                && e.linkedId().equals(Map.of("ID", "11"))));
    }

    @Test
    void headAbsentFromDpTableIsRecordedAndSkipped() {
        // DPB missing from the dpTable, DPC healthy: the run continues, DPC still derives.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("TRK1", "0", "10")),
                        aeb("DPB", "11"),
                        aeb("DPC", "12"))));
        ControlTable ct = new ControlTable(
                List.of(track("TRK1", List.of("DPB"), List.of("DPC"))),
                List.of(dp("DPC", "ABOVE THE RAIL"))); // DPB not in dpTable

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals(List.of("Counting head 'DPB' of track 'TRK1' is absent from the PDQ DP table"),
                problems.items());
        assertEquals(2, out.size(), "the healthy head DPC still derives its pair");
        assertEquals("1", value(out, "CFG_ZP_FMA1", "12", "DIR_INV")); // out + ABOVE -> disagree
    }

    @Test
    void headAbsentFromFctIsRecordedAndSkipped() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("TRK1", "0", "10")))));
        ControlTable ct = new ControlTable(
                List.of(track("TRK1", List.of("DPGHOST"), List.of())),
                List.of(dp("DPGHOST", "ABOVE THE RAIL"))); // in dpTable but no FCT DP

        List<InstancedExpectation> out = build(fct, ct);

        assertEquals(List.of("Counting head 'DPGHOST' of track 'TRK1' has no matching DP in the FCT"),
                problems.items());
        assertTrue(out.isEmpty());
    }

    @Test
    void multipleProblemsAccumulateInOneRun() {
        // Ghost head + malformed dpId on another AEB: both facts collected, nothing thrown here.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10", fma("TRK1", "0", "10")),
                        aeb("DPBAD", "notanumber"))));
        ControlTable ct = new ControlTable(
                List.of(track("TRK1", List.of("DPGHOST"), List.of())),
                List.of(dp("DPGHOST", "ABOVE THE RAIL")));

        build(fct, ct);

        assertEquals(List.of(
                "Non-numeric id for DP DPBAD: notanumber",
                "Counting head 'DPGHOST' of track 'TRK1' has no matching DP in the FCT"),
                problems.items());
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

    private TrackSection track(String name, List<String> dpIn, List<String> dpOut) {
        return new TrackSection("1", name, dpIn, dpOut, "", "MAIN", null, false);
    }

    private DpTableRow dp(String name, String position) {
        return new DpTableRow("1", name, position, false);
    }

    private String value(List<InstancedExpectation> list, String block, String linkedId, String key) {
        return list.stream()
                .filter(e -> e.matchMode() == MatchMode.BY_IDENTITY
                        && block.equals(e.block())
                        && e.linkedId().equals(Map.of("ID", linkedId))
                        && key.equals(e.key()))
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no expectation for " + block + " ID=" + linkedId + " " + key));
    }
}
