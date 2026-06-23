package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.frauscher.ConfigurationValidationService.dto.pdq.FadcAutoReset;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Unit tests for the supervisor derivation (VTF-335 M5 #2 / v2-expectations-contract.md §5.2): one
 * CFG_SUPERVIS_FMA block per fadcAutoReset operand, linkedID=(ID,SECTION) of the operand's FCT FMA,
 * LOGIC_TYPE from the operator, SLCT_TIMEOUT from chain membership, and the null/inconsistent guards.
 */
class SupervisorExpectationsBuilderTest {

    private final SupervisorExpectationsBuilder builder = new SupervisorExpectationsBuilder();

    @Test
    void emitsOneBlockPerOperandWithLogicTypeAndTimeout() {
        // Host 1AXT1 on DP10 (chain 0, FMA1). Operands: 1AXT2 on DP20 (same chain), SUP1 on DP30 (other chain, FMA2).
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DP10", "10", fma("1AXT1", "0", "10")),
                        aeb("DP20", "20", fma("1AXT2", "0", "20"))),
                chain("COMB", "200",
                        aeb("DP30", "30", fma("SUP1", "1", "30")))));
        ControlTable ct = new ControlTable(
                List.of(track("1AXT1", new FadcAutoReset("OR", List.of("1AXT2", "SUP1")))),
                List.of());

        List<InstancedExpectation> out = builder.build(fct, ct);

        assertEquals(4, out.size(), "2 operands x (LOGIC_TYPE + SLCT_TIMEOUT)");
        // 1AXT2 -> (ID 20, SECTION 0), same chain -> SLCT_TIMEOUT 0; OR -> LOGIC_TYPE 0.
        assertEquals("0", value(out, "CFG_SUPERVIS_FMA1", Map.of("ID", "20", "SECTION", "0"), "LOGIC_TYPE"));
        assertEquals("0", value(out, "CFG_SUPERVIS_FMA1", Map.of("ID", "20", "SECTION", "0"), "SLCT_TIMEOUT"));
        // SUP1 -> (ID 30, SECTION 1), other chain -> SLCT_TIMEOUT 1.
        assertEquals("0", value(out, "CFG_SUPERVIS_FMA1", Map.of("ID", "30", "SECTION", "1"), "LOGIC_TYPE"));
        assertEquals("1", value(out, "CFG_SUPERVIS_FMA1", Map.of("ID", "30", "SECTION", "1"), "SLCT_TIMEOUT"));
    }

    @Test
    void andOperatorGivesLogicTypeOne() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DP10", "10", fma("1AXT1", "0", "10")),
                        aeb("DP20", "20", fma("1AXT2", "0", "20")))));
        ControlTable ct = new ControlTable(
                List.of(track("1AXT1", new FadcAutoReset("AND", List.of("1AXT2")))),
                List.of());

        List<InstancedExpectation> out = builder.build(fct, ct);

        assertEquals("1", value(out, "CFG_SUPERVIS_FMA1", Map.of("ID", "20", "SECTION", "0"), "LOGIC_TYPE"));
    }

    @Test
    void nullFadcOrNullOperatorEmitsNothing() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100", aeb("DP10", "10", fma("1AXT1", "0", "10")))));
        ControlTable noFadc = new ControlTable(List.of(track("1AXT1", null)), List.of());
        ControlTable bareOperand = new ControlTable(
                List.of(track("1AXT1", new FadcAutoReset(null, List.of("1AXT2")))), List.of());

        assertTrue(builder.build(fct, noFadc).isEmpty());
        assertTrue(builder.build(fct, bareOperand).isEmpty(), "op==null (single bare operand) emits no block");
    }

    @Test
    void operandWithoutFctFmaIsInconsistent() {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100", aeb("DP10", "10", fma("1AXT1", "0", "10")))));
        ControlTable ct = new ControlTable(
                List.of(track("1AXT1", new FadcAutoReset("OR", List.of("GHOST", "ALSOGHOST")))),
                List.of());

        assertThrows(BaselineInconsistentException.class, () -> builder.build(fct, ct));
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

    private TrackSection track(String name, FadcAutoReset fadc) {
        return new TrackSection("1", name, List.of(), List.of(), "", "MAIN", fadc, false);
    }

    private String value(List<InstancedExpectation> list, String block, Map<String, String> linkedId, String key) {
        return list.stream()
                .filter(e -> e.matchMode() == MatchMode.BY_IDENTITY
                        && block.equals(e.block())
                        && linkedId.equals(e.linkedId())
                        && key.equals(e.key()))
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no expectation for " + block + " " + linkedId + " " + key));
    }
}
