@engine
Feature: Default Rule Executor
  The DefaultRuleExecutor applies default InputMatch validation when no configured rules exist
  for a specific block/entry combination and payload is present.

  Background:
    Given the validation engine is initialized

  # Note: Default rule executor integration requires more complex setup with actual file parsing
  # These scenarios focus on the decision logic that determines when defaults should apply
  # Full integration testing of default rule execution is covered in rule-execution-engine.feature

  Scenario: No configured rule with payload present should trigger default rule application
    Given I have a parsed config file for engine testing:
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
    And I have a ValidationContext with payloadPresent=true, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_DEFAULT

  Scenario: No configured rule with no payload should not apply default rule
    Given I have a parsed config file for engine testing:
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
    And I have a ValidationContext with payloadPresent=false, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: COM file should skip default rule when skipComFile defaults to true
    Given I have a parsed config file for engine testing:
      """
      {
        "fileName": "test.cfg",
        "id": 1,
        "trackSectionDetails": false,
        "acoIoexbDetails": false,
        "dtIoexbDetails": false,
        "comDetails": true,
        "blocks": []
      }
      """
    And I have a ValidationContext with payloadPresent=true, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be IGNORE

  Scenario: Non-COM file with payload should apply default rule
    Given I have a parsed config file for engine testing:
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
    And I have a ValidationContext with payloadPresent=true, no rule, and the parsed file
    When I invoke the validation decision engine
    Then the validation decision should be APPLY_DEFAULT
