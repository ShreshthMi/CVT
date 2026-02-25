Feature: Exception Coverage

  Background:
    Given the validation engine is running

  # ==================== InvalidUserValidationInputException ====================

  # Note: These exception scenarios are designed to test API-level validation
  # However, the current test framework bypasses API exceptions by calling
  # validation logic directly. These scenarios would require integration tests
  # to properly test exception throwing behavior.

  # Scenario: InvalidUserValidationInputException - missing files should fail
  #   Given I have the following payload:
  #     """
  #     {
  #       "CFG_BLOCK": {
  #         "KEY": "value"
  #       }
  #     }
  #     """
  #   And I have the following configured rules:
  #     """
  #     [
  #       {
  #         "RuleType": "InputMatch",
  #         "ConfigBlockName": "CFG_BLOCK",
  #         "ConfigEntryKey": "KEY",
  #         "UIInputRequired": "Yes"
  #       }
  #     ]
  #     """
  #   And I have no config files
  #   When I validate the input
  #   Then an exception of type "InvalidUserValidationInputException" should be thrown
  #   And the exception message should contain "At least one config file is required"

  # Scenario: InvalidUserValidationInputException - missing payload part should fail
  #   Given I have the following configured rules:
  #     """
  #     [
  #       {
  #         "RuleType": "InputMatch",
  #         "ConfigBlockName": "CFG_BLOCK",
  #         "ConfigEntryKey": "KEY",
  #         "UIInputRequired": "Yes"
  #       }
  #     ]
  #     """
  #   And I have config file "test.ADC" with content:
  #     """
  #     CFG_BLOCK.KEY = value
  #     """
  #   And I have the following payload:
  #     """
  #     {}
  #     """
  #   When I validate the input
  #   Then an exception of type "InvalidUserValidationInputException" should be thrown
  #   And the exception message should contain "Payload sections must not be empty"

  # Scenario: InvalidUserValidationInputException - empty payload sections should fail
  #   Given I have the following payload:
  #     """
  #     {}
  #     """
  #   And I have the following configured rules:
  #     """
  #     [
  #       {
  #         "RuleType": "InputMatch",
  #         "ConfigBlockName": "CFG_BLOCK",
  #         "ConfigEntryKey": "KEY",
  #         "UIInputRequired": "Yes"
  #       }
  #     ]
  #     """
  #   And I have config file "test.ADC" with content:
  #     """
  #     CFG_BLOCK.KEY = value
  #     """
  #   When I validate the input
  #   Then an exception of type "InvalidUserValidationInputException" should be thrown
  #   And the exception message should contain "Payload sections must not be empty"

  Scenario: InvalidUserValidationInputException - UIInputRequired missing payload should fail
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
    And the exception message should contain "payload input required for CFG_BLOCK::KEY"

  Scenario: InvalidUserValidationInputException - RangeCheck missing min max should fail
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

  Scenario: InvalidUserValidationInputException - OptionalInputMatch non-array should fail
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

  Scenario: InvalidUserValidationInputException - ProjectBlockCheck missing boolean should fail
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
    And the exception message should contain "ProjectBlockCheck requires BLOCK_EXISTS boolean"

  # ==================== RuleConfigurationException ====================

  Scenario: RuleConfigurationException - missing ruleType should fail
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

  Scenario: RuleConfigurationException - missing ConfigBlockName should fail
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

  Scenario: RuleConfigurationException - missing ConfigEntryKey should fail
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

  Scenario: RuleConfigurationException - missing UIInputRequired for configured rule should fail
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

  Scenario: RuleConfigurationException - invalid ValidateOnlyInFilesWith should fail
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

  Scenario: RuleConfigurationException - DuplicateCheck with UIInputRequired Yes should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "DuplicateCheck",
          "ConfigBlockName": "CFG_BLOCK",
          "ConfigEntryKey": "KEY",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "UIInputRequired must be NO for DuplicateCheck"

  Scenario: RuleConfigurationException - RangeCheck missing min should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_RANGE",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "No",
          "max": 100
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "RangeCheck requires both min and max"

  Scenario: RuleConfigurationException - RangeCheck min >= max should fail
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "RangeCheck",
          "ConfigBlockName": "CFG_RANGE",
          "ConfigEntryKey": "VALUE",
          "UIInputRequired": "No",
          "min": 100,
          "max": 50
        }
      ]
      """
    And rules are marked as configured
    When I validate the rule configuration
    Then an exception of type "RuleConfigurationException" should be thrown
    And the exception message should contain "RangeCheck requires min < max"

  Scenario: RuleConfigurationException - duplicate rule should fail
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

  
  # ==================== FileParsingException ====================
  # Note: FileParsingException is thrown during config file parsing, but
  # the test framework bypasses this by providing pre-parsed config data.
  # This scenario would require integration testing to properly test file parsing.

  # Scenario: FileParsingException - invalid config file should fail
  #   Given I have the following payload:
  #     """
  #     {
  #       "CFG_BLOCK": {
  #         "KEY": "value"
  #       }
  #     }
  #     """
  #   And I have the following configured rules:
  #     """
  #     [
  #       {
  #         "RuleType": "InputMatch",
  #         "ConfigBlockName": "CFG_BLOCK",
  #         "ConfigEntryKey": "KEY",
  #         "UIInputRequired": "Yes"
  #       }
  #     ]
  #     """
  #   And I have config file "invalid.txt" with content:
  #     """
  #     This is not a valid config file format
  #     @#$%^&*()
  #     """
  #   When I validate the input
  #   Then an exception of type "FileParsingException" should be thrown
  #   And the exception message should contain "Failed to parse config file"

 
