package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * The shared FCT + Control-Table lookup index for the instanced-expectation builders, built once per
 * request (vtf-371-design.md §4). Replaces the per-builder copies of {@code idByDpName} /
 * {@code chainByDpId} / {@code fmaByName} / {@code trackByName} and fixes their two audited defects
 * at the root:
 * <ul>
 *   <li><b>Trim asymmetry</b> — the {@link BaselineGate} reconciles trimmed names, but the old maps
 *       keyed and probed raw strings, so a trailing space passed the gate and then failed post-gate.
 *       All keys and probes here are trim-normalized.</li>
 *   <li><b>Eager map poisoning</b> — a single malformed {@code dpId} used to abort the whole run
 *       while the first builder eagerly built its maps. Parsing is per-entry tolerant: the bad entry
 *       is recorded once in the {@link BaselineInconsistencies} and skipped, so every malformed id in
 *       the FCT is enumerated in one run.</li>
 * </ul>
 * A DP whose id failed to parse is absent from {@code idByDpName}/{@code chainByDpId}; dependent
 * builder lookups then miss and record their own (reference-level) problem — both facts belong in
 * the final list.
 */
public final class BaselineIndex {

    private final Map<String, TrackSection> trackByName = new LinkedHashMap<>();
    private final Map<String, EvaluatedFma> fmaByName = new LinkedHashMap<>();
    private final Map<String, Integer> idByDpName = new LinkedHashMap<>();
    private final Map<Integer, Integer> chainByDpId = new LinkedHashMap<>();
    private final Map<String, String> positionByDpName = new LinkedHashMap<>();
    private final Map<String, Boolean> eChcByDpName = new LinkedHashMap<>();
    private final Set<Integer> acoIoExbDpIds = new HashSet<>();
    private final List<Chain> chains;

    private BaselineIndex(List<Chain> chains) {
        this.chains = chains;
    }

    public static BaselineIndex build(ComAebMap fct, ControlTable controlTable, BaselineInconsistencies problems) {
        List<Chain> chains = fct == null || fct.chains() == null ? List.of() : fct.chains();
        BaselineIndex index = new BaselineIndex(chains);
        index.indexFct(problems);
        index.indexControlTable(controlTable);
        return index;
    }

    private void indexFct(BaselineInconsistencies problems) {
        for (int i = 0; i < chains.size(); i++) {
            Chain chain = chains.get(i);
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                Integer dpId = parseId(aeb.dpId(), "DP " + aeb.dpName(), problems);
                if (dpId == null) {
                    continue; // recorded once; dependent lookups miss and report their reference.
                }
                if (aeb.dpName() != null) {
                    idByDpName.put(norm(aeb.dpName()), dpId);
                }
                chainByDpId.put(dpId, i);
                if (aeb.acoIoExbs() != null && !aeb.acoIoExbs().isEmpty()) {
                    acoIoExbDpIds.add(dpId);
                }
                if (aeb.evaluatedFmas() == null) {
                    continue;
                }
                for (EvaluatedFma fma : aeb.evaluatedFmas()) {
                    if (fma.fmaName() != null) {
                        fmaByName.putIfAbsent(norm(fma.fmaName()), fma);
                    }
                }
            }
        }
    }

    private void indexControlTable(ControlTable controlTable) {
        if (controlTable == null) {
            return;
        }
        if (controlTable.trackSections() != null) {
            for (TrackSection ts : controlTable.trackSections()) {
                if (ts.name() != null) {
                    trackByName.put(norm(ts.name()), ts);
                }
            }
        }
        if (controlTable.dpTable() != null) {
            for (DpTableRow row : controlTable.dpTable()) {
                if (row.name() != null) {
                    positionByDpName.put(norm(row.name()), row.position());
                    eChcByDpName.put(norm(row.name()), row.eChc());
                }
            }
        }
    }

    public TrackSection trackByName(String name) {
        return name == null ? null : trackByName.get(norm(name));
    }

    public EvaluatedFma fmaByName(String name) {
        return name == null ? null : fmaByName.get(norm(name));
    }

    public Integer idOfDp(String dpName) {
        return dpName == null ? null : idByDpName.get(norm(dpName));
    }

    public Integer chainOfDpId(Integer dpId) {
        return dpId == null ? null : chainByDpId.get(dpId);
    }

    /**
     * Whether the AEB evaluating {@code dpId} carries an ACO IO-EXB (has a {@code CFG_AXCNT} block).
     * {@code BEHAV_INPUT3} lives in {@code CFG_AXCNT}, so it is only present on these files — matching
     * the {@code CFG_AXCNT} scalar rules' {@code ACOIOEXBDETAILS} scope (VTF-362 M5).
     */
    public boolean hasAcoIoExb(Integer dpId) {
        return dpId != null && acoIoExbDpIds.contains(dpId);
    }

    public String positionOfDp(String dpName) {
        return dpName == null ? null : positionByDpName.get(norm(dpName));
    }

    public Boolean eChcOfDp(String dpName) {
        return dpName == null ? null : eChcByDpName.get(norm(dpName));
    }

    public List<Chain> chains() {
        return chains;
    }

    /** The addressable COM id of chain {@code chainIndex}, or null with the problem recorded. */
    public Integer comIdOfChain(Integer chainIndex, String what, BaselineInconsistencies problems) {
        if (chainIndex == null || chainIndex < 0 || chainIndex >= chains.size()) {
            problems.add("No chain resolved for " + what);
            return null;
        }
        FctCom com = chains.get(chainIndex).com();
        if (com == null) {
            // Root-cause-keyed (no per-reference context) so repeated hits dedup to one item.
            problems.add("CAN segment " + chainIndex + " has no addressable COM");
            return null;
        }
        return parseId(com.comId(), "COM id of " + com.comName(), problems);
    }

    /**
     * Tolerant id parse: null/blank/non-numeric records one problem and returns null; the caller
     * skips its derivation at the documented grain.
     */
    public static Integer parseId(String value, String what, BaselineInconsistencies problems) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            problems.add("Non-numeric id for " + what + ": " + value);
            return null;
        }
    }

    private static String norm(String value) {
        return value.trim();
    }
}
