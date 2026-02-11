Feature: InputMatchOrBlockNotFound Rule

  Background:
    Given the validation engine is running

  # ==================== Basic InputMatchOrBlockNotFound Scenarios ====================

  Scenario: InputMatchOrBlockNotFound - matching values should pass
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 1
      """
    When I validate the input
    Then the validation should pass for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  Scenario: InputMatchOrBlockNotFound - block not found should pass
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_DIRDEP_OCC entries
      """
    When I validate the input
    Then the validation should pass for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  Scenario: InputMatchOrBlockNotFound - non-matching values should fail
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 2
      """
    When I validate the input
    Then the validation should fail for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  # ==================== InputMatchOrBlockNotFound Edge Cases ====================

  Scenario: InputMatchOrBlockNotFound - payload absent but block not found should pass
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_DIRDEP_OCC entries
      """
    When I validate the input
    Then the validation should pass for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  Scenario: InputMatchOrBlockNotFound - payload absent but block found should fail
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {}
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 1
      """
    When I validate the input
    Then the validation should fail for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  Scenario: InputMatchOrBlockNotFound - multiple config values all matching should pass
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 1
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 1
      """
    When I validate the input
    Then the validation should pass for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"

  Scenario: InputMatchOrBlockNotFound - multiple config values with mismatch should fail
    Given I have the following payload:
      """
      {
        "CFG_DIRDEP_OCC": {
          "FAILSAFE_OCC_FMA1": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatchOrBlockNotFound",
          "ConfigBlockName": "CFG_DIRDEP_OCC",
          "ConfigEntryKey": "FAILSAFE_OCC_FMA1",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 1
      CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1 = 2
      """
    When I validate the input
    Then the validation should fail for "CFG_DIRDEP_OCC.FAILSAFE_OCC_FMA1"
