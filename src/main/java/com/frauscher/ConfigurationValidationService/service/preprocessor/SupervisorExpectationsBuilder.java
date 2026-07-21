package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.FadcAutoReset;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * Derives the {@code CFG_SUPERVIS_FMA1}/{@code CFG_SUPERVIS_FMA2} supervisor instanced expectations
 * (v2-expectations-contract.md §5.2). Source = the Control-Table {@code fadcAutoReset} column: the
 * evaluating DP's file gets <b>one supervisor block per operand</b>, on the host track's own FMA.
 *
 * <p>Per track T (matched to its FCT {@link EvaluatedFma} by name): {@code fileId = host.dpId},
 * {@code block = CFG_SUPERVIS_FMA{host.fmaId + 1}}, {@code LOGIC_TYPE = op} (OR=0 / AND=1, constant
 * across T's occurrences). Per operand R (a track/supervisor name, resolved uniformly via the FCT —
 * no classification): {@code linkedId = {ID: R.dpId, SECTION: R.fmaId}} (0-based), {@code SLCT_TIMEOUT
 * = chain(R) == chain(host) ? 0 : 1}. {@code RESET_TYPE}/{@code RESET_DELAY} are scalar (the cqIR
 * CFG_SUPERVIS_FMA* values in M3's bucket), not emitted here.</p>
 *
 * <p>A null {@code fadcAutoReset} or a null operator ⇒ no supervisor block (§5.2 requires OR/AND).
 * Unresolvable references (VTF-360): an unresolvable host or an unsupported operator skips the whole
 * track, an unresolvable operand skips just that operand — each recorded in the
 * {@link BaselineInconsistencies}.</p>
 */
@Component
public class SupervisorExpectationsBuilder {

    private static final String BLOCK_PREFIX = "CFG_SUPERVIS_FMA";
    private static final String LOGIC_TYPE = "LOGIC_TYPE";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";

    public List<InstancedExpectation> build(
            ControlTable controlTable, BaselineIndex index, BaselineInconsistencies problems) {
        if (controlTable == null || controlTable.trackSections() == null) {
            return List.of();
        }

        List<InstancedExpectation> out = new ArrayList<>();
        for (TrackSection track : controlTable.trackSections()) {
            FadcAutoReset fadc = track.fadcAutoReset();
            if (fadc == null || fadc.op() == null) {
                continue; // §5.2: a supervisor block requires an OR/AND operator.
            }
            addTrack(out, track, fadc, index, problems);
        }
        return out;
    }

    private void addTrack(
            List<InstancedExpectation> out,
            TrackSection track,
            FadcAutoReset fadc,
            BaselineIndex index,
            BaselineInconsistencies problems) {

        EvaluatedFma host = index.fmaByName(track.name());
        if (host == null) {
            problems.add("Track '" + track.name() + "' with a FAdC auto-reset has no matching FCT FMA");
            return;
        }
        Integer fileId = BaselineIndex.parseId(host.dpId(), "evaluating DP of track " + track.name(), problems);
        Integer hostFmaId = BaselineIndex.parseId(host.fmaId(), "fmaId of track " + track.name(), problems);
        Integer logicType = logicType(fadc.op(), track.name(), problems);
        if (fileId == null || hostFmaId == null || logicType == null) {
            return; // skip the whole track; LOGIC_TYPE / the host coordinates are underivable.
        }
        String block = BLOCK_PREFIX + (hostFmaId + 1);
        Integer fileChain = index.chainOfDpId(fileId);

        if (fadc.operands() == null) {
            return;
        }
        for (String operand : fadc.operands()) {
            EvaluatedFma ref = index.fmaByName(operand);
            if (ref == null) {
                problems.add("FAdC auto-reset operand '" + operand + "' of track '" + track.name()
                        + "' has no matching FCT FMA");
                continue;
            }
            Integer refId = BaselineIndex.parseId(ref.dpId(), "evaluating DP of operand " + operand, problems);
            Integer refSection = BaselineIndex.parseId(ref.fmaId(), "fmaId of operand " + operand, problems);
            if (refId == null || refSection == null) {
                continue; // skip this operand only.
            }

            Map<String, String> linkedId = new LinkedHashMap<>();
            linkedId.put(ID, String.valueOf(refId));
            linkedId.put(SECTION, String.valueOf(refSection));

            int slctTimeout = Objects.equals(fileChain, index.chainOfDpId(refId)) ? 0 : 1;

            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, LOGIC_TYPE, String.valueOf(logicType)));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
    }

    private Integer logicType(String op, String trackName, BaselineInconsistencies problems) {
        if ("OR".equalsIgnoreCase(op)) {
            return 0;
        }
        if ("AND".equalsIgnoreCase(op)) {
            return 1;
        }
        problems.add("Unsupported FAdC auto-reset operator '" + op + "' on track '" + trackName + "'");
        return null;
    }
}
