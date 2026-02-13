@engine
Feature: Rule Execution Engine
  The RuleExecutionEngine orchestrates the execution of validation rules,
  manages rule filtering based on file eligibility, and coordinates with the ValidationDecisionEngine.

  Background:
    Given the validation engine is initialized

  # Note: Rule execution engine requires complex integration with actual validation rules,
  # file contexts, and payload contexts. These scenarios focus on orchestration behavior
  # rather than individual rule logic (which is tested in the rules/ directory).

  Scenario: Engine with no configured rules returns empty results
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
    And I have a FileContext from the parsed file
    And I have a ValidationKey with block "TEST_BLOCK" and entry "TEST_KEY"
    And I have a ResolvedPayloadContext with no payload
    When I execute the rule execution engine
    Then the rule execution result should have ruleExecuted=false
    And the rule execution result should have 0 validation results

  Scenario: Engine filters out rules not applicable for file type
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
        "fileName": "non_track.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": false,
        "blocks": []
      }
      """
    And I have a FileContext from the parsed file
    And I have a ValidationKey with block "TEST_BLOCK" and entry "TEST_KEY"
    And I have a ResolvedPayloadContext with payload for the validation key
    When I execute the rule execution engine
    Then the rule execution result should have ruleExecuted=false
    And the rule execution result should have 0 validation results

  Scenario: Engine skips COM files when skipComFile is true
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
        "fileName": "com_file.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a FileContext from the parsed file
    And I have a ValidationKey with block "TEST_BLOCK" and entry "TEST_KEY"
    And I have a ResolvedPayloadContext with payload for the validation key
    When I execute the rule execution engine
    Then the rule execution result should have ruleExecuted=false
    And the rule execution result should have 0 validation results

  Scenario: Engine processes COM files when skipComFile is false
    Given I have the following configured rules:
      """
      [
        {
          "RuleType": "InputMatch",
          "ConfigBlockName": "TEST_BLOCK",
          "ConfigEntryKey": "TEST_KEY",
          "UIInputRequired": "NO",
          "SkipComFile": false
        }
      ]
      """
    And I have a parsed config file for engine testing:
      """
      {
        "fileName": "com_file.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "ioexbDetails": false,
        "comDetails": true,
        "blocks": [
          {
            "name": "TEST_BLOCK",
            "blockIndex": 0,
            "entries": [
              {"key": "TEST_KEY", "value": "expected_value", "comment": ""}
            ]
          }
        ]
      }
      """
    And I have a FileContext from the parsed file
    And I have a ValidationKey with block "TEST_BLOCK" and entry "TEST_KEY"
    And I have a ResolvedPayloadContext with payload for the validation key
    When I execute the rule execution engine
    Then the rule execution result should have ruleExecuted=true
