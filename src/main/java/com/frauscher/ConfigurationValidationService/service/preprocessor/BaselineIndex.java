package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * request (vtf-360-design.md §4). Replaces the per-builder copies of {@code idByDpName} /
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
 * A DP whose id failed to parse is absent from the DP index; dependent builder lookups then miss and
 * record their own (reference-level) problem — both facts belong in the final list.
 *
 * <h2>Location scoping</h2>
 * A project may span several stations whose track and DP names repeat verbatim, so a bare name is no
 * longer an identity. Every name index here is therefore two-level — <b>name → scope → value</b> —
 * where the scope is the FCT {@code Station} on the FCT side and the Control table {@code LOCATION}
 * banner on the PDQ side (matched to each other as the same code, trimmed and case-insensitive).
 *
 * <p>One rule resolves every lookup, and it degrades cleanly in both directions:</p>
 * <ol>
 *   <li>a scoped hit wins;</li>
 *   <li>else an entry recorded with <i>no</i> scope matches any scope — so a tagged PDQ still works
 *       against an FCT whose Project.xml carries no {@code Station} at all;</li>
 *   <li>else, for DP references only, a project-wide fallback: a name unique across every station
 *       resolves (block sections legitimately reach a DP at the neighbouring station), and a name
 *       carried by two or more is recorded as an inconsistency naming them rather than silently
 *       resolving to whichever was indexed first;</li>
 *   <li>track and FMA lookups are strict instead — a track belongs to its own location by definition,
 *       so a miss there is a miss, and the caller reports it at its own grain.</li>
 * </ol>
 *
 * <p>An untagged single-station baseline never leaves step 3's unique case, which is exactly the
 * behaviour it had before any of this existed.</p>
 */
public final class BaselineIndex {

    /** Scope key for an entry whose station/location is unknown; matches any probe. */
    private static final String NO_SCOPE = "";

    private final Map<String, Map<String, TrackSection>> trackByName = new LinkedHashMap<>();
    private final Map<String, Map<String, EvaluatedFma>> fmaByName = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> idByDpName = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> positionByDpName = new LinkedHashMap<>();
    private final Map<String, Map<String, Boolean>> eChcByDpName = new LinkedHashMap<>();
    private final Map<Integer, Integer> chainByDpId = new LinkedHashMap<>();
    private final Set<Integer> acoIoExbDpIds = new HashSet<>();
    private final List<Chain> chains;
    private final BaselineInconsistencies problems;
    private boolean locationScoped;

    private BaselineIndex(List<Chain> chains, BaselineInconsistencies problems) {
        this.chains = chains;
        this.problems = problems;
    }

