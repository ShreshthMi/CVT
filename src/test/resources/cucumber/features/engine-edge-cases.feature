Feature: Edge Cases and Error Handling

  Background:
    Given the validation engine is running

  # ==================== Missing Payload Scenarios ====================

  Scenario: Validation with empty payload should skip validation
    Given I have the following payload:
      """
      {}
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BEHAV_TGGL",
          "ConfigEntryKey": "BEHAV_RESET",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    When I validate the payload against rules
    Then an exception of type "InvalidUserValidationInputException" should be thrown
    And the exception message should contain "payload input required for CFG_BEHAV_TGGL::BEHAV_RESET"

  Scenario: Validation with null block value should skip validation
    Given I have the following payload:
      """
      {
        "CFG_BEHAV_TGGL": null
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_BEHAV_TGGL",
          "ConfigEntryKey": "BEHAV_RESET",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    When I validate the input
    Then no validation results should be returned

  # ==================== Missing Config Block Scenarios ====================

  Scenario: InputMatch should fail when config block not found
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      OTHER_BLOCK.OTHER_KEY = value
      """
    When I validate the input
    Then the validation should fail for "CFG_BEHAV_TGGL.BEHAV_RESET"

  Scenario: RangeCheck should fail when config block not found
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
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      OTHER_BLOCK.OTHER_KEY = value
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  # ==================== Boundary Value Tests ====================

  Scenario: RangeCheck at minimum boundary should pass
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
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 1
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: RangeCheck at maximum boundary should pass
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
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 4096
      """
    When I validate the input
    Then the validation should pass for "ID.ID"

  Scenario: RangeCheck one below minimum should fail
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
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 0
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  Scenario: RangeCheck one above maximum should fail
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
          "RuleType": "RangeCheck",
          "ConfigBlockName": "ID",
          "ConfigEntryKey": "ID"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      ID.ID = 4097
      """
    When I validate the input
    Then the validation should fail for "ID.ID"

  # ==================== Multiple Config Files ====================

  Scenario: Validation across multiple config files - all pass
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test1.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    And I have config file "test2.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    And I have config file "test3.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    When I validate the input
    Then the validation should pass for "CFG_BEHAV_TGGL.BEHAV_RESET"

  Scenario: Validation across multiple config files - one fails
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test1.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    And I have config file "test2.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 5
      """
    When I validate the input
    Then the validation should fail for "CFG_BEHAV_TGGL.BEHAV_RESET"

  # ==================== Empty Config File ====================

  Scenario: Validation with empty config file should fail
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      """
    When I validate the input
    Then the validation should fail for "CFG_BEHAV_TGGL.BEHAV_RESET"

  # ==================== UIInputRequired Tests ====================

  Scenario: UIInputRequired Yes with payload should execute
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET = 7
      """
    When I validate the input
    Then the validation should pass for "CFG_BEHAV_TGGL.BEHAV_RESET"

  Scenario: UIInputRequired No should execute without UI payload
    Given I have the following payload:
      """
      {
        "OTHER_BLOCK": {
          "OTHER_KEY": "value"
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

  # ==================== Special Characters in Values ====================

  Scenario: Values with special characters should match correctly
    Given I have the following payload:
      """
      {
        "CFG_PROJECT": {
          "NAME": "Project-Name_123"
        }
      }
      """
    And I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "CFG_PROJECT",
          "ConfigEntryKey": "NAME",
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_PROJECT.NAME = Project-Name_123
      """
    When I validate the input
    Then the validation should pass for "CFG_PROJECT.NAME"

  # ==================== Whitespace Handling ====================

  Scenario: Values with leading/trailing whitespace in config should be trimmed
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
          "UIInputRequired": "Yes"
        }
      ]
      """
    And I have config file "test.ADC" with content:
      """
      CFG_BEHAV_TGGL.BEHAV_RESET =    7   
      """
    When I validate the input
    Then the validation should pass for "CFG_BEHAV_TGGL.BEHAV_RESET"
