Feature: CHC Extractor Service
  The CHCExtractorService extracts Counting Head Configuration (CHC) details
  from CHC files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract CHC details from CHC file with one CFG_CONTROL block
    Given I have a parsed config file "chc_001.ADC" with comDetails=false:
      """
      {
        "fileName": "chc_001.ADC",
        "id": 400,
        "comDetails": false,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "400", "comment": "CHC_400"}
            ]
          },
          {
            "name": "CFG_ZP",
            "blockIndex": 0,
            "entries": [
              {"key": "INTERVAL", "value": "100", "comment": ""},
              {"key": "SUPERVIS_COUNT", "value": "5", "comment": ""},
              {"key": "SYSTEM_COUNT", "value": "10", "comment": ""}
            ]
          },
          {
            "name": "CFG_CONTROL",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "500", "comment": "TS_001"},
              {"key": "SLCT_TIMEOUT", "value": "1", "comment": ""},
              {"key": "SECTION", "value": "0", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract CHC details
    Then the extraction should return 1 CHC detail(s)
