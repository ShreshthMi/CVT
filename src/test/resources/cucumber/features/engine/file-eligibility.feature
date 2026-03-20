@engine
Feature: File Eligibility for Rule Application
  The file eligibility mechanism determines whether a rule should be applied to a specific file
  based on the ValidateOnlyInFilesWith setting and file type markers.

  Background:
    Given the validation engine is initialized

  Scenario: Rule with no ValidateOnlyInFilesWith applies to all files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule with TRACKSECTIONDETAILS applies only to track section files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "TRACKSECTIONDETAILS"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "track.cfg",
        "id": 1,
        "trackSectionDetails": true,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule with TRACKSECTIONDETAILS ignores non-track section files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "TRACKSECTIONDETAILS"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "other.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: Rule with ACOIOEXBDETAILS applies only to ACO IOEXB files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "ACOIOEXBDETAILS"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "ioexb.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": true,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule with ACOIOEXBDETAILS ignores non-ACO IOEXB files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "ACOIOEXBDETAILS"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "other.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: Rule with COMDETAILS applies only to COM files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "COMDETAILS",
          "SkipComFile": false
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "com.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule with COMDETAILS ignores non-COM files
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "ValidateOnlyInFilesWith": "COMDETAILS"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "other.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE
