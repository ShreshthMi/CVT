Feature: Multiple Block Rules

  Background:
    Given the validation engine is running

  # ==================== MultipleBlockSingleInputMatch Scenarios ====================

  Scenario: MultipleBlockSingleInputMatch - all matching should pass
    Given I have the following payload:
      """
      {
        "CFG_SECTION_OUT": {
          "CLR_OCC": "0"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockSingleInputMatch",
          "ConfigBlockName": "CFG_SECTION_OUT",
          "ConfigEntryKey": "CLR_OCC",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION_OUT.CLR_OCC = 0
      CFG_SECTION_OUT.CLR_OCC = 0
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION_OUT.CLR_OCC"

  Scenario: MultipleBlockSingleInputMatch - partial match should fail
    Given I have the following payload:
      """
      {
        "CFG_SECTION_OUT": {
          "CLR_OCC": "0"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockSingleInputMatch",
          "ConfigBlockName": "CFG_SECTION_OUT",
          "ConfigEntryKey": "CLR_OCC",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION_OUT.CLR_OCC = 0
      CFG_SECTION_OUT.CLR_OCC = 1
      """
    When I validate the input
    Then the validation should fail for "CFG_SECTION_OUT.CLR_OCC"

  # ==================== MultipleBlockMultipleInputMatch Scenarios ====================

  Scenario: MultipleBlockMultipleInputMatch - matching values should pass
    Given I have the following payload:
      """
      {
        "CFG_TIMEOUT": {
          "TIMEOUT_VALUE": ["1000", "2000", "3000"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockMultipleInputMatch",
          "ConfigBlockName": "CFG_TIMEOUT",
          "ConfigEntryKey": "TIMEOUT_VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_TIMEOUT.TIMEOUT_VALUE = 1000
      CFG_TIMEOUT.TIMEOUT_VALUE = 2000
      """
    When I validate the input
    Then the validation should pass for "CFG_TIMEOUT.TIMEOUT_VALUE"

  Scenario: MultipleBlockMultipleInputMatch - non-matching values should fail
    Given I have the following payload:
      """
      {
        "CFG_TIMEOUT": {
          "TIMEOUT_VALUE": ["1000", "2000", "3000"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockMultipleInputMatch",
          "ConfigBlockName": "CFG_TIMEOUT",
          "ConfigEntryKey": "TIMEOUT_VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_TIMEOUT.TIMEOUT_VALUE = 1000
      CFG_TIMEOUT.TIMEOUT_VALUE = 5000
      """
    When I validate the input
    Then the validation should fail for "CFG_TIMEOUT.TIMEOUT_VALUE"

  # ==================== MultipleBlock Rules Edge Cases ====================

  Scenario: MultipleBlockSingleInputMatch - all blocks match should pass
    Given I have the following payload:
      """
      {
        "CFG_SECTION_OUT": {
          "CLR_OCC": "0"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockSingleInputMatch",
          "ConfigBlockName": "CFG_SECTION_OUT",
          "ConfigEntryKey": "CLR_OCC",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION_OUT.CLR_OCC = 0
      CFG_SECTION_OUT.CLR_OCC = 0
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION_OUT.CLR_OCC"

  Scenario: MultipleBlockSingleInputMatch - different values across blocks should fail
    Given I have the following payload:
      """
      {
        "CFG_SECTION_OUT": {
          "CLR_OCC": "0"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockSingleInputMatch",
          "ConfigBlockName": "CFG_SECTION_OUT",
          "ConfigEntryKey": "CLR_OCC",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION_OUT.CLR_OCC = 0
      CFG_SECTION_OUT.CLR_OCC = 1
      """
    When I validate the input
    Then the validation should fail for "CFG_SECTION_OUT.CLR_OCC"

  Scenario: MultipleBlockMultipleInputMatch - array payload with all matching should pass
    Given I have the following payload:
      """
      {
        "CFG_MULTI": {
          "VALUE": ["A", "B", "C"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockMultipleInputMatch",
          "ConfigBlockName": "CFG_MULTI",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_MULTI.VALUE = A
      CFG_MULTI.VALUE = B
      """
    When I validate the input
    Then the validation should pass for "CFG_MULTI.VALUE"

  Scenario: MultipleBlockMultipleInputMatch - array payload with partial match should fail
    Given I have the following payload:
      """
      {
        "CFG_MULTI": {
          "VALUE": ["A", "B", "C"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "MultipleBlockMultipleInputMatch",
          "ConfigBlockName": "CFG_MULTI",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_MULTI.VALUE = A
      CFG_MULTI.VALUE = D
      """
    When I validate the input
    Then the validation should fail for "CFG_MULTI.VALUE"
