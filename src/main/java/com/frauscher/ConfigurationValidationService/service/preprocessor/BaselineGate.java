package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     */
    private void reconcileTracks(PdqUploadResponse pdq, ComAebMap fct) {
        ControlTable controlTable = pdq.getControlTable();
        if (controlTable == null
                || controlTable.trackSections() == null
                || controlTable.trackSections().isEmpty()) {
            throw new ControlTableMissingException(
                    "PDQ Control Table is missing or carries no track sections");
        }

        List<String> pdqTracks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TrackSection ts : controlTable.trackSections()) {
            String name = ts.name() == null ? "" : ts.name().trim();
            if (!seen.add(name)) {
                throw new BaselineInconsistentException(
                        "Duplicate track name in the PDQ Control Table: '" + name + "'");
            }
            pdqTracks.add(name);
        }

        List<String> fctUniverse = fctTrackUniverse(fct);

        List<String> notFound = new ArrayList<>();
        for (String track : pdqTracks) {
            if (!fctUniverse.remove(track)) {
                notFound.add(track);
            }
        }
        List<String> extra = fctUniverse; // whatever the PDQ list did not consume

        if (!notFound.isEmpty() || !extra.isEmpty()) {
            throw new TrackReconciliationFailedException(notFound, extra);
        }
    }

    private List<String> fctTrackUniverse(ComAebMap fct) {
        List<String> universe = new ArrayList<>();
        if (fct.chains() == null) {
            return universe;
        }
        fct.chains().forEach(chain -> {
            if (chain.aebs() == null) {
                return;
            }
            chain.aebs().forEach(aeb -> {
                if (aeb.evaluatedFmas() == null) {
                    return;
                }
                aeb.evaluatedFmas().forEach(fma -> {
                    if (fma.fmaName() != null) {
                        universe.add(fma.fmaName().trim());
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
}
