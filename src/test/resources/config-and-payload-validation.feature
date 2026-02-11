Feature: Configuration and Payload Validation

  Background:
    Given the validation engine is running

  # ==================== DefaultRuleConfigValidator ====================

  Scenario: RuleConfigValidator - missing ruleType should fail
    Given I have the following configured rules:
      """
      [
        {
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "ruleType is mandatory"

  Scenario: RuleConfigValidator - missing ConfigBlockName should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "ConfigBlockName is mandatory"

  Scenario: RuleConfigValidator - missing ConfigEntryKey should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "ConfigEntryKey is mandatory"

  Scenario: RuleConfigValidator - configured rule missing UIInputRequired should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "UIInputRequired is mandatory"

  Scenario: RuleConfigValidator - invalid ValidateOnlyInFilesWith should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes",
          "ValidateOnlyInFilesWith": "INVALID"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "Invalid ValidateOnlyInFilesWith"

  Scenario: RuleConfigValidator - RangeCheck requires min and max
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_RANGE",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "No",
          "min": 0
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "RangeCheck requires both min and max"

  Scenario: RuleConfigValidator - RangeCheck min must be less than max
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_RANGE",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "No",
          "min": 10,
          "max": 5
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "RangeCheck requires min < max"

  Scenario: RuleConfigValidator - DuplicateCheck cannot require UI input
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "CFG_DUP",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "UIInputRequired must be NO for DuplicateCheck"

  Scenario: RuleConfigValidator - duplicate rule definitions should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        },
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "Duplicate rule detected"

  # ==================== DefaultPayloadValidator ====================

  Scenario: PayloadValidator - UIInputRequired missing payload should fail
    Given I have the following payload:
      """
      {
        "CFG_BLOCK": {}
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        }
      ]
      """
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "payload input required"

  Scenario: PayloadValidator - RangeCheck requires min max map
    Given I have the following payload:
      """
      {
        "CFG_RANGE": {
          "VALUE": "5"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_RANGE",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "RangeCheck requires {min, max}"

  Scenario: PayloadValidator - OptionalInputMatch requires array payload
    Given I have the following payload:
      """
      {
        "CFG_OPTIONAL": {
          "VALUE": "1"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_OPTIONAL",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "Array value required"

  Scenario: PayloadValidator - MultipleBlockMultipleInputMatch requires array payload
    Given I have the following payload:
      """
      {
        "CFG_MULTI": {
          "VALUE": "A"
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
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "Array value required"

  Scenario: PayloadValidator - ProjectBlockCheck requires BLOCK_EXISTS boolean
    Given I have the following payload:
      """
      {
        "CFG_PROJECT": {
          "PROJECT_NUMBER": "123"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "ProjectBlockCheck",
          "ConfigBlockName": "CFG_PROJECT",
          "ConfigEntryKey": "PROJECT_NUMBER",
          "UIInputRequired": "Yes"
        }
      ]
      """
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "ProjectBlockCheck requires BLOCK_EXISTS"

  Scenario: PayloadValidator - valid array payload passes
    Given I have the following payload:
      """
      {
        "CFG_OPTIONAL": {
          "VALUE": ["0", "1"]
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "OptionalInputMatch",
          "ConfigBlockName": "CFG_OPTIONAL",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "Yes"
        }
      ]
      """
    When I validate the payload against rules
    Then no validation results should be returned
