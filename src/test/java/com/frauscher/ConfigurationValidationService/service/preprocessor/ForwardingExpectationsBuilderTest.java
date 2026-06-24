package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * Unit tests for the CFG_FWRD_ACD derivation (VTF-335 M5 #6 / v2-expectations-contract.md §5.6):
 * a home COM forwards each counting-head DP it owns to every other COM whose tracks consume it
 * (cross-chain refs), deduped per (source DP, dest COM). Same-COM heads forward nothing.
 */
class ForwardingExpectationsBuilderTest {

    private final ForwardingExpectationsBuilder builder = new ForwardingExpectationsBuilder();

    @Test
    void crossComHeadForwardsFromHomeComToConsumingCom() {
        // DPA(10) lives on COMA(100). Track TRKB (evaluated by DPB(20) on COMB(200)) consumes DPA.
        // -> COMA forwards DPA to COMB.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100", aeb("DPA", "10")),
                chain("COMB", "200", aeb("DPB", "20", fma("TRKB", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(track("TRKB", List.of("DPA"), List.of())), List.of());

        List<InstancedExpectation> out = builder.build(fct, ct);

        assertEquals(1, out.size());
        InstancedExpectation e = out.get(0);
        assertEquals(100, e.fileId(), "emitted on DPA's home COM");
        assertEquals("CFG_FWRD_ACD", e.block());
        assertEquals(MatchMode.BY_IDENTITY, e.matchMode());
        assertEquals(Map.of("CAN_TX_ID", "10", "DEST_COM", "200"), e.linkedId());
        assertEquals("DEST_COM", e.key());
        assertEquals("200", e.expectedValue());
        assertNull(e.defaultValue(), "forwarding membership is a mandatory match");
    }

    @Test
    void sameChainHeadForwardsNothing() {
        // DPA(10) and the consuming track's DPB(20) are both on COMA(100) -> no cross-COM forward.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10"),
                        aeb("DPB", "20", fma("TRKB", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(track("TRKB", List.of("DPA"), List.of())), List.of());

        assertTrue(builder.build(fct, ct).isEmpty());
    }

    @Test
    void dedupsPerSourceAndDestAndKeepsDistinctDests() {
        // DPS(10) on COMA(100) is consumed by two tracks on COMB(200) (-> one deduped forward to 200)
        // and by one track on COMC(300) (-> a distinct forward to 300). DPS forwarded to {200, 300}.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100", aeb("DPS", "10")),
                chain("COMB", "200",
                        aeb("DPB1", "20", fma("TRKB1", "0", "20")),
                        aeb("DPB2", "21", fma("TRKB2", "0", "21"))),
                chain("COMC", "300", aeb("DPC", "30", fma("TRKC", "0", "30")))));
        ControlTable ct = new ControlTable(List.of(
                track("TRKB1", List.of("DPS"), List.of()),
                track("TRKB2", List.of("DPS"), List.of()),
                track("TRKC", List.of("DPS"), List.of())), List.of());

        List<InstancedExpectation> out = builder.build(fct, ct);

        assertEquals(2, out.size(), "duplicate (DPS,COMB) collapsed; (DPS,COMB) and (DPS,COMC) distinct");
        assertTrue(out.stream().allMatch(e -> e.fileId() == 100
                        && "10".equals(e.linkedId().get("CAN_TX_ID"))),
                "all forwarded from DPS on home COM 100");
        Set<String> dests = out.stream()
                .map(e -> e.linkedId().get("DEST_COM"))
                .collect(Collectors.toSet());
        assertEquals(Set.of("200", "300"), dests);
    }

    @Test
    void headInDpOutForwardsToo() {
        // The cross-COM head can be an out-sensor (dpOut) just as well as an in-sensor.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100", aeb("DPA", "10")),
                chain("COMB", "200", aeb("DPB", "20", fma("TRKB", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(track("TRKB", List.of(), List.of("DPA"))), List.of());

        List<InstancedExpectation> out = builder.build(fct, ct);

        assertEquals(1, out.size());
        assertEquals(Map.of("CAN_TX_ID", "10", "DEST_COM", "200"), out.get(0).linkedId());
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
}
