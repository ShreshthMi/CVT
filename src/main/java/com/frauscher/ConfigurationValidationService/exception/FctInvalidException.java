package com.frauscher.ConfigurationValidationService.exception;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Thrown when an uploaded FCT2 archive fails identification, parse, or invariant checks. Externally
 * surfaces one of the two locked codes ({@code FCT_TAMPERED} / {@code FCT_INCOMPLETE_BASELINE}) via
 * the carried {@link FctInvalidReason}; the specific reason drives logging only. Design §5.6.
 */
@Slf4j
@Getter
public class FctInvalidException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    private final transient FctInvalidReason reason;

    public FctInvalidException(FctInvalidReason reason, String detail) {
        super(reason.externalCode(), "FCT archive is invalid");
        this.reason = reason;
        log.warn("{} [{}]: {}", reason.externalCode(), reason, detail);
    }
}