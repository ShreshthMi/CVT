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
