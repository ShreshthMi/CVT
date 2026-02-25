Feature: Config Options Lookup API

  Scenario: ConfigOptionsService loads all parameter groups from properties file
    Given the config options service is initialized
    When I request all config options
    Then the response should contain parameter groups
    And each parameter group should have a key and option mappings

  Scenario: ConfigOptionsService returns discrete option mappings
    Given the config options service is initialized
    When I request all config options
    Then the parameter "COMM_FAIL" should have 2 option mappings
    And the parameter "COMM_FAIL" should contain option "0" with value "0 - normal"
    And the parameter "COMM_FAIL" should contain option "1" with value "1 - fault"

  Scenario: ConfigOptionsService returns range-based parameter metadata
    Given the config options service is initialized
    When I request all config options
    Then the parameter "ID_MIN" should contain option "min" with value "1"
    And the parameter "ID_MIN" should contain option "max" with value "4095"
    And the parameter "ID_MIN" should contain option "step" with value "1"
    And the parameter "ID_MIN" should contain option "description" with value "Minimum sender ID of AEB boards to be checked"

  Scenario: ConfigOptionsService groups multi-value parameters correctly
    Given the config options service is initialized
    When I request all config options
    Then the parameter "BEHAV_RESET" should have 8 option mappings
    And the parameter "BEHAV_GE" should have 4 option mappings
    And the parameter "INTERVAL" should have 4 option mappings

  Scenario: ConfigOptionsController returns HTTP 200 with config options
    Given the config options service is initialized
    When I call GET /v1/configoptions
    Then the HTTP response status should be 200
    And the response body should be a non-empty JSON array
