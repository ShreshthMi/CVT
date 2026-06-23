package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;

import lombok.RequiredArgsConstructor;

/**
 * The real {@link ExpectationsPreprocessor}. Runs the {@link BaselineGate} (hard HTTP 400 admission
 * control, v2-expectations-contract.md §3) and then builds the {@link Expectations} buckets. The
 * scalar bucket (§4) lands in M3 and the instanced bucket (§5) in M5; until then the gate runs and an
 * empty {@link Expectations} is returned (emission-only — BE-06 consumes).
 */
@Service
@RequiredArgsConstructor
public class DefaultExpectationsPreprocessor implements ExpectationsPreprocessor {

    private final BaselineGate baselineGate;
    private final ScalarExpectationsBuilder scalarExpectationsBuilder;
    private final CountingHeadExpectationsBuilder countingHeadExpectationsBuilder;
    private final SupervisorExpectationsBuilder supervisorExpectationsBuilder;

    @Override
    public Expectations preprocess(ValidationInputV2 userInput) {
        baselineGate.check(userInput);

        Map<String, Map<String, Object>> scalarExpectations =
                scalarExpectationsBuilder.build(userInput);

        ComAebMap fct = userInput.getFctData();
        ControlTable controlTable = userInput.getPdqData().getControlTable();

        List<InstancedExpectation> instancedExpectations = new ArrayList<>();
        instancedExpectations.addAll(countingHeadExpectationsBuilder.build(fct, controlTable));
        instancedExpectations.addAll(supervisorExpectationsBuilder.build(fct, controlTable));
        // TODO(VTF-335 M5): ACO, CHC, IP_SWITCH, forwarding derivations.

        return new Expectations(scalarExpectations, instancedExpectations);
    }
}
