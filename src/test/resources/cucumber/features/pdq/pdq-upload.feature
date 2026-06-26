Feature: PDQ workbook upload parsing

  The PDQ upload parser turns the in-scope sheets of the PDQ workbook into the
  block-grouped cqIrParameters. Per-value transforms and negative cases (empty
  Response, value-not-divisible-by-step, unmapped INTERVAL) are covered precisely
  by the JUnit unit tests; this feature is the happy-path BDD coverage driven
  against the real sample workbook.

  Scenario: The sample PDQ workbook parses into the block-grouped cqIrParameters
    Given the sample PDQ workbook
    When the PDQ workbook is parsed
    Then the parsed AEB equipment version is "GS07"
    And cqIrParameters block "IDENTIFICATION" has "min" equal to "1"
    And cqIrParameters block "CFG_SECTION" has "COMM_FAIL" equal to "0"
    And cqIrParameters block "CFG_OCC" has "OCC_EXT" equal to "0"
    And cqIrParameters block "CFG_SUPERVIS_FMA2" has "RESET_TYPE" equal to "3"
    And cqIrParameters block "CFG_PROJECT_AEB" has "BLOCK_EXISTS" equal to "true"
    And cqIrParameters block "CFG_ZP" has "SUPERVIS_COUNT_LMT" equal to "0"

  Scenario: The sample PDQ workbook parses the control table and data transmission
    Given the sample PDQ workbook
    When the PDQ workbook is parsed
    Then the control table has 7 track sections and 8 DP rows
    And the data transmission has 2 data-safety rows and 2 output rows