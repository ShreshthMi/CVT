Feature: OptionalInputMatchOrBlockNotFound Rule

  Background:
    Given the validation engine is running

  # ==================== Payload absent (optional skip) ====================

  Scenario: OptionalInputMatchOrBlockNotFound - no payload should skip validation
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
          "RuleType": "OptionalInputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "DefaultValue": "1"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_SECTION entries
      """
    When I validate the input
    Then no validation results should be returned

  # ==================== Block not found with DefaultValue ====================

  Scenario: OptionalInputMatchOrBlockNotFound - block not found with default value should pass
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
          "RuleType": "OptionalInputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "DefaultValue": "1"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_SECTION entries
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION.RESET_OUT"

  Scenario: OptionalInputMatchOrBlockNotFound - block not found with non-default value should fail
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {
          "RESET_OUT": "5"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "DefaultValue": "1"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_SECTION entries
      """
    When I validate the input
    Then the validation should fail for "CFG_SECTION.RESET_OUT"

  # ==================== Block found (delegates to OptionalInputMatch) ====================

  Scenario: OptionalInputMatchOrBlockNotFound - block found matching value should pass
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
          "RuleType": "OptionalInputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "DefaultValue": "1"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.RESET_OUT = 1
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION.RESET_OUT"

  Scenario: OptionalInputMatchOrBlockNotFound - block found non-matching value should fail
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
          "RuleType": "OptionalInputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "DefaultValue": "1"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.RESET_OUT = 3
      """
    When I validate the input
    Then the validation should fail for "CFG_SECTION.RESET_OUT"
