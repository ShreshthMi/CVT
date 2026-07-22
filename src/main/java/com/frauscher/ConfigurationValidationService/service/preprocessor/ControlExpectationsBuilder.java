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

import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * Derives the {@code CFG_CONTROL} (CHC) instanced expectations + the derived
 * {@code CFG_AXCNT.BEHAV_INPUT3} (v2-expectations-contract.md §5.4). The first derivation needing real
 * set arithmetic over the Control Table.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Signed sensor vector per track: {@code +1} per {@code dpIn} name, {@code −1} per {@code dpOut}
 *       (names → ids deferred to emission; classification works in trim-normalized names).</li>
 *   <li><b>Main vs combination</b> by signed-set arithmetic ({@code trackType} is NOT trusted): a track
 *       is a combination iff its vector equals the cancellation-sum of a subset (≥2) of the other
 *       tracks' vectors (non-negative {0,1} sums — a linear span would misclassify mains). Combination
 *       (virtual/supervisor) tracks are excluded from the CHC.</li>
 *   <li>Over <b>main</b> tracks only, tally each DP's signs: a DP that is net-in for one main and
 *       net-out for another → <b>middle</b> (shared boundary); single-sign → <b>boundary</b>.</li>
 *   <li>Emit per DP ({@code fileId} = that DP): middle → <b>2</b> {@code CFG_CONTROL} (BY_IDENTITY,
 *       unordered) referencing the 2 adjacent main tracks; boundary + {@code eChc=YES} → <b>one per
 *       owning main track</b> (a plain boundary owns exactly one; a same-sign junction head shared by
 *       >1 main — the ABS AD01A/AU09A layout — references each, VTF-361); boundary + {@code eChc=NO} →
 *       <b>0</b>. Per block {@code linkedId = (ID=evalDP, SECTION=FMA)} of the referenced track (via
 *       FCT), value {@code SLCT_TIMEOUT} = same/diff COM chain. Plus the derived
 *       {@code CFG_AXCNT.BEHAV_INPUT3} = {@code "7"} iff boundary+{@code eChc=YES}, else {@code "6"}.</li>
 * </ol>
 *
 * <p>Unresolvable/unclassifiable DPs (VTF-360) are recorded in the {@link BaselineInconsistencies} and
 * skipped per DP (their CFG_CONTROL + BEHAV_INPUT3 emissions); an unresolvable adjacent track skips
 * just that one block.</p>
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

    public List<InstancedExpectation> build(
            ControlTable controlTable, BaselineIndex index, BaselineInconsistencies problems) {
        if (controlTable == null || controlTable.trackSections() == null) {
            return List.of();
        }

        List<TrackSection> tracks = controlTable.trackSections();

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
            addForDp(out, dpName, plusByDp, minusByDp, tracks, index, problems);
        }
        return out;
    }

    private void addForDp(
            List<InstancedExpectation> out,
            String dpName,
            Map<String, List<Integer>> plusByDp,
            Map<String, List<Integer>> minusByDp,
            List<TrackSection> tracks,
            BaselineIndex index,
            BaselineInconsistencies problems) {

        List<Integer> plus = plusByDp.getOrDefault(dpName, List.of());
        List<Integer> minus = minusByDp.getOrDefault(dpName, List.of());

        Integer fileId = index.idOfDp(dpName);
        if (fileId == null) {
            problems.add("Counting-head DP '" + dpName + "' has no matching DP in the FCT");
            return;
        }
        Integer thisChain = index.chainOfDpId(fileId);

        boolean middle = !plus.isEmpty() && !minus.isEmpty();
        if (middle) {
            if (plus.size() + minus.size() > 2) {
                problems.add("Middle counting-head DP '" + dpName + "' is shared by more than 2 main tracks");
                return;
            }
            // exactly one + and one − main track → the 2 adjacent tracks.
            addControlBlock(out, fileId, thisChain, tracks.get(plus.get(0)), index, problems);
            addControlBlock(out, fileId, thisChain, tracks.get(minus.get(0)), index, problems);
            out.add(InstancedExpectation.single(fileId, AXCNT, BEHAV_INPUT3, "6"));
            return;
        }

        // boundary (single sign)
        List<Integer> owners = plus.isEmpty() ? minus : plus;
        boolean eChcYes = Boolean.TRUE.equals(index.eChcOfDp(dpName));
        if (eChcYes) {
            // One CFG_CONTROL per owning main track. The usual boundary head owns exactly one; a same-sign
            // junction head — shared by >1 main on the same side (the ABS AD01A/AU09A layout) — references
            // each owning track (VTF-361, user-confirmed 2026-07-06). BEHAV_INPUT3=7 regardless of count.
            for (int owner : owners) {
                addControlBlock(out, fileId, thisChain, tracks.get(owner), index, problems);
            }
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
            BaselineIndex index,
            BaselineInconsistencies problems) {

        EvaluatedFma ref = index.fmaByName(refTrack.name());
        if (ref == null) {
            problems.add("Adjacent track '" + refTrack.name() + "' has no matching FCT FMA");
            return;
        }
        Integer refDpId = BaselineIndex.parseId(ref.dpId(), "evaluating DP of track " + refTrack.name(), problems);
        if (refDpId == null) {
            return; // skip just this block; the malformed id is already recorded.
        }
        String section = ref.fmaId() == null ? "" : ref.fmaId().trim();
        int slctTimeout = Objects.equals(thisChain, index.chainOfDpId(refDpId)) ? 0 : 1;

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

    /** Signed sensor vector, keyed by trim-normalized DP name (matching the {@link BaselineIndex} probes). */
    private Map<String, Integer> signedVector(TrackSection t) {
        Map<String, Integer> v = new HashMap<>();
        if (t.dpIn() != null) {
            for (String d : t.dpIn()) {
                if (d != null) {
                    v.merge(d.trim(), 1, Integer::sum);
                }
            }
        }
        if (t.dpOut() != null) {
            for (String d : t.dpOut()) {
                if (d != null) {
                    v.merge(d.trim(), -1, Integer::sum);
                }
            }
        }
        v.entrySet().removeIf(e -> e.getValue() == 0);
        return v;
    }
}
