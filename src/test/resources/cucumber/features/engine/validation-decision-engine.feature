Feature: Validation Decision Engine
  The ValidationDecisionEngine determines whether to APPLY_RULE, APPLY_DEFAULT, or IGNORE validation
  based on rule configuration, payload presence, file type, and COM file settings.

  Background:
    Given the validation engine is initialized

  # ==================== APPLY_RULE Scenarios ====================

  Scenario: Rule configured with payload present should apply rule
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
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule configured with no payload should still apply rule
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "NO"
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=false, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  # ==================== APPLY_DEFAULT Scenarios ====================

  Scenario: No rule with payload present should apply default
    Given I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_DEFAULT

  # ==================== IGNORE Scenarios ====================

  Scenario: No rule with no payload should ignore
    Given I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=false, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: Rule not applicable for file type should ignore
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
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: COM file with skipComFile=true should ignore
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "SkipComFile": true
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: COM file with skipComFile=null defaults to true and should ignore
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
        "ioexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: COM file with skipComFile=false should apply rule
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "SkipComFile": false
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Non-COM file with skipComFile=true should apply rule
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "YES",
          "SkipComFile": true
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE

  Scenario: Rule applicable for matching file type should apply rule
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
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": true,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, the configured rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_RULE
