package com.frauscher.ConfigurationValidationService.exception;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Thrown when a PDQ workbook fails parse or validation.
 *
 * <p>Externally surfaces the single locked code {@code PDQ_INVALID} (no detail); the carried
 * {@link PdqInvalidReason} provides internal diagnostics for logging only. Parse-layer failures
 * (malformed multipart -> HTTP 400, size exceedance -> HTTP 413) are pre-business-logic and are
 * NOT folded into this exception. See {@code fcvt-phase2-design.md} §6.7.</p>
 */
@Slf4j
@Getter
public class PdqInvalidException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    private final transient PdqInvalidReason reason;

    public PdqInvalidException(PdqInvalidReason reason, String detail) {
        super("PDQ_INVALID", "PDQ workbook is invalid");
        this.reason = reason;
        log.warn("PDQ_INVALID [{}]: {}", reason, detail);
    }
}