package com.frauscher.ConfigurationValidationService.exception;

import java.util.List;

/**
 * Thrown by the Phase 2 baseline gate (v2-expectations-contract.md §3.1) when the PDQ Control Table
 * track list does not reconcile with the FCT track universe: tracks the PDQ expects but the FCT does
 * not define ({@code notFoundTracks}), and/or FCT tracks the PDQ list never consumes
 * ({@code extraTracks}). Both lists are accumulated and reported together. Maps to HTTP 400 with the
 * {@code PHASE2_TRACK_RECONCILIATION_FAILED} error code.
 *
 * <p>The lists are kept as accessors for the later "collect every preprocessing error" change; the
 * current flat {@code ApiErrorResponse} surfaces them in the (verbose) {@code message}.</p>
 */
public class TrackReconciliationFailedException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> notFoundTracks;
    private final transient List<String> extraTracks;

    public TrackReconciliationFailedException(List<String> notFoundTracks, List<String> extraTracks) {
        super("PHASE2_TRACK_RECONCILIATION_FAILED", buildMessage(notFoundTracks, extraTracks));
        this.notFoundTracks = List.copyOf(notFoundTracks);
        this.extraTracks = List.copyOf(extraTracks);
    }

    public List<String> getNotFoundTracks() {
        return notFoundTracks;
    }

    public List<String> getExtraTracks() {
        return extraTracks;
    }

    private static String buildMessage(List<String> notFoundTracks, List<String> extraTracks) {
        StringBuilder sb = new StringBuilder("Track reconciliation failed.");
        if (notFoundTracks != null && !notFoundTracks.isEmpty()) {
            sb.append(" PDQ Control Table tracks not found in the FCT: ").append(notFoundTracks).append('.');
        }
        if (extraTracks != null && !extraTracks.isEmpty()) {
            sb.append(" FCT tracks not present in the PDQ Control Table: ").append(extraTracks).append('.');
        }
        return sb.toString();
    }
}
