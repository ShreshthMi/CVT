package com.frauscher.ConfigurationValidationService.exception;

/**
 * Thrown when {@code POST /api/config/v2/validate} is called without both baseline uploads. The
 * coupled-artifacts gate (design §2) requires FCT <b>and</b> PDQ together; only one present is an
 * invalid state. Maps to {@code HTTP 400 PHASE2_INPUTS_INCOMPLETE}.
 */
public class Phase2InputsIncompleteException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public Phase2InputsIncompleteException() {
        super("PHASE2_INPUTS_INCOMPLETE", "v2 validation requires both FCT and PDQ uploads");
    }
}
