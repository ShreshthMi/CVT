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
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Derives the {@code CFG_ZP_FMA1}/{@code CFG_ZP_FMA2} counting-head instanced expectations
 * (v2-expectations-contract.md §5.1). <b>Every</b> FCT {@link EvaluatedFma} gets a {@code CFG_ZP_FMA}
 * block on its evaluating DP — combination/supervisor tracks carry sensors (dpIn/dpOut) too, so they
 * are not filtered out. One {@code BY_IDENTITY} expectation pair (DIR_INV + SLCT_TIMEOUT) is emitted
 * per counting head.
 *
 * <p>Per FMA E (FCT {@code EvaluatedFma}), matched to its PDQ {@link TrackSection} by name:
 * {@code fileId = E.dpId} (the evaluating DP), {@code block = CFG_ZP_FMA{E.fmaId + 1}} (fmaId is
 * 0-based). For each head X in {@code dpIn ∪ dpOut}: {@code linkedId = {ID: idOf(X)}};
 * {@code DIR_INV = (X∈dpOut) == (dpTable[X] BELOW THE RAIL) ? 0 : 1}; {@code SLCT_TIMEOUT =
 * chain(X) == chain(fileDP) ? 0 : 1}. A head absent from the FCT or from the PDQ DP table is a
 * malformed baseline (§3.3) → {@code PHASE2_BASELINE_INCONSISTENT}.</p>
 */
@Component
public class CountingHeadExpectationsBuilder {

    private static final String BLOCK_PREFIX = "CFG_ZP_FMA";
    private static final String DIR_INV = "DIR_INV";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String BELOW_THE_RAIL = "BELOW THE RAIL";
    private static final String ID = "ID";

    public List<InstancedExpectation> build(ComAebMap fct, ControlTable controlTable) {
        if (fct == null || fct.chains() == null || controlTable == null) {
            return List.of();
        }

        Map<String, TrackSection> trackByName = trackByName(controlTable);
        Map<String, Integer> idByDpName = idByDpName(fct);
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);
        Map<String, String> positionByDpName = positionByDpName(controlTable);

        List<InstancedExpectation> out = new ArrayList<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.evaluatedFmas() == null) {
                    continue;
                }
                for (EvaluatedFma fma : aeb.evaluatedFmas()) {
                    addFma(out, fma, trackByName, idByDpName, chainByDpId, positionByDpName);
                }
            }
        }
        return out;
    }

    private void addFma(
            List<InstancedExpectation> out,
            EvaluatedFma fma,
            Map<String, TrackSection> trackByName,
            Map<String, Integer> idByDpName,
            Map<Integer, Integer> chainByDpId,
            Map<String, String> positionByDpName) {

        TrackSection track = trackByName.get(fma.fmaName());
        if (track == null) {
            // The reconciliation gate (§3.1) guarantees a match; defensive.
            throw new BaselineInconsistentException(
                    "FCT FMA '" + fma.fmaName() + "' has no matching PDQ Control Table track");
        }

        int fileId = parseId(fma.dpId(), "evaluating DP of FMA " + fma.fmaName());
        String block = BLOCK_PREFIX + (parseId(fma.fmaId(), "fmaId of FMA " + fma.fmaName()) + 1);
        Integer fileChain = chainByDpId.get(fileId);

        addHeads(out, safe(track.dpIn()), false, fileId, block, fileChain,
                idByDpName, chainByDpId, positionByDpName, fma.fmaName());
        addHeads(out, safe(track.dpOut()), true, fileId, block, fileChain,
                idByDpName, chainByDpId, positionByDpName, fma.fmaName());
    }

    private void addHeads(
            List<InstancedExpectation> out,
            List<String> heads,
            boolean isOut,
            int fileId,
            String block,
            Integer fileChain,
            Map<String, Integer> idByDpName,
            Map<Integer, Integer> chainByDpId,
            Map<String, String> positionByDpName,
            String trackName) {

        for (String head : heads) {
            Integer headId = idByDpName.get(head);
            if (headId == null) {
                throw new BaselineInconsistentException(
                        "Counting head '" + head + "' of track '" + trackName
                                + "' has no matching DP in the FCT");
            }
            String position = positionByDpName.get(head);
            if (position == null) {
                throw new BaselineInconsistentException(
                        "Counting head '" + head + "' of track '" + trackName
                                + "' is absent from the PDQ DP table");
            }

            boolean isBelow = BELOW_THE_RAIL.equalsIgnoreCase(position.trim());
            int dirInv = (isOut == isBelow) ? 0 : 1;
            int slctTimeout = Objects.equals(fileChain, chainByDpId.get(headId)) ? 0 : 1;

            Map<String, String> linkedId = Map.of(ID, String.valueOf(headId));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, DIR_INV, String.valueOf(dirInv)));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
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

    private Map<String, String> positionByDpName(ControlTable controlTable) {
        Map<String, String> map = new LinkedHashMap<>();
        if (controlTable.dpTable() != null) {
            for (DpTableRow row : controlTable.dpTable()) {
                if (row.name() != null) {
                    map.put(row.name(), row.position());
                }
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
}
