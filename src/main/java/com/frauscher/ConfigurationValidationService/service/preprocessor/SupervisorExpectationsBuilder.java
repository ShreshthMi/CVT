package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.FadcAutoReset;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

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
 * <p>A null {@code fadcAutoReset} or a null operator ⇒ no supervisor block (§5.2 requires OR/AND). An
 * operand with no matching FCT FMA is a malformed baseline → {@code PHASE2_BASELINE_INCONSISTENT}.</p>
 */
@Component
public class SupervisorExpectationsBuilder {

    private static final String BLOCK_PREFIX = "CFG_SUPERVIS_FMA";
    private static final String LOGIC_TYPE = "LOGIC_TYPE";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";

    public List<InstancedExpectation> build(ComAebMap fct, ControlTable controlTable) {
        if (fct == null || fct.chains() == null
                || controlTable == null || controlTable.trackSections() == null) {
            return List.of();
        }

        Map<String, EvaluatedFma> fmaByName = fmaByName(fct);
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);

        List<InstancedExpectation> out = new ArrayList<>();
        for (TrackSection track : controlTable.trackSections()) {
            FadcAutoReset fadc = track.fadcAutoReset();
            if (fadc == null || fadc.op() == null) {
                continue; // §5.2: a supervisor block requires an OR/AND operator.
            }
            addTrack(out, track, fadc, fmaByName, chainByDpId);
        }
        return out;
    }

    private void addTrack(
            List<InstancedExpectation> out,
            TrackSection track,
            FadcAutoReset fadc,
            Map<String, EvaluatedFma> fmaByName,
            Map<Integer, Integer> chainByDpId) {

        EvaluatedFma host = fmaByName.get(track.name());
        if (host == null) {
            throw new BaselineInconsistentException(
                    "Track '" + track.name() + "' with a FAdC auto-reset has no matching FCT FMA");
        }
        int fileId = parseId(host.dpId(), "evaluating DP of track " + track.name());
        String block = BLOCK_PREFIX + (parseId(host.fmaId(), "fmaId of track " + track.name()) + 1);
        String logicType = String.valueOf(logicType(fadc.op(), track.name()));
        Integer fileChain = chainByDpId.get(fileId);

        if (fadc.operands() == null) {
            return;
        }
        for (String operand : fadc.operands()) {
            EvaluatedFma ref = fmaByName.get(operand);
            if (ref == null) {
                throw new BaselineInconsistentException(
                        "FAdC auto-reset operand '" + operand + "' of track '" + track.name()
                                + "' has no matching FCT FMA");
            }
            int refId = parseId(ref.dpId(), "evaluating DP of operand " + operand);
            int refSection = parseId(ref.fmaId(), "fmaId of operand " + operand);

            Map<String, String> linkedId = new LinkedHashMap<>();
            linkedId.put(ID, String.valueOf(refId));
            linkedId.put(SECTION, String.valueOf(refSection));

            int slctTimeout = Objects.equals(fileChain, chainByDpId.get(refId)) ? 0 : 1;

            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, LOGIC_TYPE, logicType));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
    }

    private int logicType(String op, String trackName) {
        if ("OR".equalsIgnoreCase(op)) {
            return 0;
        }
        if ("AND".equalsIgnoreCase(op)) {
            return 1;
        }
        throw new BaselineInconsistentException(
                "Unsupported FAdC auto-reset operator '" + op + "' on track '" + trackName + "'");
    }

    private Map<String, EvaluatedFma> fmaByName(ComAebMap fct) {
        Map<String, EvaluatedFma> map = new LinkedHashMap<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.evaluatedFmas() == null) {
                    continue;
                }
                for (EvaluatedFma fma : aeb.evaluatedFmas()) {
                    if (fma.fmaName() != null) {
                        map.putIfAbsent(fma.fmaName(), fma);
                    }
                }
            }
        }
        return map;
    }

    private Map<Integer, Integer> chainByDpId(ComAebMap fct) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        List<com.frauscher.ConfigurationValidationService.dto.fct.Chain> chains = fct.chains();
        for (int i = 0; i < chains.size(); i++) {
            var chain = chains.get(i);
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                map.put(parseId(aeb.dpId(), "DP " + aeb.dpName()), i);
            }
        }
        return map;
    }

    private int parseId(String value, String what) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BaselineInconsistentException("Non-numeric id for " + what + ": " + value);
        }
    }
}
