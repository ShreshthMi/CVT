package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqParsingService;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Happy-path BDD steps for the PDQ upload parser, driven against the real fixture workbook through
 * {@link PdqParsingService}. (Negative/transform cases live in the JUnit unit tests — the binary
 * {@code .xlsx} input cannot be expressed inline in Gherkin.)
 */
public class PdqUploadSteps {

    @Autowired
    private PdqParsingService pdqParsingService;

    private byte[] workbookBytes;
    private PdqUploadResponse response;

    @Given("the sample PDQ workbook")
    public void theSamplePdqWorkbook() {
        workbookBytes = PdqFixtures.workbookBytes();
    }

    @When("the PDQ workbook is parsed")
    public void thePdqWorkbookIsParsed() {
        response = pdqParsingService.parse(new ByteArrayInputStream(workbookBytes));
    }

    @Then("the parsed AEB equipment version is {string}")
    public void theParsedAebEquipmentVersionIs(String expected) {
        assertEquals(expected, response.getAebEquipmentVersion());
    }

    @Then("cqIrParameters block {string} has {string} equal to {string}")
    public void cqIrParametersBlockHasEntry(String block, String key, String expected) {
        Map<String, Object> blockMap = response.getCqIrParameters().get(block);
        assertNotNull(blockMap, "missing block " + block);
        assertEquals(expected, blockMap.get(key));
    }

    @Then("cqIrParameters block {string} does not contain {string}")
    public void cqIrParametersBlockDoesNotContain(String block, String key) {
        Map<String, Object> blockMap = response.getCqIrParameters().get(block);
        assertNotNull(blockMap, "missing block " + block);
        assertFalse(blockMap.containsKey(key), "block " + block + " unexpectedly contains " + key);
    }
}