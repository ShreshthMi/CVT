package com.frauscher.ConfigurationValidationService.validation.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation.Kind;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedFinding;

/**
 * Cluster 1 (VTF-338) Check-A named verdicts ({@link CanSegmentValidator}, design §8.1): SLCT_TIMEOUT
 * mismatch → INVALID SCOPE (2–7) / INVALID VALUE; an unexpected occurrence whose referenced AEB id is
 * unknown → FILE NOT FOUND; and an uploaded AEB ADC absent from the FCT segments → ORPHANED.
 */
class CanSegmentValidatorTest {

    private final CanSegmentValidator validator = new CanSegmentValidator();
    private final ComAebMap fct = new ComAebMap(List.of(
            new Chain(new FctCom("100", "COM100"), false, List.of(
                    new FctAeb("5", "DP1A", List.of(), List.of(), 0),
                    new FctAeb("6", "DP2B", List.of(), List.of(), 0)))));

    @Test
    void slctTimeoutActual2to7BecomesInvalidScope() {
        InstancedFinding f = slct("5", "0", "3"); // expected 0, actual 3 (out of binary scope)
        validator.apply(List.of(f), fct, List.of());
        assertEquals(ValidationConstants.INVALID_SCOPE, f.result().getActualValue());
        assertEquals("INVALID", f.result().getStatus());
    }

    @Test
    void slctTimeoutWrongBinaryBecomesInvalidValue() {
        InstancedFinding f = slct("5", "1", "0"); // expected 1, actual 0 (in scope, wrong)
        validator.apply(List.of(f), fct, List.of());
        assertEquals(ValidationConstants.INVALID_VALUE, f.result().getActualValue());
        assertEquals("INVALID", f.result().getStatus());
    }

    @Test
    void unexpectedUnknownReferenceBecomesFileNotFound() {
        InstancedFinding f = unexpected("999"); // 999 is not a known AEB
        validator.apply(List.of(f), fct, List.of());
        assertEquals(ValidationConstants.FILE_NOT_FOUND, f.result().getActualValue());
        assertEquals("FAIL", f.result().getStatus());
    }

    @Test
    void unexpectedKnownReferenceStaysUnexpected() {
        InstancedFinding f = unexpected("6"); // 6 IS a known AEB — just not expected here
        validator.apply(List.of(f), fct, List.of());
        assertEquals(ValidationConstants.UNEXPECTED_OCCURRENCE, f.result().getActualValue());
    }

    @Test
    void orphanedAebFileIsEmitted() {
        ParsedConfigFile known = aebFile("C0005_00.ADC", 5);
        ParsedConfigFile orphan = aebFile("C1234_00.ADC", 1234);
        ParsedConfigFile com = comFile("C0100_00.ADC", 777); // COM ids are never AEB-orphaned

        List<ValidationResult> orphaned = validator.apply(List.of(), fct, List.of(known, orphan, com));

        assertEquals(1, orphaned.size());
        ValidationResult r = orphaned.get(0);
        assertEquals("C1234_00.ADC", r.getFileName());
        assertEquals(ValidationConstants.ORPHANED, r.getActualValue());
        assertEquals("FAIL", r.getStatus());
        assertTrue(orphaned.stream().noneMatch(o -> "C0005_00.ADC".equals(o.getFileName())));
    }

    // ---- helpers ----

    private InstancedFinding slct(String id, String expected, String actual) {
        ValidationResult r = new ValidationResult("C0005_00.ADC", "IdentitySetMatch", "CFG_ZP_FMA1",
                "SLCT_TIMEOUT[ID=" + id + "]", expected, actual, "FAIL");
        return new InstancedFinding(r, "CFG_ZP_FMA1", 5, Kind.VALUE, Map.of("ID", id), null,
                "SLCT_TIMEOUT", expected, actual, Map.of());
    }

    private InstancedFinding unexpected(String id) {
        ValidationResult r = new ValidationResult("C0005_00.ADC", "IdentitySetMatch", "CFG_ZP_FMA1",
                "ID=" + id, ValidationConstants.UNEXPECTED_OCCURRENCE, ValidationConstants.UNEXPECTED_OCCURRENCE, "FAIL");
        return new InstancedFinding(r, "CFG_ZP_FMA1", 5, Kind.UNEXPECTED, Map.of("ID", id), null,
                null, null, null, Map.of());
    }

    private ParsedConfigFile aebFile(String name, int id) {
        return new ParsedConfigFile(name, List.of(), false, false, false, false, id);
    }

    private ParsedConfigFile comFile(String name, int id) {
        return new ParsedConfigFile(name, List.of(), false, false, false, true, id);
    }
}
