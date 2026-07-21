package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

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
 * <p>Unresolvable references (VTF-360) are recorded in the {@link BaselineInconsistencies} and skipped
 * at their grain: the FMA when its track/DP does not resolve, the single (track, head) tuple when the
 * head or either COM does not.</p>
 */
@Component
public class ForwardingExpectationsBuilder {

    private static final String BLOCK = "CFG_FWRD_ACD";
    private static final String CAN_TX_ID = "CAN_TX_ID";
    private static final String DEST_COM = "DEST_COM";

    public List<InstancedExpectation> build(ComAebMap fct, BaselineIndex index, BaselineInconsistencies problems) {
        if (fct == null || fct.chains() == null) {
            return List.of();
        }

        // Dedup forwarding tuples (home COM, source DP, dest COM) in encounter order.
        Set<Forward> forwards = new LinkedHashSet<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.evaluatedFmas() == null) {
                    continue;
                }
                for (EvaluatedFma fma : aeb.evaluatedFmas()) {
                    collectForwards(forwards, fma, index, problems);
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
            Set<Forward> forwards, EvaluatedFma fma, BaselineIndex index, BaselineInconsistencies problems) {

        TrackSection track = index.trackByName(fma.fmaName());
        if (track == null) {
            problems.add("FCT FMA '" + fma.fmaName() + "' has no matching PDQ Control Table track");
            return;
        }
        Integer consumingDpId = BaselineIndex.parseId(fma.dpId(), "evaluating DP of FMA " + fma.fmaName(), problems);
        if (consumingDpId == null) {
            return;
        }
        Integer consumingChain = index.chainOfDpId(consumingDpId);

        addHeads(forwards, safe(track.dpIn()), consumingChain, index, problems, fma.fmaName());
        addHeads(forwards, safe(track.dpOut()), consumingChain, index, problems, fma.fmaName());
    }

    private void addHeads(
            Set<Forward> forwards,
            List<String> heads,
            Integer consumingChain,
            BaselineIndex index,
            BaselineInconsistencies problems,
            String trackName) {

        for (String head : heads) {
            Integer headId = index.idOfDp(head);
            if (headId == null) {
                problems.add("Counting head '" + head + "' of track '" + trackName
                        + "' has no matching DP in the FCT");
                continue;
            }
            Integer headChain = index.chainOfDpId(headId);
            if (Objects.equals(consumingChain, headChain)) {
                continue; // same COM segment → no forwarding (SLCT_TIMEOUT 0).
            }
            Integer homeComId = index.comIdOfChain(headChain,
                    "home COM of forwarded DP '" + head + "'", problems);
            Integer destComId = index.comIdOfChain(consumingChain,
                    "consuming COM of track '" + trackName + "'", problems);
            if (homeComId == null || destComId == null) {
                continue; // skip just this tuple; the COM-less segment dedups to one recorded item.
            }
            forwards.add(new Forward(homeComId, headId, destComId));
        }
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    /** A deduped forwarding obligation: {@code home COM} forwards {@code source DP} to {@code dest COM}. */
    private record Forward(int homeComId, int sourceDpId, int destComId) {
    }
}
