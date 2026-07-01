package com.frauscher.ConfigurationValidationService.constants;

public final class ValidationConstants {
    public static final String CONFIG_BLOCK_OR_PARAM_NOT_FOUND = "CONFIG_BLOCK_OR_PARAM_NOT_FOUND";
    public static final String CONFIG_BLOCK_NOT_FOUND = "CONFIG_BLOCK_NOT_FOUND";
    public static final String CONFIG_BLOCK_FOUND = "CONFIG_BLOCK_FOUND";

    public static final String PROJECT_BLOCK_NOT_EXPECTED = "PROJECT_BLOCK_NOT_EXPECTED";
    public static final String PROJECT_BLOCK_NOT_CONFIGURED = "PROJECT_BLOCK_NOT_CONFIGURED";
    public static final String PROJECT_BLOCK_NOT_FOUND = "PROJECT_BLOCK_NOT_FOUND";
    public static final String PROJECT_ENTRY_NOT_FOUND = "PROJECT_ENTRY_NOT_FOUND";

    public static final String RANGE_NOT_CONFIGURED = "RANGE_NOT_CONFIGURED";

    // Instanced (v2) occurrence selection — set-equality / positional sentinels (VTF-336).
    public static final String EXPECTED_OCCURRENCE_NOT_FOUND = "EXPECTED_OCCURRENCE_NOT_FOUND";
    public static final String UNEXPECTED_OCCURRENCE = "UNEXPECTED_OCCURRENCE";

    // Cluster 1 (VTF-338) Check-A named verdicts — carried as sentinels in expected/actual (design §8.1).
    /** An uploaded AEB ADC whose [IDENTIFICATION] ID matches no AEB in the FCT-defined CAN segments. */
    public static final String ORPHANED = "ORPHANED";
    /** An in-scope block references an AEB id that does not exist in the design baseline. */
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    /** A SLCT_TIMEOUT actual of 2–7 — outside Phase 2's binary physical/virtual scope. */
    public static final String INVALID_SCOPE = "INVALID_SCOPE";
    /** A SLCT_TIMEOUT actual that is in scope (0/1) but not the expected physical/virtual value. */
    public static final String INVALID_VALUE = "INVALID_VALUE";

    public static final String UNIQUE = "UNIQUE";
    public static final String YES = "YES";
    public static final String NO = "NO";
}
