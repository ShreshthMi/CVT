Feature: Engine Layer Tests

  Background:
    Given the validation engine is running

  # ==================== RuleExecutionEngine Tests ====================

  Scenario: RuleExecutionEngine - should skip file when ValidateOnlyInFilesWith doesn't match
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {
          "RESET_OUT": "0"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "RESET_OUT",
          "UIInputRequired": "Yes",
          "ValidateOnlyInFilesWith": "TRACKSECTIONDETAILS"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.RESET_OUT = 0
      """
    And the file type is "ACOIOEXBDETAILS"
    When I validate the input
    Then no validation results should be returned

  Scenario: RuleExecutionEngine - should execute rule when ValidateOnlyInFilesWith matches ACOIOEXBDETAILS
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
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_SECTION_OUT",
          "ConfigEntryKey": "CLR_OCC",
          "UIInputRequired": "Yes",
          "ValidateOnlyInFilesWith": "ACOIOEXBDETAILS"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION_OUT.CLR_OCC = 0
      """
    And the file type is "ACOIOEXBDETAILS"
    When I validate the input
    Then the validation should pass for "CFG_SECTION_OUT.CLR_OCC"

  Scenario: RuleExecutionEngine - should execute rule when ValidateOnlyInFilesWith matches COMDETAILS
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

  Scenario: RuleExecutionEngine - should skip COM file when SkipComFile is true
    Given I have the following payload:
      """
      {
        "CFG_BEHAV_TGGL": {
          "BEHAV_RESET": "7"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BEHAV_TGGL",
          "ConfigEntryKey": "BEHAV_RESET",
          "UIInputRequired": "Yes",
          "SkipComFile": true
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    And the file is a COM file
    When I validate the input
    Then no validation results should be returned

  Scenario: RuleExecutionEngine - should execute on COM file when SkipComFile is false
    Given I have the following payload:
      """
      {
        "CFG_BEHAV_TGGL": {
          "BEHAV_RESET": "7"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BEHAV_TGGL",
          "ConfigEntryKey": "BEHAV_RESET",
          "UIInputRequired": "Yes",
          "SkipComFile": false
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    And the file is a COM file
    When I validate the input
    Then the validation should pass for "CFG_BEHAV_TGGL.BEHAV_RESET"

  Scenario: RuleExecutionEngine - should execute multiple rules for same key
    Given I have the following payload:
      """
      {
        "ID": {
          "ID": {
            "min": 1,
            "max": 4096
          }
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
        },
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID",
          "UIInputRequired": "No"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 100
      """
    And I have config file "test2.ADC" with content:
      """
      ID.ID = 100
      """
    When I validate the input
    Then the validation result count should be 2

  # ==================== DefaultRuleExecutor Tests ====================

  Scenario: DefaultRuleExecutor - should apply InputMatch as default when no rule configured
    Given I have the following payload:
      """
      {
        "UNCONFIGURED_BLOCK": {
          "SOME_KEY": "expectedValue"
        }
      }
      """
    And I have the following configured rules:
      """
      []
      """
    And I have config file "test.ADC" with content:
      """
      UNCONFIGURED_BLOCK.SOME_KEY = expectedValue
      """
    When I validate the input with default rules
    Then the validation should pass for "UNCONFIGURED_BLOCK.SOME_KEY"

  Scenario: DefaultRuleExecutor - default rule should fail when values mismatch
    Given I have the following payload:
      """
      {
        "UNCONFIGURED_BLOCK": {
          "SOME_KEY": "expectedValue"
        }
      }
      """
    And I have the following configured rules:
      """
      []
      """
    And I have config file "test.ADC" with content:
      """
      UNCONFIGURED_BLOCK.SOME_KEY = differentValue
      """
    When I validate the input with default rules
    Then the validation should fail for "UNCONFIGURED_BLOCK.SOME_KEY"
