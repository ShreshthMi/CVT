package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;
import com.frauscher.ConfigurationValidationService.exception.ControlTableMissingException;
import com.frauscher.ConfigurationValidationService.exception.TrackReconciliationFailedException;

/**
 * The Phase 2 baseline gate (v2-expectations-contract.md §3): hard HTTP 400 admission control run
 * <i>before</i> any expectations are built. It (1) reconciles the PDQ Control Table track list against
 * the FCT track universe and (2) cross-checks {@code RSR_TYPE} across the PDQ and the optional tpf
 * blocks. Runs after the controller's coupled-artifacts gate, so {@code fctData} and {@code pdqData}
 * are non-null here.
 */
@Component
public class BaselineGate {

    private static final String RSR_BLOCK = "CFG_RSR_TYPE";
    private static final String RSR_KEY = "RSR_TYPE";
    /** Bucket for a track or FMA whose location/station is unknown. */
    private static final String NO_SCOPE = "";

    public void check(ValidationInputV2 userInput) {
        reconcileTracks(userInput.getPdqData(), userInput.getFctData());
        crossCheckRsrType(userInput);
    }

    /**
     * §3.1 — the PDQ Control Table track list is authoritative; each name is literal, case-sensitive
     * (after a defensive trim) matched against the FCT track universe (every {@code EvaluatedFma.fmaName}
     * over all chains/AEBs; ACO output names excluded), consuming one occurrence per match (multiset).
     * NOT-FOUND (PDQ track absent from the FCT) and EXTRA (FCT track the PDQ never consumes) are
     * accumulated and reported together.
     *
     * <p><b>Per location when the Control table is tagged.</b> A project may span several stations whose
     * track names repeat verbatim, so the multiset is consumed <i>within</i> a location against that
     * station's FMAs alone — a name at STN1 can no longer be satisfied by the same name at STN2. What
     * changes is only the scope of the comparison: a repeat inside <i>one</i> location is still a
     * rejection, and it is only across locations that a repeated name became legal.</p>
     *
     * <p>A Control table with no banners keeps the original flat comparison, as does a baseline whose
     * Project.xml carries no {@code Station} at all — matching {@link BaselineIndex}, where an unscoped
     * entry answers any probe.</p>
     */
    private void reconcileTracks(PdqUploadResponse pdq, ComAebMap fct) {
        ControlTable controlTable = pdq.getControlTable();
        if (controlTable == null
                || controlTable.trackSections() == null
                || controlTable.trackSections().isEmpty()) {
            throw new ControlTableMissingException(
                    "PDQ Control Table is missing or carries no track sections");
        }

        Map<String, List<String>> pdqByLocation = new LinkedHashMap<>();
        Set<List<String>> seen = new HashSet<>();
        boolean tagged = false;
        for (TrackSection ts : controlTable.trackSections()) {
            String name = ts.name() == null ? "" : ts.name().trim();
            String location = scope(ts.location());
            tagged |= ts.location() != null;
            if (!seen.add(List.of(location, name))) {
                throw new BaselineInconsistentException(
                        "Duplicate track name in the PDQ Control Table: '" + label(location, name) + "'");
            }
            pdqByLocation.computeIfAbsent(location, k -> new ArrayList<>()).add(name);
        }

        Map<String, List<String>> fctByStation = fctTrackUniverse(fct);
        boolean fctHasStations = !fctByStation.keySet().equals(Set.of(NO_SCOPE));

        // A Control table naming two or more locations against an FCT that names no station at all is not
        // reconcilable: nothing on the FCT side can say which location an FMA belongs to, so every
        // location's probe would resolve to the same unscoped entry and two locations' tracks would be
        // validated against one station's topology, silently. One location is still fine — there is only
        // one answer to give. Both artefacts arrive independently on the v2 validate request, so this
        // pairing is reachable even when each half is internally sound.
        if (tagged && !fctHasStations && pdqByLocation.size() > 1) {
            throw new BaselineInconsistentException(
                    "The Control table banners " + pdqByLocation.size() + " locations " + pdqByLocation.keySet()
                            + " but the FCT carries no Station, so no track can be attributed to one");
        }

        List<String> notFound = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        if (tagged && fctHasStations) {
            reconcilePerLocation(pdqByLocation, fctByStation, notFound, extra);
        } else {
            reconcileFlat(pdqByLocation, fctByStation, notFound, extra);
        }

        if (!notFound.isEmpty() || !extra.isEmpty()) {
            throw new TrackReconciliationFailedException(notFound, extra);
        }
    }

