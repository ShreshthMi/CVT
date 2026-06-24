package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Derives the {@code CFG_CONTROL} (CHC) instanced expectations + the derived
 * {@code CFG_AXCNT.BEHAV_INPUT3} (v2-expectations-contract.md §5.4). The first derivation needing real
 * set arithmetic over the Control Table.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Signed sensor vector per track: {@code +1} per {@code dpIn} name, {@code −1} per {@code dpOut}
 *       (names → ids deferred to emission; classification works in names).</li>
 *   <li><b>Main vs combination</b> by signed-set arithmetic ({@code trackType} is NOT trusted): a track
 *       is a combination iff its vector equals the cancellation-sum of a subset (≥2) of the other
 *       tracks' vectors (non-negative {0,1} sums — a linear span would misclassify mains). Combination
 *       (virtual/supervisor) tracks are excluded from the CHC.</li>
 *   <li>Over <b>main</b> tracks only, tally each DP's signs: a DP that is net-in for one main and
 *       net-out for another → <b>middle</b> (shared boundary); single-sign → <b>boundary</b>.</li>
 *   <li>Emit per DP ({@code fileId} = that DP): middle → <b>2</b> {@code CFG_CONTROL} (BY_IDENTITY,
 *       unordered) referencing the 2 adjacent main tracks; boundary + {@code eChc=YES} → <b>1</b>;
 *       boundary + {@code eChc=NO} → <b>0</b>. Per block {@code linkedId = (ID=evalDP, SECTION=FMA)} of
 *       the referenced track (via FCT), value {@code SLCT_TIMEOUT} = same/diff COM chain. Plus the
 *       derived {@code CFG_AXCNT.BEHAV_INPUT3} = {@code "7"} iff boundary+{@code eChc=YES}, else
 *       {@code "6"}.</li>
 * </ol>
 * A middle DP shared by &gt;2 mains, an unresolved DP/track, or an {@code eChc=YES} boundary not on
 * exactly one main → {@code PHASE2_BASELINE_INCONSISTENT} (§3.3 build-time gate).
 */
@Component
public class ControlExpectationsBuilder {

    private static final String CONTROL = "CFG_CONTROL";
    private static final String AXCNT = "CFG_AXCNT";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String BEHAV_INPUT3 = "BEHAV_INPUT3";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";
    /** Generous bound on a combination's constituent count (real combinations are 2–3 mains). */
    private static final int MAX_COMBINATION_SIZE = 8;

    public List<InstancedExpectation> build(ComAebMap fct, ControlTable controlTable) {
        if (fct == null || fct.chains() == null
                || controlTable == null || controlTable.trackSections() == null) {
            return List.of();
        }

        List<TrackSection> tracks = controlTable.trackSections();
        Map<String, Integer> idByDpName = idByDpName(fct);
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);
        Map<String, EvaluatedFma> fmaByName = fmaByName(fct);
        Map<String, Boolean> eChcByDpName = eChcByDpName(controlTable);

        List<Map<String, Integer>> vectors = new ArrayList<>(tracks.size());
        for (TrackSection t : tracks) {
            vectors.add(signedVector(t));
        }

        // main = NOT expressible as a {0,1}-sum (≥2) of the other tracks' signed vectors.
        boolean[] isMain = new boolean[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            isMain[i] = !isCombination(i, vectors);
        }

