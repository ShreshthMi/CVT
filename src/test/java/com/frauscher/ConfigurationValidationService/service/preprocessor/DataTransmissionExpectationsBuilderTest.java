package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataSafetyLevel;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataTransmission;
import com.frauscher.ConfigurationValidationService.dto.pdq.OutputDataTransmission;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Cluster 1 (VTF-338) CFG_DATA_OUT expectations ({@link DataTransmissionExpectationsBuilder}): each PDQ DT
 * row (receiving DP paired with source DP by index) yields a BY_IDENTITY SLCT_TIMEOUT expectation on the
 * receiving AEB, 0 same-segment / 1 different-segment; misaligned sub-tables or an unresolvable DP → 400.
 */
class DataTransmissionExpectationsBuilderTest {

    private final DataTransmissionExpectationsBuilder builder = new DataTransmissionExpectationsBuilder();

    // segment 0 = {DP1A=5, DP2B=6}; segment 1 = {DP3A=7}
    private final ComAebMap fct = new ComAebMap(List.of(
            new Chain(new FctCom("100", "COM100"), false, List.of(
                    new FctAeb("5", "DP1A", List.of(), List.of(), 0),
                    new FctAeb("6", "DP2B", List.of(), List.of(), 0))),
            new Chain(new FctCom("200", "COM200"), false, List.of(
                    new FctAeb("7", "DP3A", List.of(), List.of(), 0)))));

    @Test
    void sameSegmentSourceIsZeroDifferentSegmentIsOne() {
        DataTransmission dt = new DataTransmission(
                List.of(level("DP1A"), level("DP1A")),
                List.of(output("DP2B"), output("DP3A")));

        List<InstancedExpectation> exp = builder.build(fct, dt);

        assertEquals(2, exp.size());
        // DP1A (seg 0) ← DP2B (seg 0) → physical, SLCT_TIMEOUT 0
        InstancedExpectation same = find(exp, "6");
        assertEquals(5, same.fileId());
        assertEquals("CFG_DATA_OUT", same.block());
        assertEquals(MatchMode.BY_IDENTITY, same.matchMode());
        assertEquals("SLCT_TIMEOUT", same.key());
        assertEquals("0", same.expectedValue());
        // DP1A (seg 0) ← DP3A (seg 1) → virtual, SLCT_TIMEOUT 1
        assertEquals("1", find(exp, "7").expectedValue());
    }

    @Test
    void misalignedSubTablesAreRejected() {
        DataTransmission dt = new DataTransmission(
                List.of(level("DP1A"), level("DP1A")),
                List.of(output("DP2B")));
        assertThrows(BaselineInconsistentException.class, () -> builder.build(fct, dt));
    }

    @Test
    void unresolvableSourceDpIsRejected() {
        DataTransmission dt = new DataTransmission(
                List.of(level("DP1A")),
                List.of(output("DP_UNKNOWN")));
        assertThrows(BaselineInconsistentException.class, () -> builder.build(fct, dt));
    }

    @Test
    void nullOrEmptyDtYieldsNothing() {
        assertTrue(builder.build(fct, null).isEmpty());
        assertTrue(builder.build(fct, new DataTransmission(List.of(), List.of())).isEmpty());
    }

    private InstancedExpectation find(List<InstancedExpectation> exp, String sourceId) {
        return exp.stream().filter(e -> sourceId.equals(e.linkedId().get("ID"))).findFirst().orElseThrow();
    }

    private DataSafetyLevel level(String dpName) {
        return new DataSafetyLevel(dpName, "3", "3", "0");
    }

    private OutputDataTransmission output(String sourceDpName) {
        return new OutputDataTransmission(sourceDpName, "2", "5");
    }
}
