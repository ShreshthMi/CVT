package com.frauscher.ConfigurationValidationService.exception;

/**
 * Thrown by the Phase 2 baseline gate (v2-expectations-contract.md §3) when the FCT + PDQ (+ tpf)
 * baseline is internally inconsistent: an RSR_TYPE mismatch (or RSR_TYPE absent from both PDQ and tpf),
 * a duplicate track name in the PDQ Control Table, or a build-time inconsistency raised while
 * assembling instanced expectations. Maps to HTTP 400 with the {@code PHASE2_BASELINE_INCONSISTENT}
 * error code.
 */
public class BaselineInconsistentException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public BaselineInconsistentException(String message) {
        super("PHASE2_BASELINE_INCONSISTENT", message);
    }
}
