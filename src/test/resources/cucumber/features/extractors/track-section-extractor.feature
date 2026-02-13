Feature: Track Section Extractor Service
  The TrackSectionExtractorService extracts track section configuration details
  from track section files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract track section from FMA1 blocks
    Given I have a parsed config file "track_001.ADC" with trackSectionDetails=true:
      """
      {
        "fileName": "track_001.ADC",
        "id": 600,
        "trackSectionDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "600", "comment": "TS_001"}
            ]
          },
          {
            "name": "CFG_ZP_FMA1",
            "blockIndex": 0,
            "entries": [
              {"key": "CFG_ZP_FMA1", "value": "1", "comment": "TS_001"},
              {"key": "DIR_INV", "value": "0", "comment": ""},
              {"key": "ID", "value": "700", "comment": "CH_001"},
              {"key": "SLCT_TIMEOUT", "value": "1", "comment": ""}
            ]
          },
          {
            "name": "CFG_TIMEOUT",
            "blockIndex": 1,
            "entries": [
              {"key": "TIMEOUT_VALUE", "value": "100", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract track section details
    Then the extraction should return 1 track section(s)
    And track section 0 should have tsName "TS_001"
    And track section 0 should have fma "1"
    And track section 0 should have dpId "600"
    And track section 0 should have dpName "TS_001"
