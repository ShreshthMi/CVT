package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.dto.Phase2ValidationInput;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.ConfigValidationV2Service;
import com.frauscher.ConfigurationValidationService.service.fct.FctParsingService;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqParsingService;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Happy-path BDD steps for the v2 validate path, exercising the gated service over the fixtures. */
public class ConfigValidationV2Steps {

    private static final String PARSED_CONFIG_FILES = """
            [
              {
                "fileName": "C0001_00.ADC",
                "id": 1,
                "blocks": [
                  {
                    "name": "ID", "blockIndex": 0, "sequenceNumber": 1,
                    "startSequenceNumber": 1, "endSequenceNumber": 1, "totalOccurrence": 1,
                    "entries": [
                      {"key": "ID", "bits": 12, "value": "1"},
                      {"key": "CHANNEL", "bits": 4, "value": "0"}
                    ]
                  }
                ],
                "trackSectionDetails": false, "acoIoexbDetails": false,
                "dtIoexbDetails": false, "comDetails": false
              }
            ]
            """;

    @Autowired
    private ConfigValidationV2Service configValidationV2Service;
    @Autowired
    private FctParsingService fctParsingService;
    @Autowired
    private PdqParsingService pdqParsingService;
    @Autowired
    private ObjectMapper objectMapper;

    private Phase2ValidationInput userInput;
    private ValidationSummary summary;

    @Given("a v2 validation input built from the sample FCT and PDQ")
    public void aV2ValidationInput() {
        userInput = new Phase2ValidationInput();
        userInput.setFctData(fctParsingService.parse(FctFixtures.acoBytes()));
        userInput.setPdqData(pdqParsingService.parse(new ByteArrayInputStream(PdqFixtures.workbookBytes())));
    }

    @When("the v2 validation runs over a parsed config file")
    public void theV2ValidationRuns() throws Exception {
        List<ParsedConfigFile> parsedConfigFiles =
                objectMapper.readValue(PARSED_CONFIG_FILES, new TypeReference<List<ParsedConfigFile>>() { });
        summary = configValidationV2Service.validate(parsedConfigFiles, userInput);
    }

    @Then("a validation summary is returned with empty results")
    public void aValidationSummaryIsReturned() {
        assertNotNull(summary);
        assertTrue(summary.getResults().isEmpty());
    }
}
