Feature: FCT archive upload parsing

  Happy-path BDD coverage for the Baseline FCT parser, driven against the real .fct2 fixtures.
  Negative cases (tampered archive, duplicate ID, unsupported IoExb mode) are covered by the
  JUnit unit/service tests.

  Scenario: The ACO FCT archive parses into the ComAebMap
    Given the sample ACO FCT archive
    When the FCT archive is parsed
    Then the ComAebMap has 2 chains
    And a chain has COM "COM100"

  Scenario: The redundant FCT archive collapses the MASTER/SLAVE pair
    Given the redundant FCT archive
    When the FCT archive is parsed
    Then a chain is marked redundant