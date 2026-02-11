Feature: DuplicateCheck Rule

  Background:
    Given the validation engine is running

  # ==================== Basic DuplicateCheck Scenarios ====================

  Scenario: DuplicateCheck - no duplicates should pass
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "unique"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No",
          "SkipComFile": false
        }
      ]
      """
    And I have config file "test1.ADC" with content:
      """
      ID.ID = 1
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: DuplicateCheck - duplicates found should fail
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "duplicate"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No",
          "SkipComFile": false
        }
      ]
      """
    And I have config file "test1.ADC" with content:
      """
      ID.ID = 1
      """
    And I have config file "test2.ADC" with content:
      """
      ID.ID = 1
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  # ==================== DuplicateCheck Edge Cases ====================

  Scenario: DuplicateCheck - first occurrence should pass
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "first"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 1
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: DuplicateCheck - second occurrence with same value should fail
    Given duplicate registry has "ID.ID" with value "1"
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "duplicate"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 1
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  Scenario: DuplicateCheck - different values across files should pass
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "different"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No"
        }
      ]
      """
    And I have config file "test1.ADC" with content:
      """
      ID.ID = 1
      """
    And I have config file "test2.ADC" with content:
      """
      ID.ID = 2
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: DuplicateCheck - empty config should skip validation
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": "empty"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No ID.ID entries
      """
    When I validate the input
    Then no validation results should be returned
