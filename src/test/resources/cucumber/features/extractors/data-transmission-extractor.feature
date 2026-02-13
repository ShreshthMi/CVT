Feature: Data Transmission Extractor Service
  The DataTransmissionExtractorService extracts data transmission and safety-level configuration
  from IOEXB files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract data transmission details from IOEXB file
    Given I have a parsed config file "ioexb_trans_001.ADC" with ioexbDetails=true:
      """
      {
        "fileName": "ioexb_trans_001.ADC",
        "id": 800,
        "ioexbDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "800", "comment": "IOEXB_800"}
            ]
          },
          {
            "name": "CFG_DATA_SAFETY_LEVEL",
            "blockIndex": 0,
            "entries": [
              {"key": "SAFETY_LEVEL_IN", "value": "1", "comment": ""},
              {"key": "SAFETY_LEVEL_OUT", "value": "2", "comment": ""}
            ]
          },
          {
            "name": "CFG_DATA_OUT",
            "blockIndex": 0,
            "entries": [
              {"key": "SOURCE_DP_ID", "value": "900", "comment": ""},
              {"key": "NMBR_OUT", "value": "1", "comment": ""},
              {"key": "POSITION", "value": "0", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract data transmission details
    Then the extraction should return 1 data transmission detail(s)
