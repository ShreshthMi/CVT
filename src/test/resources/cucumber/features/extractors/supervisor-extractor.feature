Feature: Supervisor Extractor Service
  The SupervisorExtractorService extracts supervisor configuration for train detection systems
  from CHC files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract supervisor details from CHC file with FMA1
    Given I have a parsed config file "chc_sup_001.ADC" with comDetails=false:
      """
      {
        "fileName": "chc_sup_001.ADC",
        "id": 700,
        "comDetails": false,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "700", "comment": "CHC_700"}
            ]
          },
          {
            "name": "CFG_SUPERVIS_FMA1",
            "blockIndex": 0,
            "entries": [
              {"key": "CFG_SUPERVIS_FMA1", "value": "", "comment": "Supervisor_FMA1"},
              {"key": "RESET_TYPE", "value": "0", "comment": ""},
              {"key": "RESET_DELAY", "value": "5", "comment": ""},
              {"key": "ID", "value": "800", "comment": "TS_SUP_001"},
              {"key": "SECTION", "value": "1", "comment": ""},
              {"key": "LOGIC_TYPE", "value": "0", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract supervisor details
    Then the extraction should return 1 supervisor(s)
    And supervisor 0 should have supName "Supervisor_FMA1"
    And supervisor 0 should have dpId "700"
    And supervisor 0 should have dpName "CHC_700"
