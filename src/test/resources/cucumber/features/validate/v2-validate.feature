Feature: v2 validate endpoint (coupled-artifacts gate)

  Happy-path BDD for the Phase 2 validate path. Gate rejections (only one upload present, or
  neither) are covered by the JUnit/MockMvc endpoint tests, since the v2 request carries
  binary-derived fctData/pdqData that cannot be expressed inline in Gherkin.

  Scenario: With both FCT and PDQ present, the v2 path returns a validation summary
    Given a v2 validation input built from the sample FCT and PDQ
    When the v2 validation runs over a parsed config file
    Then a validation summary is returned with empty results