    /**
     * Every entry is qualified with its location, so the two lists stay readable side by side.
     *
     * <p>FMAs on a chain with no station of its own sit in one shared pool that any location may consume,
     * which is what keeps this in step with {@link BaselineIndex}: there an unscoped entry answers a
     * scoped probe, so without the same allowance here the gate would reject an FMA the builders would
     * then have resolved perfectly well — the gate/post-gate asymmetry VTF-360 fixed at the root.</p>
     *
     * <p>They agree on <i>presence</i> and deliberately differ on <i>multiplicity</i>. The index is a
     * non-consuming lookup, so it would answer the same unscoped FMA to every location that asks; the gate
     * consumes, so the second location asking gets a NOT-FOUND. That is the multiset doing its job — one
     * physical FMA cannot be two track sections — and is not an asymmetry to be tidied away.</p>
     */
    private void reconcilePerLocation(
            Map<String, List<String>> pdqByLocation,
            Map<String, List<String>> fctByStation,
            List<String> notFound,
            List<String> extra) {

        List<String> unscoped = new ArrayList<>(fctByStation.getOrDefault(NO_SCOPE, List.of()));
        for (Map.Entry<String, List<String>> location : pdqByLocation.entrySet()) {
            // A partially tagged Control table (untagged rows above the first banner) puts a NO_SCOPE key
            // on BOTH sides. Alias the pool for that one key rather than copying it, or the unscoped FMAs
            // are consumable twice over — once here and once from the shared pool — which both accepts a
            // baseline where two track rows lean on one physical FMA and double-reports the leftovers.
            // The Control table parser rejects such a sheet, but pdqData also arrives straight off the v2
            // validate request body, where nothing has vetted it.
            boolean sharedPool = NO_SCOPE.equals(location.getKey());
            List<String> universe = sharedPool
                    ? unscoped
                    : new ArrayList<>(fctByStation.getOrDefault(location.getKey(), List.of()));
            for (String track : location.getValue()) {
                if (!universe.remove(track) && !unscoped.remove(track)) {
                    notFound.add(label(location.getKey(), track));
                }
            }
            if (!sharedPool) {
                universe.forEach(track -> extra.add(label(location.getKey(), track)));
            }
        }
        // Stations the Control table never banners at all — their whole track list is unconsumed.
        for (Map.Entry<String, List<String>> station : fctByStation.entrySet()) {
            if (!NO_SCOPE.equals(station.getKey()) && !pdqByLocation.containsKey(station.getKey())) {
                station.getValue().forEach(track -> extra.add(label(station.getKey(), track)));
            }
        }
        extra.addAll(unscoped);
    }

    /** The original project-wide multiset: one pool of PDQ tracks against one pool of FCT tracks. */
    private void reconcileFlat(
            Map<String, List<String>> pdqByLocation,
            Map<String, List<String>> fctByStation,
            List<String> notFound,
            List<String> extra) {

        List<String> universe = new ArrayList<>();
        fctByStation.values().forEach(universe::addAll);
        for (List<String> tracks : pdqByLocation.values()) {
            for (String track : tracks) {
                if (!universe.remove(track)) {
                    notFound.add(track);
                }
            }
        }
        extra.addAll(universe);
    }

    /** The FCT track universe, grouped by the station its CAN segment sits under. */
    private Map<String, List<String>> fctTrackUniverse(ComAebMap fct) {
        Map<String, List<String>> universe = new LinkedHashMap<>();
        if (fct.chains() == null) {
            return universe;
        }
        fct.chains().forEach(chain -> {
            if (chain.aebs() == null) {
                return;
            }
            String station = scope(chain.com() == null ? null : chain.com().station());
            chain.aebs().forEach(aeb -> {
                if (aeb.evaluatedFmas() == null) {
                    return;
                }
                aeb.evaluatedFmas().forEach(fma -> {
                    if (fma.fmaName() != null) {
                        universe.computeIfAbsent(station, k -> new ArrayList<>()).add(fma.fmaName().trim());
                    }
                });
            });
        });
        return universe;
    }

    /**
     * §3.2 — {@code RSR_TYPE} dual-source cross-check. Both present and differing, or absent from both
     * (PDQ is mandatory), are inconsistencies; exactly one present is used as-is. Present-but-blank is
     * treated as absent.
     */
    private void crossCheckRsrType(ValidationInputV2 userInput) {
        String pdqRsr = scalar(userInput.getPdqData().getCqIrParameters(), RSR_BLOCK, RSR_KEY);
        String tpfRsr = scalar(userInput.getTpfSections(), RSR_BLOCK, RSR_KEY);

        boolean pdqPresent = pdqRsr != null;
        boolean tpfPresent = tpfRsr != null;

        if (pdqPresent && tpfPresent) {
            if (!pdqRsr.equals(tpfRsr)) {
                throw new BaselineInconsistentException(
                        "RSR_TYPE mismatch between PDQ (" + pdqRsr + ") and tpf (" + tpfRsr + ")");
            }
            return;
        }
        if (!pdqPresent && !tpfPresent) {
            throw new BaselineInconsistentException(
                    "RSR_TYPE is absent from both the PDQ and the tpf blocks");
        }
        // Exactly one present -> use it; consistency holds.
    }

    private static String scalar(Map<String, Map<String, Object>> sections, String block, String key) {
        if (sections == null) {
            return null;
        }
        Map<String, Object> entries = sections.get(block);
        if (entries == null) {
            return null;
        }
        Object value = entries.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Station and location codes are matched trimmed and case-insensitively — deliberately looser than
     * the case-sensitive track-name match above, because the code is short free text typed into two
     * different artefacts by two different teams. Kept in step with {@link BaselineIndex}.
     */
    private static String scope(String value) {
        return value == null ? NO_SCOPE : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String label(String scope, String name) {
        return scope.isEmpty() ? name : scope + " / " + name;
    }
}
