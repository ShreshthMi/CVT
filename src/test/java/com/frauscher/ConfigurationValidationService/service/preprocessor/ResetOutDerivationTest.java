package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * {@code CFG_SECTION.RESET_OUT} is derived from the Control table's Reset Type column, not read from the
 * CQ-IR — {@code fcvt-phase2-design.md} §361 and {@code pdq-upload-contract-v1.4.wiki} §130 both say so and
 * both call its CQ-IR row a meta row. The derivation was specified and never built:
 * {@code TrackSection.resetType} was parsed and read by nothing, so no expectation existed, the
 * {@code OptionalInputMatchOrBlockNotFound} rule short-circuited on an absent payload, and UAT saw no
 * RESET_OUT results at all — not a FAIL, not even a PASS.
 *
 * <p>The axis differs from the rest of the CHC derivation: {@code CFG_SECTION} lives on the AEB that
 * <b>evaluates</b> a track, so tracks are grouped by their FCT {@code EvaluatedFma} owner rather than by
 * counting-head DP.</p>
 */
class ResetOutDerivationTest {

    private static final String WITH_LV = "RESTRICTED RESET WITH LV";                       // -> 1
    private static final String PREP_WITHOUT_LV = "RESTRICTED PREPARATORY RESET WITHOUT LV"; // -> 2
    private static final String PREP_WITH_LV = "RESTRICTED PREPARATORY RESET WITH LV";       // -> 3

    private final ControlExpectationsBuilder builder =
            new ControlExpectationsBuilder(new ResetOutMappingService());
    private final BaselineInconsistencies problems = new BaselineInconsistencies();

    // ---------- the catalog ----------

    @Test
    void catalogMapsTheThreeAeSuppliedResetTypes() {
        ResetOutMappingService mappings = new ResetOutMappingService();
        assertEquals("1", mappings.codeFor(WITH_LV));
        assertEquals("2", mappings.codeFor(PREP_WITHOUT_LV));
        assertEquals("3", mappings.codeFor(PREP_WITH_LV));
    }

    /** Column E is free text passed through verbatim, so the lookup absorbs casing and spacing drift. */
    @Test
    void catalogLookupIsCaseAndWhitespaceInsensitive() {
        ResetOutMappingService mappings = new ResetOutMappingService();
        assertEquals("2", mappings.codeFor("  restricted   preparatory reset WITHOUT lv  "));
        assertNull(mappings.codeFor("SOMETHING ELSE"));
        assertNull(mappings.codeFor("   "));
        assertNull(mappings.codeFor(null));
    }

    // ---------- the derivation ----------

    @Test
    void emitsResetOutForTheEvaluatingAeb() {
        List<InstancedExpectation> out = build(PREP_WITH_LV, PREP_WITH_LV);

        assertTrue(problems.isEmpty(), () -> "unexpected problems: " + problems.items());
        assertEquals("3", resetOut(out, 10), "the evaluating AEB gets the code, not the counting-head DP");
        assertNull(resetOut(out, 1), "a counting-head DP evaluates nothing, so it gets no RESET_OUT");
    }

    /** One AEB, two evaluated tracks: RESET_OUT is one value per file, so they must agree. */
    @Test
    void differingResetTypesOnOneAebAreABaselineDefect() {
        List<InstancedExpectation> out = build(WITH_LV, PREP_WITHOUT_LV);

        assertNull(resetOut(out, 10), "an unresolvable RESET_OUT must not be guessed");
        assertTrue(problems.items().stream().anyMatch(i -> i.contains("differing Reset")),
                () -> "expected a differing-Reset-Type problem, got " + problems.items());
    }

    @Test
    void aResetTypeOutsideTheCatalogIsABaselineDefect() {
        List<InstancedExpectation> out = build("RESTRICTED SOMETHING NEW", "RESTRICTED SOMETHING NEW");

        assertNull(resetOut(out, 10));
        assertTrue(problems.items().stream().anyMatch(i -> i.contains("No RESET_OUT mapping")),
                () -> "expected a catalog-miss problem, got " + problems.items());
    }

    /**
     * A named track section with a blank Reset Type is rejected at parse time
     * ({@code ControlTableParserTest}), so by the time the derivation runs a blank can only come from an
     * unnamed row. It emits nothing rather than inventing a code.
     */
    @Test
    void aBlankResetTypeEmitsNothing() {
        List<InstancedExpectation> out = build("", "");

        assertNull(resetOut(out, 10));
        assertTrue(problems.isEmpty(), () -> "no expectation, and nothing to report: " + problems.items());
    }

    // ---------- fixture ----------

    /**
     * Two main tracks, both evaluated by AEB DP10, each carrying its own Reset Type. DP1A/DP2A/DP3A are the
     * counting heads; they evaluate nothing and so own no CFG_SECTION.
     */
    private List<InstancedExpectation> build(String resetType1, String resetType2) {
        ComAebMap fct = new ComAebMap(List.of(
                chain("COMA", "100",
                        aeb("DP1A", "1"),
                        aeb("DP2A", "2"),
                        aeb("DP3A", "3"),
                        aeb("DP10", "10", fma("1AXT1", "0", "10"), fma("2AXT1", "1", "10")))));
        ControlTable ct = new ControlTable(
                List.of(
                        track("1AXT1", List.of("DP1A"), List.of("DP2A"), resetType1),
                        track("2AXT1", List.of("DP2A"), List.of("DP3A"), resetType2)),
                List.of(dp("DP1A", true), dp("DP2A", false), dp("DP3A", false)));

        return builder.build(ct, BaselineIndex.build(fct, ct, problems), problems);
    }

    /** The derived CFG_SECTION.RESET_OUT for a file, or {@code null} when none was emitted. */
    private String resetOut(List<InstancedExpectation> out, int fileId) {
        return out.stream()
                .filter(e -> "CFG_SECTION".equals(e.block()) && "RESET_OUT".equals(e.key())
                        && e.fileId() == fileId)
                .map(InstancedExpectation::expectedValue)
                .findFirst()
                .orElse(null);
    }

    private TrackSection track(String name, List<String> dpIn, List<String> dpOut, String resetType) {
        return new TrackSection("1", name, dpIn, dpOut, resetType, "MAIN", null, false);
    }

    private DpTableRow dp(String name, boolean eChc) {
        return new DpTableRow("1", name, "ABOVE THE RAIL", eChc);
    }

    private Chain chain(String comName, String comId, FctAeb... aebs) {
        return new Chain(new FctCom(comId, comName), false, List.of(aebs));
    }

    private FctAeb aeb(String dpName, String dpId, EvaluatedFma... fmas) {
        return new FctAeb(dpId, dpName, List.of(fmas),
                List.of(new AcoIoExb("ACO", null, null, null, null, null, null)), 0);
    }

    private EvaluatedFma fma(String name, String fmaId, String dpId) {
        return new EvaluatedFma(name, fmaId, dpId);
    }
}
