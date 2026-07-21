package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;

/**
 * Derives the {@code CFG_IP_SWITCH} per-COM instanced expectation (v2-expectations-contract.md §5.5).
 * One {@code SINGLE} expectation per FCT chain, {@code fileId = chain.com.comId}, no {@code linkedId}:
 * <ul>
 *   <li>{@code redundantComPresent == true} → mandatory {@code IP_SWITCH = "1"} (absent or ≠1 → FAIL).</li>
 *   <li>{@code false} → optional {@code IP_SWITCH = "0"} with default {@code "0"} (absent → PASS — the
 *       usual case for a non-redundant COM; present must be 0, present 1 → FAIL). Emitted (not omitted)
 *       so a stray {@code IP_SWITCH=1} in a non-redundant COM is caught.</li>
 * </ul>
 * A non-numeric COM id (VTF-360) records the problem and skips that COM's expectation.
 */
@Component
public class IpSwitchExpectationsBuilder {

    private static final String BLOCK = "CFG_IP_SWITCH";
    private static final String IP_SWITCH = "IP_SWITCH";

    public List<InstancedExpectation> build(ComAebMap fct, BaselineInconsistencies problems) {
        if (fct == null || fct.chains() == null) {
            return List.of();
        }
        List<InstancedExpectation> out = new ArrayList<>();
        for (var chain : fct.chains()) {
            FctCom com = chain.com();
            if (com == null || com.comId() == null) {
                continue;
            }
            Integer comId = BaselineIndex.parseId(com.comId(), "COM id of " + com.comName(), problems);
            if (comId == null) {
                continue;
            }
            if (chain.redundantComPresent()) {
                out.add(InstancedExpectation.single(comId, BLOCK, IP_SWITCH, "1"));
            } else {
                out.add(InstancedExpectation.singleOptional(comId, BLOCK, IP_SWITCH, "0", "0"));
            }
        }
        return out;
    }
}
