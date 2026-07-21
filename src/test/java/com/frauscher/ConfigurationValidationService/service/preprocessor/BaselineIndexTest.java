package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * The shared builder index (VTF-360): trim-normalized lookups (the gate trims, so must we) and
 * per-entry-tolerant id parsing (one malformed dpId no longer poisons the whole run — it is recorded
 * once and only its own entries are skipped).
 */
class BaselineIndexTest {

    @Test
    void probesAndKeysAreTrimNormalized() {
        // Trailing-space PDQ names + padded FCT names — the exact shape the gate admits (it trims).
        ComAebMap fct = new ComAebMap(List.of(new Chain(new FctCom("100", "COM100"), false, List.of(
                new FctAeb("5", " DP1A ", List.of(new EvaluatedFma(" 1AXT1", "0", "5")), List.of(), 0)))));
        ControlTable ct = new ControlTable(
                List.of(new TrackSection("1", "1AXT1 ", List.of("DP1A"), List.of(), null, "MAIN", null, false)),
                List.of(new DpTableRow("1", "DP1A ", "ABOVE THE RAIL", true)));

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, ct, problems);

        assertTrue(problems.isEmpty());
        assertEquals(5, index.idOfDp("DP1A"));
        assertEquals(5, index.idOfDp(" DP1A "));
        assertEquals(0, index.chainOfDpId(5));
        assertEquals("1AXT1", index.fmaByName("1AXT1 ").fmaName().trim());
        assertEquals("1AXT1", index.trackByName(" 1AXT1").name().trim());
        assertEquals("ABOVE THE RAIL", index.positionOfDp("DP1A"));
        assertEquals(Boolean.TRUE, index.eChcOfDp(" DP1A"));
    }

    @Test
    void malformedDpIdIsRecordedOnceAndOnlyItsEntriesSkipped() {
        ComAebMap fct = new ComAebMap(List.of(new Chain(new FctCom("100", "COM100"), false, List.of(
                new FctAeb("bad", "DP1A", List.of(), List.of(), 0),
                new FctAeb("7", "DP2A", List.of(), List.of(), 0)))));

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, null, problems);

        assertEquals(List.of("Non-numeric id for DP DP1A: bad"), problems.items());
        assertNull(index.idOfDp("DP1A"));
        assertEquals(7, index.idOfDp("DP2A")); // the healthy sibling survives
        assertEquals(0, index.chainOfDpId(7));
    }

    @Test
    void comIdOfChainRecordsRootCauseKeyedProblems() {
        ComAebMap fct = new ComAebMap(List.of(new Chain(null, false, List.of())));
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, null, problems);

        assertNull(index.comIdOfChain(null, "home COM of DP 'X'", problems));
        assertNull(index.comIdOfChain(0, "home COM of DP 'X'", problems));
        assertNull(index.comIdOfChain(0, "home COM of DP 'Y'", problems)); // same root cause → dedups

        assertEquals(List.of(
                "No chain resolved for home COM of DP 'X'",
                "CAN segment 0 has no addressable COM"), problems.items());
    }
}