        // Over mains only: per DP name, the main-track indices where it is net-in (+) and net-out (−).
        Map<String, List<Integer>> plusByDp = new LinkedHashMap<>();
        Map<String, List<Integer>> minusByDp = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            if (!isMain[i]) {
                continue;
            }
            for (Map.Entry<String, Integer> e : vectors.get(i).entrySet()) {
                Map<String, List<Integer>> target = e.getValue() > 0 ? plusByDp : minusByDp;
                target.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(i);
            }
        }

        Set<String> dpNames = new LinkedHashSet<>();
        dpNames.addAll(plusByDp.keySet());
        dpNames.addAll(minusByDp.keySet());

        List<InstancedExpectation> out = new ArrayList<>();
        for (String dpName : dpNames) {
            addForDp(out, dpName, plusByDp, minusByDp, tracks, idByDpName, chainByDpId, fmaByName, eChcByDpName);
        }
        return out;
    }

    private void addForDp(
            List<InstancedExpectation> out,
            String dpName,
            Map<String, List<Integer>> plusByDp,
            Map<String, List<Integer>> minusByDp,
            List<TrackSection> tracks,
            Map<String, Integer> idByDpName,
            Map<Integer, Integer> chainByDpId,
            Map<String, EvaluatedFma> fmaByName,
            Map<String, Boolean> eChcByDpName) {

        List<Integer> plus = plusByDp.getOrDefault(dpName, List.of());
        List<Integer> minus = minusByDp.getOrDefault(dpName, List.of());

        Integer fileId = idByDpName.get(dpName);
        if (fileId == null) {
            throw new BaselineInconsistentException(
                    "Counting-head DP '" + dpName + "' has no matching DP in the FCT");
        }
        Integer thisChain = chainByDpId.get(fileId);

        boolean middle = !plus.isEmpty() && !minus.isEmpty();
        if (middle) {
            if (plus.size() + minus.size() > 2) {
                throw new BaselineInconsistentException(
                        "Middle counting-head DP '" + dpName + "' is shared by more than 2 main tracks");
            }
            // exactly one + and one − main track → the 2 adjacent tracks.
            addControlBlock(out, fileId, thisChain, tracks.get(plus.get(0)), fmaByName, chainByDpId);
            addControlBlock(out, fileId, thisChain, tracks.get(minus.get(0)), fmaByName, chainByDpId);
            out.add(InstancedExpectation.single(fileId, AXCNT, BEHAV_INPUT3, "6"));
            return;
        }

        // boundary (single sign)
        List<Integer> owners = plus.isEmpty() ? minus : plus;
        boolean eChcYes = Boolean.TRUE.equals(eChcByDpName.get(dpName));
        if (eChcYes) {
            if (owners.size() != 1) {
                throw new BaselineInconsistentException(
                        "Boundary counting-head DP '" + dpName + "' (eChc=YES) is not on exactly one main track");
            }
            addControlBlock(out, fileId, thisChain, tracks.get(owners.get(0)), fmaByName, chainByDpId);
            out.add(InstancedExpectation.single(fileId, AXCNT, BEHAV_INPUT3, "7"));
        } else {
            out.add(InstancedExpectation.single(fileId, AXCNT, BEHAV_INPUT3, "6"));
        }
    }

    private void addControlBlock(
            List<InstancedExpectation> out,
            int fileId,
            Integer thisChain,
            TrackSection refTrack,
            Map<String, EvaluatedFma> fmaByName,
            Map<Integer, Integer> chainByDpId) {

        EvaluatedFma ref = fmaByName.get(refTrack.name());
        if (ref == null) {
            throw new BaselineInconsistentException(
                    "Adjacent track '" + refTrack.name() + "' has no matching FCT FMA");
        }
        int refDpId = parseId(ref.dpId(), "evaluating DP of track " + refTrack.name());
        String section = ref.fmaId() == null ? "" : ref.fmaId().trim();
        int slctTimeout = Objects.equals(thisChain, chainByDpId.get(refDpId)) ? 0 : 1;

        Map<String, String> linkedId = new LinkedHashMap<>();
        linkedId.put(ID, String.valueOf(refDpId));
        linkedId.put(SECTION, section);
        out.add(InstancedExpectation.byIdentity(fileId, CONTROL, linkedId, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
    }

    // ---- combination detection (subset / cancellation search) ----

    private boolean isCombination(int targetIdx, List<Map<String, Integer>> vectors) {
        Map<String, Integer> target = vectors.get(targetIdx);
        if (target.isEmpty()) {
            return false;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            if (i != targetIdx && !vectors.get(i).isEmpty()) {
                candidates.add(i);
            }
        }
        return search(candidates, 0, 0, new HashMap<>(), target, vectors);
    }

    private boolean search(
            List<Integer> candidates,
            int start,
            int picked,
            Map<String, Integer> partial,
            Map<String, Integer> target,
            List<Map<String, Integer>> vectors) {

        for (int i = start; i < candidates.size(); i++) {
            Map<String, Integer> v = vectors.get(candidates.get(i));
            mergeInto(partial, v, 1);
            int np = picked + 1;
            if (np >= 2 && equalsIgnoringZero(partial, target)) {
                mergeInto(partial, v, -1);
                return true;
            }
            if (np < MAX_COMBINATION_SIZE
                    && search(candidates, i + 1, np, partial, target, vectors)) {
                mergeInto(partial, v, -1);
                return true;
            }
            mergeInto(partial, v, -1);
        }
        return false;
    }

    private void mergeInto(Map<String, Integer> partial, Map<String, Integer> v, int sign) {
        for (Map.Entry<String, Integer> e : v.entrySet()) {
            partial.merge(e.getKey(), sign * e.getValue(), Integer::sum);
        }
    }

    private boolean equalsIgnoringZero(Map<String, Integer> partial, Map<String, Integer> target) {
        int nonZero = 0;
        for (Map.Entry<String, Integer> e : partial.entrySet()) {
            if (e.getValue() == 0) {
                continue;
            }
            nonZero++;
            if (!e.getValue().equals(target.get(e.getKey()))) {
                return false;
            }
        }
        return nonZero == target.size();
    }

    private Map<String, Integer> signedVector(TrackSection t) {
        Map<String, Integer> v = new HashMap<>();
        if (t.dpIn() != null) {
            for (String d : t.dpIn()) {
                v.merge(d, 1, Integer::sum);
            }
        }
        if (t.dpOut() != null) {
            for (String d : t.dpOut()) {
                v.merge(d, -1, Integer::sum);
            }
        }
        v.entrySet().removeIf(e -> e.getValue() == 0);
        return v;
    }

    // ---- shared FCT / Control-Table lookups ----

    private Map<String, Integer> idByDpName(ComAebMap fct) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (var chain : fct.chains()) {
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

    private Map<String, Boolean> eChcByDpName(ControlTable controlTable) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (controlTable.dpTable() != null) {
            for (DpTableRow row : controlTable.dpTable()) {
                if (row.name() != null) {
                    map.put(row.name(), row.eChc());
                }
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
