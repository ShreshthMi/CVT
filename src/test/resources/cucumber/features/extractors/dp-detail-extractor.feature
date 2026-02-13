Feature: DP Detail Extractor Service
  The DpDetailExtractorService extracts Device/Data Point base configuration
  from parsed configuration files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract basic DP details from a config file
    Given I have a parsed config file "dp_001.ADC" with comDetails=false:
      """
      {
        "fileName": "dp_001.ADC",
        "id": 100,
        "comDetails": false,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "100", "comment": "DP_100"}
            ]
          },
          {
            "name": "CFG_SECTION",
            "blockIndex": 0,
            "entries": [
              {"key": "COMM_FAIL", "value": "0", "comment": ""},
              {"key": "BEHAV_GE", "value": "1", "comment": ""},
              {"key": "CLR_TRACK", "value": "1", "comment": ""}
            ]
          },
          {
            "name": "CFG_TIMEOUT",
            "blockIndex": 0,
            "entries": [
              {"key": "TIMEOUT_VALUE", "value": "50", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract DP details
    Then the extraction should return 1 DP detail(s)
