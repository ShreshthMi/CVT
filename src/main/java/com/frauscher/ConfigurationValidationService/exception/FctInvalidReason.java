package com.frauscher.ConfigurationValidationService.exception;

/**
 * Internal diagnostic detail for a {@link FctInvalidException}. Each reason maps to one of the two
 * external codes — {@code FCT_TAMPERED} (cross-reference / invariant / XML-parse failure) or
 * {@code FCT_INCOMPLETE_BASELINE} (pre-export baseline-shape problem). Design §5.6.
 */
public enum FctInvalidReason {

    /** Project.xml could not be parsed. */
    XML_PARSE_FAILED("FCT_TAMPERED"),
    /** A cross-reference (e.g. an ACO OutputFma -> FMA -> AEB) does not resolve. */
    XREF_UNRESOLVED("FCT_TAMPERED"),
    /** The upload is not a readable ZIP — bad magic bytes, corrupt, or password-protected. */
    NOT_A_VALID_ARCHIVE("FCT_TAMPERED"),
    /** The archive contains a nested archive. */
    NESTED_ARCHIVE("FCT_TAMPERED"),
    /** A decompression/size cap (ratio, per-entry, or total uncompressed) was exceeded. */
    DECOMPRESSION_LIMIT_EXCEEDED("FCT_TAMPERED"),
    /** The archive has no project.xml. */
    PROJECT_XML_MISSING("FCT_TAMPERED"),

    /** A CanConnections next/previous points at a non-existent Bp. */
    DANGLING_CAN_CONNECTION("FCT_INCOMPLETE_BASELINE"),
    /** A CAN segment has no COM. */
    CHAIN_NO_COM("FCT_INCOMPLETE_BASELINE"),
    /** A CAN segment has 2+ COMs that are not a recognized redundancy pair (MASTER/SLAVE or PRIMARY/SECONDARY). */
    MULTI_COM_NO_REDUNDANCY("FCT_INCOMPLETE_BASELINE"),
    /** Two entities share the same Id. */
    DUPLICATE_ENTITY_ID("FCT_INCOMPLETE_BASELINE"),
    /** An IoExb is in an unsupported mode (neither ACO nor DT — e.g. counting-head-output). */
    UNSUPPORTED_IOEXB_MODE("FCT_INCOMPLETE_BASELINE"),
    /**
     * One CAN segment's backplanes sit under more than one {@code <Station>}. Segments are grouped by
     * CanConnections alone, so nothing structurally confines one to a single station — but the station
     * is the location key every name-keyed lookup is scoped by, and a segment straddling two of them
     * has no single answer. Rejected explicitly rather than left to surface as a misleading
     * {@code MULTI_COM_NO_REDUNDANCY}, or to collapse two stations' COMs into a bogus redundant pair.
     */
    CHAIN_SPANS_STATIONS("FCT_INCOMPLETE_BASELINE");

    private final String externalCode;

    FctInvalidReason(String externalCode) {
        this.externalCode = externalCode;
    }

    public String externalCode() {
        return externalCode;
    }
}