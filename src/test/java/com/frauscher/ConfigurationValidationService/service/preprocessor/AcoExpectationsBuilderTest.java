package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;

/**
 * Unit tests for the ACO derivation (VTF-335 M5 #3 / v2-expectations-contract.md §5.3): POSITIONAL
 * CFG_SECTION_OUT blocks, 2 per card, slot-2 = filler duplicate of track-1 when single-track,
 * validating ID(=aco_fmaId)+SECTION+SLCT_TIMEOUT at each slot.
 */
class AcoExpectationsBuilderTest {

    private final AcoExpectationsBuilder builder = new AcoExpectationsBuilder();

    @Test
    void emitsTwoPositionsPerCardWithFillerForSingleTrack() {
        // Host DPA(10, chain 0). Card 1: single-track -> output (10,0); slot-2 = filler dup.
        // Card 2: dual-track -> (20,0) same chain + (30,1) other chain.
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DPA", "10",
                                card("T1", "0", "10", null, null, null),
                                card("T2", "0", "20", "T3", "1", "30")),
                        aeb("DP20", "20")),
                chain("COMB", "200",
                        aeb("DP30", "30"))));

        List<InstancedExpectation> out = builder.build(fct);

        assertEquals(4 * 3, out.size(), "2 cards x 2 slots x (ID + SECTION + SLCT_TIMEOUT)");

        // Position 0 = card1 track-1: ID 10, SECTION 0, same chain -> SLCT 0.
        assertEquals("10", value(out, 0, "ID"));
        assertEquals("0", value(out, 0, "SECTION"));
        assertEquals("0", value(out, 0, "SLCT_TIMEOUT"));
        // Position 1 = card1 filler: identical to position 0.
        assertEquals("10", value(out, 1, "ID"));
        assertEquals("0", value(out, 1, "SECTION"));
        // Position 2 = card2 track-1: ID 20, same chain -> SLCT 0.
        assertEquals("20", value(out, 2, "ID"));
        assertEquals("0", value(out, 2, "SLCT_TIMEOUT"));
        // Position 3 = card2 track-2: ID 30, SECTION 1, other chain -> SLCT 1.
        assertEquals("30", value(out, 3, "ID"));
        assertEquals("1", value(out, 3, "SECTION"));
        assertEquals("1", value(out, 3, "SLCT_TIMEOUT"));
    }

    @Test
    void aebWithoutAcoEmitsNothing() {
        ComAebMap fct = new ComAebMap(List.of(chain("COMA", "100", aeb("DPA", "10"))));
        assertEquals(0, builder.build(fct).size());
    }

    // --- builders ---

    private Chain chain(String comName, String comId, FctAeb... aebs) {
        return new Chain(new FctCom(comId, comName), false, List.of(aebs));
    }

    private FctAeb aeb(String dpName, String dpId, AcoIoExb... acos) {
        return new FctAeb(dpId, dpName, List.of(), List.of(acos), 0);
    }

    private AcoIoExb card(String o1Name, String o1Id, String o1Dp, String o2Name, String o2Id, String o2Dp) {
        return new AcoIoExb("ACO", o1Name, o1Id, o1Dp, o2Name, o2Id, o2Dp);
    }

    private String value(List<InstancedExpectation> list, int position, String key) {
        return list.stream()
                .filter(e -> e.matchMode() == MatchMode.POSITIONAL
                        && "CFG_SECTION_OUT".equals(e.block())
                        && e.position() != null && e.position() == position
                        && key.equals(e.key()))
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no expectation at position " + position + " key " + key));
    }
}
