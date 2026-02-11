Feature: ProjectBlockCheck Rule

  Background:
    Given the validation engine is running

  # ==================== Basic ProjectBlockCheck Scenarios ====================

  Scenario: ProjectBlockCheck - block exists and matches should pass
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": true,
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT_AEB.PROJECT_NUMBER = 1234
      """
    When I validate the input
    Then the validation should pass for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck - block exists but mismatch should fail
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": true,
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT_AEB.PROJECT_NUMBER = 5678
      """
    When I validate the input
    Then the validation should fail for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck - block not required but present should fail
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": false,
          "PROJECT_NUMBER": "9898"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT_AEB.PROJECT_NUMBER = 1234
      """
    When I validate the input
    Then the validation should fail for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck with ValidateOnlyInFilesWith - COM file validation
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_COM": {
          "BLOCK_EXISTS": true,
          "PROJECT_NUMBER": "9898"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_COM",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes",
          "ValidateOnlyInFilesWith": "COMDETAILS"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT_COM.PROJECT_NUMBER = 9898
      """
    And the file type is "COMDETAILS"
    When I validate the input
    Then the validation should pass for "CFG_PROJECT_COM.PROJECT_NUMBER"

  # ==================== ProjectBlockCheck Edge Cases ====================

  Scenario: ProjectBlockCheck - BLOCK_EXISTS=false and block not found should pass
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": false,
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_PROJECT_AEB entries
      """
    When I validate the input
    Then the validation should pass for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck - BLOCK_EXISTS=true but block missing should fail
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": true,
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_PROJECT_AEB entries
      """
    When I validate the input
    Then the validation should fail for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck - BLOCK_EXISTS=true with boolean string should work
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": "true",
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT_AEB.PROJECT_NUMBER = 1234
      """
    When I validate the input
    Then the validation should pass for "CFG_PROJECT_AEB.PROJECT_NUMBER"

  Scenario: ProjectBlockCheck - BLOCK_EXISTS=false with boolean string should work
    Given I have the following payload:
      """
      {
        "CFG_PROJECT_AEB": {
          "BLOCK_EXISTS": "false",
          "PROJECT_NUMBER": "1234"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT_AEB",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      # No CFG_PROJECT_AEB entries
      """
    When I validate the input
    Then the validation should pass for "CFG_PROJECT_AEB.PROJECT_NUMBER"
