package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.FadcAutoReset;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * VTF-360 end-to-end preprocessor behavior: a gate-passing baseline with several distinct post-gate
 * defects is rejected ONCE with every problem enumerated in the message (accumulate-then-reject,
 * vtf-360-design.md §2); a clean baseline preprocesses without throwing.
 */
class DefaultExpectationsPreprocessorTest {

    private final DefaultExpectationsPreprocessor preprocessor = new DefaultExpectationsPreprocessor(
            new BaselineGate(),
            new ScalarExpectationsBuilder(),
            new CountingHeadExpectationsBuilder(),
            new SupervisorExpectationsBuilder(),
            new AcoExpectationsBuilder(),
            new ControlExpectationsBuilder(new ResetOutMappingService()),
            new IpSwitchExpectationsBuilder(),
            new ForwardingExpectationsBuilder(),
            new DataTransmissionExpectationsBuilder());

    @Test
    void enumeratesEveryPostGateProblemInOneRejection() {
        // Gate passes (tracks reconcile: {TRK1}; RSR present) but the baseline carries 4 distinct
        // post-gate defects across different builders:
        //   1. AEB 'DPBAD' has a non-numeric dpId          (shared index)
        //   2. TRK1's dpIn head 'DPGHOST' is not in the FCT (counting-head + forwarding — dedups)
        //   3. same DP, Control's own wording                (control builder)
        //   4. TRK1 has an unsupported auto-reset operator   (supervisor builder)
        ComAebMap fct = new ComAebMap(List.of(new Chain(new FctCom("100", "COMA"), false, List.of(
                new FctAeb("10", "DP10", List.of(new EvaluatedFma("TRK1", "0", "10")), List.of(), 0),
                new FctAeb("xx", "DPBAD", List.of(), List.of(), 0),
                new FctAeb("11", "DP11", List.of(), List.of(), 0)))));
        ControlTable ct = new ControlTable(
                List.of(new TrackSection("1", "TRK1", List.of("DPGHOST"), List.of("DP11"), "",
                        "MAIN", new FadcAutoReset("XOR", List.of("TRK1")), false)),
                List.of(new DpTableRow("1", "DP11", "ABOVE THE RAIL", false),
                        new DpTableRow("2", "DPGHOST", "ABOVE THE RAIL", false)));

        BaselineInconsistentException ex = assertThrows(BaselineInconsistentException.class,
                () -> preprocessor.preprocess(input(fct, ct)));

        assertEquals("Baseline inconsistent — 4 problems: "
                + "1) Non-numeric id for DP DPBAD: xx; "
                + "2) Counting head 'DPGHOST' of track 'TRK1' has no matching DP in the FCT; "
                + "3) Unsupported FAdC auto-reset operator 'XOR' on track 'TRK1'; "
                + "4) Counting-head DP 'DPGHOST' has no matching DP in the FCT",
                ex.getMessage());
    }

    @Test
    void cleanBaselinePreprocessesWithoutThrowing() {
        ComAebMap fct = new ComAebMap(List.of(new Chain(new FctCom("100", "COMA"), false, List.of(
                new FctAeb("10", "DP10", List.of(new EvaluatedFma("TRK1", "0", "10")), List.of(), 0),
                new FctAeb("11", "DP11", List.of(), List.of(), 0),
                new FctAeb("12", "DP12", List.of(), List.of(), 0)))));
        ControlTable ct = new ControlTable(
                List.of(new TrackSection("1", "TRK1", List.of("DP11"), List.of("DP12"), "",
                        "MAIN", null, false)),
                List.of(new DpTableRow("1", "DP11", "ABOVE THE RAIL", false),
                        new DpTableRow("2", "DP12", "BELOW THE RAIL", false)));

        assertDoesNotThrow(() -> preprocessor.preprocess(input(fct, ct)));
    }

    private ValidationInputV2 input(ComAebMap fct, ControlTable ct) {
        ValidationInputV2 in = new ValidationInputV2();
        in.setFctData(fct);
        in.setPdqData(PdqUploadResponse.builder()
                .cqIrParameters(Map.of("CFG_RSR_TYPE", Map.of("RSR_TYPE", "1")))
                .controlTable(ct)
                .build());
        return in;
    }
}
