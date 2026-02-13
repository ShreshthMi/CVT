Feature: IOEXB Behaviour Extractor Service
  The IOEXBBehaviourExtractorService extracts IOEXB behavior and input configuration
  from IOEXB files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract IOEXB behaviour from IOEXB file
    Given I have a parsed config file "ioexb_001.ADC" with ioexbDetails=true:
      """
      {
        "fileName": "ioexb_001.ADC",
        "id": 300,
        "ioexbDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "300", "comment": "IOEXB_300"}
            ]
          },
          {
            "name": "CFG_AXCNT",
            "blockIndex": 0,
            "entries": [
              {"key": "BEHAV_INPUT1", "value": "0", "comment": ""},
              {"key": "TYPE_IN1", "value": "1", "comment": ""},
              {"key": "BEHAV_INPUT2", "value": "1", "comment": ""},
              {"key": "TYPE_IN2", "value": "0", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract IOEXB behaviour details
    Then the extraction should return 1 IOEXB behaviour detail(s)
