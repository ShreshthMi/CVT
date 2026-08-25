Feature: IOEXB ACO Extractor Service
  The IOEXBAcoExtractorService extracts IOEXB Axle Counting Output (ACO) configuration
  from IOEXB files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract IOEXB ACO details from IOEXB file
    Given I have a parsed config file "ioexb_aco_001.ADC" with acoIoexbDetails=true:
      """
      {
        "fileName": "ioexb_aco_001.ADC",
        "id": 500,
        "acoIoexbDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "500", "comment": "IOEXB_500"}
            ]
          },
          {
            "name": "CFG_SECTION_OUT",
            "blockIndex": 0,
            "entries": [
              {"key": "CFG_SECTION_OUT", "value": "", "comment": "ACO_FMA1"},
              {"key": "CLR_OCC", "value": "0", "comment": ""},
              {"key": "SECTION", "value": "1", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract IOEXB ACO details
    Then the extraction should return 1 IOEXB ACO detail(s)
    And IOEXB ACO detail 0 should have dpId "500"
    And IOEXB ACO detail 0 should have acoFma1 "ACO_FMA1"
    And IOEXB ACO detail 0 should have slot "0"

  # An ACO card may drive the same track section from BOTH of its outputs. The two CFG_SECTION_OUT
  # blocks are distinct physical outputs, so each gets its own row -- the extractor used to skip the
  # second because its header comment repeated, which silently dropped a real output FMA and left the
  # slot with no cell for the mismatch annotator to mark.
  Scenario: A card driving one track section from both outputs yields a row per block
    Given I have a parsed config file "ioexb_aco_002.ADC" with acoIoexbDetails=true:
      """
      {
        "fileName": "ioexb_aco_002.ADC",
        "id": 501,
        "acoIoexbDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "501", "comment": "IOEXB_501"}
            ]
          },
          {
            "name": "CFG_SECTION_OUT",
            "blockIndex": 0,
            "entries": [
              {"key": "CFG_SECTION_OUT", "value": "", "comment": "250BT"},
              {"key": "CLR_OCC", "value": "0", "comment": ""},
              {"key": "SECTION", "value": "0", "comment": ""}
            ]
          },
          {
            "name": "CFG_SECTION_OUT",
            "blockIndex": 1,
            "entries": [
              {"key": "CFG_SECTION_OUT", "value": "", "comment": "250BT"},
              {"key": "CLR_OCC", "value": "0", "comment": ""},
              {"key": "SECTION", "value": "0", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract IOEXB ACO details
    Then the extraction should return 2 IOEXB ACO detail(s)
    And IOEXB ACO detail 0 should have acoFma1 "250BT"
    And IOEXB ACO detail 0 should have slot "0"
    And IOEXB ACO detail 1 should have acoFma1 "250BT"
    And IOEXB ACO detail 1 should have slot "1"
