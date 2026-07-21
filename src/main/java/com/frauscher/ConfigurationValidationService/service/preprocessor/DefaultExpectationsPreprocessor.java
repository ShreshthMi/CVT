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
 * control, v2-expectations-contract.md §3) and then builds the {@link Expectations} buckets: the scalar
 * bucket (§4) and the full instanced bucket (§5 — counting heads, supervisors, ACO, control, IP switch,
 * forwarding). Emission-only — BE-06 consumes the expectations against the actual ADC values.
 *
 * <p>VTF-360: the builders share one trim-normalized {@link BaselineIndex} and record every
 * unresolvable reference into a {@link BaselineInconsistencies} collector instead of first-throwing —
 * the end-check then rejects with ONE {@code PHASE2_BASELINE_INCONSISTENT} enumerating the complete
 * problem list (vtf-371-design.md §2). A clean baseline proceeds unchanged.</p>
 */
@Service
@RequiredArgsConstructor
public class DefaultExpectationsPreprocessor implements ExpectationsPreprocessor {

    private final BaselineGate baselineGate;
    private final ScalarExpectationsBuilder scalarExpectationsBuilder;
    private final CountingHeadExpectationsBuilder countingHeadExpectationsBuilder;
    private final SupervisorExpectationsBuilder supervisorExpectationsBuilder;
    private final AcoExpectationsBuilder acoExpectationsBuilder;
    private final ControlExpectationsBuilder controlExpectationsBuilder;
    private final IpSwitchExpectationsBuilder ipSwitchExpectationsBuilder;
    private final ForwardingExpectationsBuilder forwardingExpectationsBuilder;
    private final DataTransmissionExpectationsBuilder dataTransmissionExpectationsBuilder;

    @Override
    public Expectations preprocess(ValidationInputV2 userInput) {
        baselineGate.check(userInput);

        Map<String, Map<String, Object>> scalarExpectations =
                scalarExpectationsBuilder.build(userInput);

        ComAebMap fct = userInput.getFctData();
        ControlTable controlTable = userInput.getPdqData().getControlTable();

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, controlTable, problems);

        List<InstancedExpectation> instancedExpectations = new ArrayList<>();
        instancedExpectations.addAll(countingHeadExpectationsBuilder.build(fct, index, problems));
        instancedExpectations.addAll(supervisorExpectationsBuilder.build(controlTable, index, problems));
        instancedExpectations.addAll(acoExpectationsBuilder.build(fct, index, problems));
        instancedExpectations.addAll(controlExpectationsBuilder.build(controlTable, index, problems));
        instancedExpectations.addAll(ipSwitchExpectationsBuilder.build(fct, problems));
        instancedExpectations.addAll(forwardingExpectationsBuilder.build(fct, index, problems));
        instancedExpectations.addAll(dataTransmissionExpectationsBuilder.build(
                userInput.getPdqData().getDataTransmission(), index, problems));

        // VTF-360 end-check: reject once, with every accumulated problem, or proceed clean.
        problems.throwIfAny();

        return new Expectations(scalarExpectations, instancedExpectations);
    }
}