    public static BaselineIndex build(ComAebMap fct, ControlTable controlTable, BaselineInconsistencies problems) {
        List<Chain> chains = fct == null || fct.chains() == null ? List.of() : fct.chains();
        BaselineIndex index = new BaselineIndex(chains, problems);
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
            String station = stationOfChain(i);
            for (var aeb : chain.aebs()) {
                Integer dpId = parseId(aeb.dpId(), "DP " + aeb.dpName(), problems);
                if (dpId == null) {
                    continue; // recorded once; dependent lookups miss and report their reference.
                }
                if (aeb.dpName() != null) {
                    // Last-wins, as before this class was scoped. Two AEBs sharing a cpName within one
                    // station is a malformed FCT that nothing upstream rejects, and the tie-break between
                    // them is arbitrary either way — but it must stay the arbitrary answer it already was.
                    put(idByDpName, aeb.dpName(), station, dpId, true);
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
                        put(fmaByName, fma.fmaName(), station, fma, false);
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
                    locationScoped |= ts.location() != null;
                    put(trackByName, ts.name(), ts.location(), ts, true);
                }
            }
        }
        if (controlTable.dpTable() != null) {
            for (DpTableRow row : controlTable.dpTable()) {
                if (row.name() != null) {
                    locationScoped |= row.location() != null;
                    // Two rows for one DP at one location: the second silently replaced the first, taking
                    // its POSITION and E-CHC with it, and DIR_INV is derived from POSITION. The gate does
                    // this for track names; nothing was doing it for the DP table.
                    Map<String, String> seen = positionByDpName.get(norm(row.name()));
                    if (seen != null && seen.containsKey(normScope(row.location()))) {
                        problems.add("DP table has more than one row for DP '" + row.name().trim() + "'"
                                + (row.location() == null ? "" : " at " + row.location().trim()));
                    }
                    put(positionByDpName, row.name(), row.location(), row.position(), true);
                    put(eChcByDpName, row.name(), row.location(), row.eChc(), true);
                }
            }
        }
    }

    private <V> void put(Map<String, Map<String, V>> index, String name, String scope, V value, boolean overwrite) {
        Map<String, V> byScope = index.computeIfAbsent(norm(name), k -> new LinkedHashMap<>());
        if (overwrite) {
            byScope.put(normScope(scope), value);
        } else {
            byScope.putIfAbsent(normScope(scope), value);
        }
    }

    /**
     * The one lookup rule (see the class javadoc). {@code fallbackAcrossScopes} distinguishes a DP
     * reference, which may legitimately reach another station, from a track/FMA, which may not.
     */
    private <V> V lookup(
            Map<String, Map<String, V>> index, String scope, String name, boolean fallbackAcrossScopes, String what) {

        if (name == null) {
            return null;
        }
        Map<String, V> byScope = index.get(norm(name));
        if (byScope == null || byScope.isEmpty()) {
            return null;
        }
        // containsKey, not get() != null: an index may legitimately hold a null value (a DP-table row with
        // no POSITION), and treating that as a miss would send one lookup down the cross-scope path while
        // its sibling resolved exactly — two indexes disagreeing about the same row.
        String probe = normScope(scope);
        if (byScope.containsKey(probe)) {
            return byScope.get(probe); // an unscoped probe matches an unscoped entry here, by construction
        }
        if (scope != null) {
            if (byScope.containsKey(NO_SCOPE)) {
                return byScope.get(NO_SCOPE); // the other side carries no station — every scope matches it
            }
            if (!fallbackAcrossScopes) {
                return null; // strict: a track belongs to its own location; the caller reports the miss
            }
        }
        if (byScope.size() == 1) {
            return byScope.values().iterator().next();
        }
        // Root-cause-keyed so repeated hits on the same name dedup to a single reported item.
        problems.add(what + " '" + name.trim() + "' is ambiguous — it exists at " + describe(byScope.keySet())
                + (scope == null
                        ? " and the reference names no location that picks one"
                        : " and the reference names " + probe + ", which is none of them"));
        return null;
    }

    /** Renders the scope set for a message, naming the unscoped bucket rather than showing a gap. */
    private static String describe(Set<String> scopes) {
        List<String> named = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            named.add(scope.isEmpty() ? "(no station)" : scope);
        }
        return named.toString();
    }

    /** Whether the Control table carried any {@code LOCATION} banner at all. */
    public boolean isLocationScoped() {
        return locationScoped;
    }

    /** The FCT {@code Station} of a CAN segment, or null when Project.xml carries no Station. */
    public String stationOfChain(int chainIndex) {
        if (chainIndex < 0 || chainIndex >= chains.size()) {
            return null;
        }
        FctCom com = chains.get(chainIndex).com();
        return com == null ? null : com.station();
    }

    public TrackSection trackByName(String location, String name) {
        return lookup(trackByName, location, name, false, "Control table track section");
    }

    public EvaluatedFma fmaByName(String station, String name) {
        return lookup(fmaByName, station, name, false, "FCT FMA");
    }

    public Integer idOfDp(String location, String dpName) {
        return lookup(idByDpName, location, dpName, true, "DP");
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

    public String positionOfDp(String location, String dpName) {
        return lookup(positionByDpName, location, dpName, true, "DP-table row for DP");
    }

    public Boolean eChcOfDp(String location, String dpName) {
        return lookup(eChcByDpName, location, dpName, true, "DP-table row for DP");
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

    /**
     * Station and location codes are matched trimmed and case-insensitively — deliberately looser than
     * the case-sensitive track-name match in {@link BaselineGate}, because the code is short free text
     * typed into two different artefacts by two different teams.
     */
    private static String normScope(String scope) {
        return scope == null ? NO_SCOPE : scope.trim().toUpperCase(Locale.ROOT);
    }
}
