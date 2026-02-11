Feature: RangeCheck Rule

  Background:
    Given the validation engine is running

  # ==================== Basic RangeCheck Scenarios ====================

  Scenario: RangeCheck - value within range should pass
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": {
            "min": 0,
            "max": 100
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 50
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: RangeCheck - value below minimum should fail
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": {
            "min": 10,
            "max": 100
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 5
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  Scenario: RangeCheck - value above maximum should fail
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": {
            "min": 0,
            "max": 100
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 150
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  # ==================== RangeCheck Edge Cases ====================

  Scenario: RangeCheck - negative range values should work
    Given I have the following payload:
      """
      {
        "CFG_TEMP": {
          "MIN_TEMP": {
            "min": -50,
            "max": 0
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_TEMP",
          "ConfigEntryKey": "MIN_TEMP",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_TEMP.MIN_TEMP = -25
      """
    When I validate the input
    Then the validation should pass for "CFG_TEMP.MIN_TEMP"

  Scenario: RangeCheck - value below negative min should fail
    Given I have the following payload:
      """
      {
        "CFG_TEMP": {
          "MIN_TEMP": {
            "min": -50,
            "max": 0
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_TEMP",
          "ConfigEntryKey": "MIN_TEMP",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_TEMP.MIN_TEMP = -75
      """
    When I validate the input
    Then the validation should fail for "CFG_TEMP.MIN_TEMP"

  Scenario: RangeCheck - zero value at boundary should pass
    Given I have the following payload:
      """
      {
        "CFG_TEMP": {
          "MAX_TEMP": {
            "min": 0,
            "max": 100
          }
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_TEMP",
          "ConfigEntryKey": "MAX_TEMP",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_TEMP.MAX_TEMP = 0
      """
    When I validate the input
    Then the validation should pass for "CFG_TEMP.MAX_TEMP"
