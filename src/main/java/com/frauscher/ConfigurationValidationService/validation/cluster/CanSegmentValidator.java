package com.frauscher.ConfigurationValidationService.validation.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedFinding;

/**
 * Cluster 1 (VTF-338) Check-A named verdicts (design §8.1). A post-pass over the instanced findings + the
 * uploaded ADCs that refines the generic PASS/FAIL/occurrence outcomes into the CAN-segment verdict
 * vocabulary, carried as sentinels in {@code expected}/{@code actual}:
 * <ul>
 *   <li><b>ORPHANED</b> — an uploaded AEB ADC whose {@code [IDENTIFICATION] ID} matches no AEB in the
 *       FCT-defined CAN segments (today such files are silently skipped by the expectation-driven engine).
 *       Emitted as a new {@link ValidationResult} (no detail cell).</li>
 *   <li><b>FILE NOT FOUND</b> — an in-scope block references an AEB id absent from the baseline. Refines an
 *       {@code UNEXPECTED_OCCURRENCE} whose referenced {@code ID} is unknown.</li>
 *   <li><b>INVALID SCOPE / INVALID VALUE</b> — a {@code SLCT_TIMEOUT} value mismatch: actual {@code 2–7}
 *       (out of Phase 2's binary physical/virtual scope) → INVALID SCOPE; otherwise → INVALID VALUE. Set
 *       to {@link ValidationStatus#INVALID}.</li>
 * </ul>
 *
 * <p>The physical/virtual classification driving the expected {@code SLCT_TIMEOUT} (0 same-segment / 1
 * different-segment) is already segment-based in {@code CountingHeadExpectationsBuilder} et al. (chain ==
 * CAN segment), so this pass only refines the <i>verdict</i>, not the expectation. Finding results are
 * mutated in place; the detail-cell annotation still reads the finding's raw actual, so the cell keeps the
 * value while the log entry carries the verdict.</p>
 */
@Component
public class CanSegmentValidator {

    private static final String ID = "ID";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String IDENTIFICATION = "IDENTIFICATION";
    private static final String RULE_IDENTITY_LOOKUP = "IdentityLookup";

    /** Blocks whose ID / SLCT_TIMEOUT Cluster 1 owns (design §8.1). */
    private static final Set<String> IN_SCOPE = Set.of(
            "CFG_ZP_FMA1", "CFG_ZP_FMA2", "CFG_SECTION_OUT", "CFG_CONTROL",
            "CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2", "CFG_DATA_OUT");

    /**
     * Applies the Check-A verdicts. Mutates the finding results in place (INVALID SCOPE/VALUE, FILE NOT
     * FOUND) and returns the new ORPHANED results (which have no detail cell).
     */
    public List<ValidationResult> apply(
            List<InstancedFinding> findings, ComAebMap fct, List<ParsedConfigFile> parsedFiles) {

        Set<Integer> aebIds = aebIds(fct);

        if (findings != null) {
            for (InstancedFinding f : findings) {
                if (f.result() == null || !IN_SCOPE.contains(f.block())) {
                    continue;
                }
                if (!ValidationStatus.FAIL.name().equals(f.result().getStatus())) {
                    continue;
                }
                if (SLCT_TIMEOUT.equals(f.entryKey()) && f.kind() == MismatchAnnotation.Kind.VALUE) {
                    refineSlctTimeout(f.result(), f.rawActual());
                } else if (f.kind() == MismatchAnnotation.Kind.UNEXPECTED) {
                    refineUnknownReference(f, aebIds);
                }
            }
        }

        List<ValidationResult> orphaned = new ArrayList<>();
        if (parsedFiles != null) {
            for (ParsedConfigFile file : parsedFiles) {
                if (file.isComDetails()) {
                    continue; // COM files are identified by comId, not an AEB dpId
                }
                if (!aebIds.contains(file.getId())) {
                    orphaned.add(new ValidationResult(file.getFileName(), RULE_IDENTITY_LOOKUP, IDENTIFICATION, ID,
                            String.valueOf(file.getId()), ValidationConstants.ORPHANED, ValidationStatus.FAIL.name()));
                }
            }
        }
        return orphaned;
    }

    /** SLCT_TIMEOUT value mismatch → INVALID SCOPE (actual 2–7) / INVALID VALUE (else). */
    private void refineSlctTimeout(ValidationResult result, String rawActual) {
        Integer actual = parse(rawActual);
        boolean scope = actual != null && actual >= 2 && actual <= 7;
        result.setActualValue(scope ? ValidationConstants.INVALID_SCOPE : ValidationConstants.INVALID_VALUE);
        result.setStatus(ValidationStatus.INVALID.name());
    }

    /** An extra occurrence whose referenced AEB id is unknown to the baseline → FILE NOT FOUND. */
    private void refineUnknownReference(InstancedFinding f, Set<Integer> aebIds) {
        String refId = f.linkedId() == null ? null : f.linkedId().get(ID);
        if (refId == null) {
            return; // no AEB-id reference (e.g. positional ACO, or forwarding's CAN_TX_ID/DEST_COM)
        }
        Integer id = parse(refId);
        if (id == null || !aebIds.contains(id)) {
            f.result().setActualValue(ValidationConstants.FILE_NOT_FOUND);
        }
    }

    private Set<Integer> aebIds(ComAebMap fct) {
        Set<Integer> ids = new HashSet<>();
        if (fct == null || fct.chains() == null) {
            return ids;
        }
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                Integer id = parse(aeb.dpId());
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Integer parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
