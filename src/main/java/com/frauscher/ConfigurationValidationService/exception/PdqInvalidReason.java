package com.frauscher.ConfigurationValidationService.exception;

/**
 * Internal diagnostic detail for a {@link PdqInvalidException}.
 *
 * <p>Drives logging and debugging only — it is <strong>never</strong> surfaced externally.
 * All PDQ parse/validation failures collapse to the single external code {@code PDQ_INVALID}
 * (see {@code fcvt-phase2-design.md} §6.7). BE-02 (VTF-332) extends this enum with the
 * ConfigControlTable / Data-Transmission variants (e.g. mixed-operator fadcAutoReset,
 * unsupported POSITION, present-but-empty DT sheet).</p>
 */
public enum PdqInvalidReason {

    /** Workbook stream could not be opened / read as an .xlsx. */
    WORKBOOK_UNREADABLE,

    /** A mandatory in-scope sheet (PDQ, CQ-IR, Control table) is missing. */
    SHEET_MISSING,

    /** A parametric CQ-IR row (non-empty Configuration Word) has an empty Response cell. */
    PARAMETRIC_ROW_EMPTY_RESPONSE,

    /** A time-valued field's value is not an exact multiple of its step. */
    VALUE_NOT_DIVISIBLE_BY_STEP,

    /** A field that must be numeric (step-divided or range) holds a non-numeric value. */
    NON_NUMERIC_VALUE,

    /** An enumerated lookup (e.g. INTERVAL ms -> code) found no mapping. */
    MAPPING_LOOKUP_FAILED,

    /** A range value (e.g. IDENTIFICATION) is not a well-formed "min to max". */
    RANGE_INVALID
}