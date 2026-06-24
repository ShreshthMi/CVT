package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;

/**
 * Unit tests for the CFG_IP_SWITCH derivation (VTF-335 M5 #5 / v2-expectations-contract.md §5.5):
 * one per-COM SINGLE expectation — mandatory "1" when redundant, optional "0"/default-"0" when not.
 */
class IpSwitchExpectationsBuilderTest {

    private final IpSwitchExpectationsBuilder builder = new IpSwitchExpectationsBuilder();

    @Test
    void redundantComExpectsMandatoryOne() {
        ComAebMap fct = new ComAebMap(List.of(
                new Chain(new FctCom("100", "COM100"), true, List.of())));

        List<InstancedExpectation> out = builder.build(fct);

        assertEquals(1, out.size());
        InstancedExpectation e = out.get(0);
        assertEquals(100, e.fileId());
        assertEquals("CFG_IP_SWITCH", e.block());
        assertEquals(MatchMode.SINGLE, e.matchMode());
        assertEquals("IP_SWITCH", e.key());
        assertEquals("1", e.expectedValue());
        assertNull(e.defaultValue(), "redundant case is a mandatory match");
    }

    @Test
    void nonRedundantComExpectsOptionalZero() {
        ComAebMap fct = new ComAebMap(List.of(
                new Chain(new FctCom("200", "COM200"), false, List.of())));

        List<InstancedExpectation> out = builder.build(fct);

        assertEquals(1, out.size());
        InstancedExpectation e = out.get(0);
        assertEquals(200, e.fileId());
        assertEquals("0", e.expectedValue());
        assertEquals("0", e.defaultValue(), "non-redundant case is optional (absent COM block → PASS)");
    }

    @Test
    void emitsOnePerChain() {
        ComAebMap fct = new ComAebMap(List.of(
                new Chain(new FctCom("100", "COM100"), true, List.of()),
                new Chain(new FctCom("200", "COM200"), false, List.of())));

        assertEquals(2, builder.build(fct).size());
    }
}
