package com.frauscher.ConfigurationValidationService.exception;

/**
 * Thrown by the Phase 2 baseline gate (v2-expectations-contract.md §3.1) when the PDQ Control Table is
 * absent or carries no track sections — the track reconciliation cannot run. Maps to HTTP 400 with the
 * {@code PHASE2_CONTROL_TABLE_MISSING} error code.
 */
public class ControlTableMissingException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public ControlTableMissingException(String message) {
        super("PHASE2_CONTROL_TABLE_MISSING", message);
    }
}
