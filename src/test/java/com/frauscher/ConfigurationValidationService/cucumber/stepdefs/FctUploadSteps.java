package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.beans.factory.annotation.Autowired;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.service.fct.FctParsingService;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Happy-path BDD steps for the FCT parser, driven against the real .fct2 fixtures. */
public class FctUploadSteps {

    @Autowired
    private FctParsingService fctParsingService;

    private byte[] archiveBytes;
    private ComAebMap result;

    @Given("the sample ACO FCT archive")
    public void theSampleAcoFctArchive() {
        archiveBytes = FctFixtures.acoBytes();
    }

    @Given("the redundant FCT archive")
    public void theRedundantFctArchive() {
        archiveBytes = FctFixtures.redundantBytes();
    }

    @When("the FCT archive is parsed")
    public void theFctArchiveIsParsed() {
        result = fctParsingService.parse(archiveBytes);
    }

    @Then("the ComAebMap has {int} chains")
    public void theComAebMapHasChains(int count) {
        assertEquals(count, result.chains().size());
    }

    @Then("a chain has COM {string}")
    public void aChainHasCom(String comName) {
        assertTrue(result.chains().stream().anyMatch(c -> comName.equals(c.com().comName())));
    }

    @Then("a chain is marked redundant")
    public void aChainIsMarkedRedundant() {
        assertTrue(result.chains().stream().anyMatch(Chain::redundantComPresent));
    }
}