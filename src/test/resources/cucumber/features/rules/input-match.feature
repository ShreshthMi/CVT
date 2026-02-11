Feature: InputMatch Rule

  Background:
    Given the validation engine is running

  # ==================== Basic InputMatch Scenarios ====================

  Scenario: InputMatch - matching values should pass
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
    When I validate the input
    Then the validation should pass for "CFG_BEHAV_TGGL.BEHAV_RESET"
    And the expected value should be "7"
    And the actual value should be "7"

  Scenario: InputMatch - non-matching values should fail
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
      CFG_BEHAV_TGGL.BEHAV_RESET = 8
      """
    When I validate the input
    Then the validation should fail for "CFG_BEHAV_TGGL.BEHAV_RESET"
    And the expected value should be "7"
    And the actual value should be "8"

  # ==================== InputMatch Edge Cases ====================

  Scenario: InputMatch - multiple config values in same file should all match
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {
          "PARAM": "100"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "PARAM",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.PARAM = 100
      CFG_SECTION.PARAM = 100
      CFG_SECTION.PARAM = 100
      """
    When I validate the input
    Then the validation should pass for "CFG_SECTION.PARAM"

  Scenario: InputMatch - multiple config values with one mismatch should fail
    Given I have the following payload:
      """
      {
        "CFG_SECTION": {
          "PARAM": "100"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_SECTION",
          "ConfigEntryKey": "PARAM",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SECTION.PARAM = 100
      CFG_SECTION.PARAM = 101
      CFG_SECTION.PARAM = 100
      """
    When I validate the input
    Then the validation should fail for "CFG_SECTION.PARAM"

  Scenario: InputMatch - special characters in payload and config should match
    Given I have the following payload:
      """
      {
        "CFG_SPECIAL": {
          "VALUE": "abc_123-XYZ"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_SPECIAL",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_SPECIAL.VALUE = abc_123-XYZ
      """
    When I validate the input
    Then the validation should pass for "CFG_SPECIAL.VALUE"
