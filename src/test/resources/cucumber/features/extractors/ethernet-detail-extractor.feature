Feature: Ethernet Detail Extractor Service
  The EthernetDetailExtractorService extracts Ethernet network communication configuration
  from COM files.

  Background:
    Given the extractor service is initialized

  Scenario: Extract ethernet details from COM file
    Given I have a parsed config file "com_001.ADC" with comDetails=true:
      """
      {
        "fileName": "com_001.ADC",
        "id": 200,
        "comDetails": true,
        "blocks": [
          {
            "name": "ID",
            "blockIndex": 0,
            "entries": [
              {"key": "ID", "value": "200", "comment": "COM1"}
            ]
          },
          {
            "name": "CFG_MY_IP_NW1",
            "blockIndex": 0,
            "entries": [
              {"key": "MY_IP_NW1_B1", "value": "192", "comment": ""},
              {"key": "MY_IP_NW1_B2", "value": "168", "comment": ""},
              {"key": "MY_IP_NW1_B3", "value": "1", "comment": ""},
              {"key": "MY_IP_NW1_B4", "value": "100", "comment": ""}
            ]
          }
        ]
      }
      """
    When I extract ethernet details
    Then the extraction should return 1 ethernet detail(s)
