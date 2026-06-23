package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;
import com.frauscher.ConfigurationValidationService.exception.ControlTableMissingException;
import com.frauscher.ConfigurationValidationService.exception.TrackReconciliationFailedException;

/**
 * Unit tests for the Phase 2 baseline gate (VTF-335 M2 / v2-expectations-contract.md §3), exercising
 * the failure paths the happy-path fixtures don't reach: track NOT-FOUND / EXTRA, duplicate PDQ track,
 * missing Control Table, and the RSR_TYPE cross-check.
 */
class BaselineGateTest {

    private final BaselineGate gate = new BaselineGate();

    @Test
    void reconcilingBaselineWithConsistentRsrPasses() {
        assertDoesNotThrow(() -> gate.check(input(pdq(List.of("259BT", "262AT"), "1"), fct("259BT", "262AT"))));
    }

    @Test
    void pdqTrackAbsentFromFctIsNotFound() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(pdq(List.of("259BT", "999XX"), "1"), fct("259BT"))));
        assertEquals(List.of("999XX"), ex.getNotFoundTracks());
        assertTrue(ex.getExtraTracks().isEmpty());
        assertTrue(ex.getMessage().contains("999XX"), "message should name the missing track");
    }

    @Test
    void fctTrackNotConsumedByPdqIsExtra() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(pdq(List.of("259BT"), "1"), fct("259BT", "262AT"))));
        assertEquals(List.of("262AT"), ex.getExtraTracks());
        assertTrue(ex.getNotFoundTracks().isEmpty());
        assertTrue(ex.getMessage().contains("262AT"), "message should name the extra track");
    }

    @Test
    void duplicateTrackNameInPdqIsInconsistent() {
        BaselineInconsistentException ex = assertThrows(BaselineInconsistentException.class,
                () -> gate.check(input(pdq(List.of("259BT", "259BT"), "1"), fct("259BT"))));
        assertTrue(ex.getMessage().contains("259BT"));
    }

    @Test
    void nullControlTableIsMissing() {
        PdqUploadResponse pdq = PdqUploadResponse.builder()
                .cqIrParameters(Map.of("CFG_RSR_TYPE", Map.of("RSR_TYPE", "1")))
                .build();
        assertThrows(ControlTableMissingException.class, () -> gate.check(input(pdq, fct("259BT"))));
    }

    @Test
    void emptyTrackSectionsIsMissing() {
        PdqUploadResponse pdq = PdqUploadResponse.builder()
                .controlTable(new ControlTable(List.of(), List.of()))
                .cqIrParameters(Map.of("CFG_RSR_TYPE", Map.of("RSR_TYPE", "1")))
                .build();
        assertThrows(ControlTableMissingException.class, () -> gate.check(input(pdq, fct("259BT"))));
    }

    @Test
    void rsrTypeMismatchBetweenPdqAndTpfIsInconsistent() {
        ValidationInputV2 in = input(pdq(List.of("259BT"), "1"), fct("259BT"));
        in.addTpfSection("CFG_RSR_TYPE", Map.of("RSR_TYPE", "3"));
        BaselineInconsistentException ex = assertThrows(BaselineInconsistentException.class,
                () -> gate.check(in));
        assertTrue(ex.getMessage().contains("RSR_TYPE"));
    }

    @Test
    void rsrTypeAbsentFromBothPdqAndTpfIsInconsistent() {
        assertThrows(BaselineInconsistentException.class,
                () -> gate.check(input(pdq(List.of("259BT"), null), fct("259BT"))));
    }

    @Test
    void rsrTypeFromTpfOnlyIsAccepted() {
        ValidationInputV2 in = input(pdq(List.of("259BT"), null), fct("259BT"));
        in.addTpfSection("CFG_RSR_TYPE", Map.of("RSR_TYPE", "1"));
        assertDoesNotThrow(() -> gate.check(in));
    }

    // --- builders ---

    private ValidationInputV2 input(PdqUploadResponse pdq, ComAebMap fct) {
        ValidationInputV2 in = new ValidationInputV2();
        in.setPdqData(pdq);
        in.setFctData(fct);
        return in;
    }

    private ComAebMap fct(String... fmaNames) {
        List<EvaluatedFma> fmas = java.util.Arrays.stream(fmaNames)
                .map(n -> new EvaluatedFma(n, "0", "1"))
                .toList();
        FctAeb aeb = new FctAeb("1", "DP", fmas, List.of(), 0);
        return new ComAebMap(List.of(new Chain(new FctCom("1", "COM"), false, List.of(aeb))));
    }

    private PdqUploadResponse pdq(List<String> trackNames, String rsrType) {
        List<TrackSection> sections = trackNames.stream()
                .map(n -> new TrackSection("1", n, List.of(), List.of(), "", "MAIN", null, false))
                .toList();
        Map<String, Map<String, Object>> cqIr = (rsrType == null)
                ? Map.of()
                : Map.of("CFG_RSR_TYPE", Map.of("RSR_TYPE", rsrType));
        return PdqUploadResponse.builder()
                .controlTable(new ControlTable(sections, List.of()))
                .cqIrParameters(cqIr)
                .build();
    }
}
