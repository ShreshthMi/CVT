package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

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
 * chain(X) == chain(fileDP) ? 0 : 1}.</p>
 *
 * <p>Unresolvable references (VTF-360, vtf-360-design.md §3) are recorded in the
 * {@link BaselineInconsistencies} and skipped at their grain — the whole FMA when its track/ids do
 * not resolve, the single head (both expectations) when the head or its DP-table row does not — so
 * one run enumerates every problem before the preprocessor rejects.</p>
 */
@Component
public class CountingHeadExpectationsBuilder {

    private static final String BLOCK_PREFIX = "CFG_ZP_FMA";
    private static final String DIR_INV = "DIR_INV";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String BELOW_THE_RAIL = "BELOW THE RAIL";
    private static final String ID = "ID";

    public List<InstancedExpectation> build(ComAebMap fct, BaselineIndex index, BaselineInconsistencies problems) {
        if (fct == null || fct.chains() == null) {
            return List.of();
        }

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
                    addFma(out, fma, index, problems);
                }
            }
        }
        return out;
    }

    private void addFma(
            List<InstancedExpectation> out, EvaluatedFma fma, BaselineIndex index, BaselineInconsistencies problems) {

        TrackSection track = index.trackByName(fma.fmaName());
        if (track == null) {
            problems.add("FCT FMA '" + fma.fmaName() + "' has no matching PDQ Control Table track");
            return;
        }

        Integer fileId = BaselineIndex.parseId(fma.dpId(), "evaluating DP of FMA " + fma.fmaName(), problems);
        Integer fmaId = BaselineIndex.parseId(fma.fmaId(), "fmaId of FMA " + fma.fmaName(), problems);
        if (fileId == null || fmaId == null) {
            return; // skip the whole FMA; the malformed id is already recorded.
        }
        String block = BLOCK_PREFIX + (fmaId + 1);
        Integer fileChain = index.chainOfDpId(fileId);

        addHeads(out, safe(track.dpIn()), false, fileId, block, fileChain, index, problems, fma.fmaName());
        addHeads(out, safe(track.dpOut()), true, fileId, block, fileChain, index, problems, fma.fmaName());
    }

    private void addHeads(
            List<InstancedExpectation> out,
            List<String> heads,
            boolean isOut,
            int fileId,
            String block,
            Integer fileChain,
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
            String position = index.positionOfDp(head);
            if (position == null) {
                problems.add("Counting head '" + head + "' of track '" + trackName
                        + "' is absent from the PDQ DP table");
                continue; // skip the head entirely — DIR_INV is underivable, and a partial pair would mislead.
            }

            boolean isBelow = BELOW_THE_RAIL.equalsIgnoreCase(position.trim());
            int dirInv = (isOut == isBelow) ? 0 : 1;
            int slctTimeout = Objects.equals(fileChain, index.chainOfDpId(headId)) ? 0 : 1;

            Map<String, String> linkedId = Map.of(ID, String.valueOf(headId));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, DIR_INV, String.valueOf(dirInv)));
            out.add(InstancedExpectation.byIdentity(fileId, block, linkedId, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
