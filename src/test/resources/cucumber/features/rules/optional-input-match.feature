Feature: OptionalInputMatch Rule

  Background:
    Given the validation engine is running

  # ==================== Basic OptionalInputMatch Scenarios ====================

  Scenario: OptionalInputMatch - matching values should pass
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {
          "RESET_OUT": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.RESET_OUT = 1
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION.RESET_OUT"

  Scenario: OptionalInputMatch - no payload value should skip validation
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {}
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.RESET_OUT = 1
      """
    When I validate the input
    Then no validation results should be returned

  # ==================== OptionalInputMatch Edge Cases ====================

  Scenario: OptionalInputMatch - array payload with matching value should pass
    Given I have the following payload:
      """
      {
        "CFG_MODE": {
          "MODE": ["0", "1", "2"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_MODE",
          "ConfigEntryKey": "MODE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_MODE.MODE = 1
      """
    When I validate the input
    Then the validation should pass for "CFG_MODE.MODE"

  Scenario: OptionalInputMatch - array payload with non-matching value should fail
    Given I have the following payload:
      """
      {
        "CFG_MODE": {
          "MODE": ["0", "1", "2"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_MODE",
          "ConfigEntryKey": "MODE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_MODE.MODE = 3
      """
    When I validate the input
    Then the validation should fail for "CFG_MODE.MODE"

  Scenario: OptionalInputMatch - config not found should fail
    Given I have the following payload:
      """
      {
        "CFG_OPTIONAL": {
          "PARAM": ["0", "1"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_OPTIONAL",
          "ConfigEntryKey": "PARAM",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_OPTIONAL.PARAM entry
      """
    When I validate the input
    Then the validation should fail for "CFG_OPTIONAL.PARAM"
