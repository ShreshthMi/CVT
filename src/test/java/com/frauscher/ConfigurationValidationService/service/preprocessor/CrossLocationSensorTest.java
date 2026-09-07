package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
 * A block section runs between two stations, so its far counting head belongs to the neighbour: one
 * physical DP that each side names in its own location. The sensor vectors are therefore keyed by the
 * DP's <b>resolved id</b>, not by (location, name) — keying by the naming location would split that one
 * head into two half-sensors that never cancel, and the shared boundary would be classified as a plain
 * boundary at each end instead of the middle DP it actually is: two {@code CFG_CONTROL} blocks lost and
 * {@code BEHAV_INPUT3} flipped, with nothing reported.
 */
class CrossLocationSensorTest {

    private static final String RESET = "RESTRICTED RESET WITH LV";

    private final ControlExpectationsBuilder builder =
            new ControlExpectationsBuilder(new ResetOutMappingService());

    @Test
    void aDpSharedAcrossTwoLocationsIsOneSensorAndReadsAsMiddle() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct(), controlTable(), problems);

        List<InstancedExpectation> out = builder.build(controlTable(), index, problems);

        // DP9 is net-out for the STN1 track and net-in for the STN2 one: a middle DP, so two CFG_CONTROL
        // blocks naming its two adjacent tracks, and BEHAV_INPUT3 = 6 rather than a boundary's 7.
        assertEquals(2, blocksOn(out, 9, "CFG_CONTROL").size(),
                "the shared head is one sensor with a main track either side of it");
        assertTrue(problems.items().isEmpty(), "nothing unresolvable here: " + problems.items());
    }

    /** The far head resolves through the project-wide fallback: STN1 names a DP that only STN2 holds. */
    @Test
    void theFarHeadResolvesToTheNeighbouringStationsDp() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct(), controlTable(), problems);

        assertEquals(9, index.idOfDp("STN1", "DP9"));
        assertEquals(9, index.idOfDp("STN2", "DP9"));
        assertTrue(problems.items().isEmpty());
    }

    /** Each end still keeps its own DPs distinct — the shared head is the exception, not the rule. */
    @Test
    void eachLocationsOwnHeadsStayDistinct() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct(), controlTable(), problems);

        assertEquals(1, index.idOfDp("STN1", "DP1"));
        assertEquals(2, index.idOfDp("STN2", "DP2"));
    }

    private List<InstancedExpectation> blocksOn(List<InstancedExpectation> out, int fileId, String block) {
        return out.stream().filter(e -> e.fileId() == fileId && block.equals(e.block())).toList();
    }

    // --- builders ---

    /**
     * STN1 runs DP1 → DP9, STN2 runs DP9 → DP2. DP9 is the boundary between them and exists once in the
     * FCT, under STN2.
     */
    private ControlTable controlTable() {
        return new ControlTable(
                List.of(
                        new TrackSection("1", "T1", List.of("DP1"), List.of("DP9"), RESET, "MAIN", null, false, "STN1"),
                        new TrackSection("1", "T2", List.of("DP9"), List.of("DP2"), RESET, "MAIN", null, false, "STN2")),
                List.of(
                        new DpTableRow("1", "DP1", "ABOVE THE RAIL", false, "STN1"),
                        new DpTableRow("2", "DP9", "ABOVE THE RAIL", false, "STN2"),
                        new DpTableRow("3", "DP2", "ABOVE THE RAIL", false, "STN2")));
    }

    private ComAebMap fct() {
        return new ComAebMap(List.of(
                new Chain(new FctCom("100", "COM100", "STN1"), false, List.of(
                        new FctAeb("1", "DP1", List.of(new EvaluatedFma("T1", "0", "1")), List.of(), 0))),
                new Chain(new FctCom("200", "COM200", "STN2"), false, List.of(
                        new FctAeb("2", "DP2", List.of(new EvaluatedFma("T2", "0", "2")), List.of(), 0),
                        new FctAeb("9", "DP9", List.of(), List.of(), 0)))));
    }
}
