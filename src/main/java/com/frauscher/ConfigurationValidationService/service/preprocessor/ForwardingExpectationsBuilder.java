package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Derives the {@code CFG_FWRD_ACD} Check-B virtual-forwarding instanced expectations
 * (v2-expectations-contract.md §5.6) — the last BE-05 emission. A COM forwards every counting-head DP it
 * owns to each <em>other</em> COM whose tracks consume that head over the network (the direct, one-hop
 * {@code SLCT_TIMEOUT=1} cross-chain references; no transitive propagation).
 *
 * <p>This is the aggregate other-side of the cross-chain counting-head refs that {@link
 * CountingHeadExpectationsBuilder} emits as {@code SLCT_TIMEOUT=1}: where #1 records the
 * <em>consuming</em> track's view, #6 records the <em>owning</em> COM's forwarding obligation. For each
 * FCT {@link EvaluatedFma} matched to its PDQ {@link TrackSection} by name, the consuming chain is
 * {@code chain(fma.dpId)}. For each head X in {@code dpIn ∪ dpOut} whose chain differs (cross-COM): X's
 * home COM ({@code chain(idOf(X)).com}) must forward X to the consuming track's COM. Tuples are deduped
 * to one per {@code (home COM, source DP, dest COM)}.</p>
 *
 * <p>Emission ({@code MatchMode.BY_IDENTITY}, set-equality in BE-06): {@code fileId = home COM id},
 * block {@code CFG_FWRD_ACD}, {@code linkedId = {CAN_TX_ID: source DP id, DEST_COM: dest COM id}} —
 * both fields identify the member because one DP can be forwarded to several COMs (distinct members).
 * {@code CAN_TX_ID} anchors the identity to the directly-readable ADC entry; {@code DEST_COM} is the
 * attribute BE-06 derives by resolving the entry's {@code INT_ID_DEST} socket → dest IP → present COM,
 * so it is also emitted as the {@code key}/{@code expectedValue}. The socket→COM resolution, IP
 * consistency, and set-equality comparison are BE-06's job (it reads the COM ADC targets); BE-05 only
 * emits the FCT-derived expected set.</p>
 *
 * <p>A head absent from the FCT, or a chain with no addressable COM, is a malformed baseline (§3.3) →
 * {@code PHASE2_BASELINE_INCONSISTENT}.</p>
 */
@Component
public class ForwardingExpectationsBuilder {

    private static final String BLOCK = "CFG_FWRD_ACD";
    private static final String CAN_TX_ID = "CAN_TX_ID";
    private static final String DEST_COM = "DEST_COM";

    public List<InstancedExpectation> build(ComAebMap fct, ControlTable controlTable) {
        if (fct == null || fct.chains() == null || controlTable == null) {
            return List.of();
        }

        Map<String, TrackSection> trackByName = trackByName(controlTable);
        Map<String, Integer> idByDpName = idByDpName(fct);
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);
        List<Chain> chains = fct.chains();

        // Dedup forwarding tuples (home COM, source DP, dest COM) in encounter order.
        Set<Forward> forwards = new LinkedHashSet<>();
        for (Chain chain : chains) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.evaluatedFmas() == null) {
                    continue;
                }
                for (EvaluatedFma fma : aeb.evaluatedFmas()) {
                    collectForwards(forwards, fma, trackByName, idByDpName, chainByDpId, chains);
                }
            }
        }

        List<InstancedExpectation> out = new ArrayList<>(forwards.size());
        for (Forward f : forwards) {
            Map<String, String> linkedId = new LinkedHashMap<>();
            linkedId.put(CAN_TX_ID, String.valueOf(f.sourceDpId()));
            linkedId.put(DEST_COM, String.valueOf(f.destComId()));
            out.add(InstancedExpectation.byIdentity(
                    f.homeComId(), BLOCK, linkedId, DEST_COM, String.valueOf(f.destComId())));
        }
        return out;
    }

    private void collectForwards(
            Set<Forward> forwards,
            EvaluatedFma fma,
            Map<String, TrackSection> trackByName,
            Map<String, Integer> idByDpName,
            Map<Integer, Integer> chainByDpId,
            List<Chain> chains) {

        TrackSection track = trackByName.get(fma.fmaName());
        if (track == null) {
            // The reconciliation gate (§3.1) guarantees a match; defensive.
            throw new BaselineInconsistentException(
                    "FCT FMA '" + fma.fmaName() + "' has no matching PDQ Control Table track");
        }
        int consumingDpId = parseId(fma.dpId(), "evaluating DP of FMA " + fma.fmaName());
        Integer consumingChain = chainByDpId.get(consumingDpId);

        addHeads(forwards, safe(track.dpIn()), consumingChain, idByDpName, chainByDpId, chains, fma.fmaName());
        addHeads(forwards, safe(track.dpOut()), consumingChain, idByDpName, chainByDpId, chains, fma.fmaName());
    }

    private void addHeads(
            Set<Forward> forwards,
            List<String> heads,
            Integer consumingChain,
            Map<String, Integer> idByDpName,
            Map<Integer, Integer> chainByDpId,
            List<Chain> chains,
            String trackName) {

        for (String head : heads) {
            Integer headId = idByDpName.get(head);
            if (headId == null) {
                throw new BaselineInconsistentException(
                        "Counting head '" + head + "' of track '" + trackName
                                + "' has no matching DP in the FCT");
            }
            Integer headChain = chainByDpId.get(headId);
            if (Objects.equals(consumingChain, headChain)) {
                continue; // same COM segment → no forwarding (SLCT_TIMEOUT 0).
            }
            int homeComId = comIdOf(headChain, chains, "home COM of forwarded DP '" + head + "'");
            int destComId = comIdOf(consumingChain, chains, "consuming COM of track '" + trackName + "'");
            forwards.add(new Forward(homeComId, headId, destComId));
        }
    }

    private int comIdOf(Integer chainIndex, List<Chain> chains, String what) {
        if (chainIndex == null || chainIndex < 0 || chainIndex >= chains.size()) {
            throw new BaselineInconsistentException("No chain resolved for " + what);
        }
        FctCom com = chains.get(chainIndex).com();
        if (com == null) {
            throw new BaselineInconsistentException("No COM for " + what);
        }
        return parseId(com.comId(), what);
    }

    private Map<String, TrackSection> trackByName(ControlTable controlTable) {
        Map<String, TrackSection> map = new LinkedHashMap<>();
        if (controlTable.trackSections() != null) {
            for (TrackSection ts : controlTable.trackSections()) {
                if (ts.name() != null) {
                    map.put(ts.name(), ts);
                }
            }
        }
        return map;
    }

    private Map<String, Integer> idByDpName(ComAebMap fct) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Chain chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.dpName() != null) {
                    map.put(aeb.dpName(), parseId(aeb.dpId(), "DP " + aeb.dpName()));
                }
            }
        }
        return map;
    }

    private Map<Integer, Integer> chainByDpId(ComAebMap fct) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        List<Chain> chains = fct.chains();
        for (int i = 0; i < chains.size(); i++) {
            Chain chain = chains.get(i);
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                map.put(parseId(aeb.dpId(), "DP " + aeb.dpName()), i);
            }
        }
        return map;
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private int parseId(String value, String what) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BaselineInconsistentException("Non-numeric id for " + what + ": " + value);
        }
    }

    /** A deduped forwarding obligation: {@code home COM} forwards {@code source DP} to {@code dest COM}. */
    private record Forward(int homeComId, int sourceDpId, int destComId) {
    }
}
