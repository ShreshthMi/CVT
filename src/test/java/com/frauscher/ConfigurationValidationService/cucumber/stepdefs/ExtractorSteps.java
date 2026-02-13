package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.DataHelper;
import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.TestContext;
import com.frauscher.ConfigurationValidationService.model.*;
import com.frauscher.ConfigurationValidationService.service.extractors.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Extractor Service testing
 */
public class ExtractorSteps {

    @Autowired
    private TrackSectionExtractorService trackSectionExtractorService;

    @Autowired
    private SupervisorExtractorService supervisorExtractorService;

    @Autowired
    private IOEXBAcoExtractorService ioexbAcoExtractorService;

    @Autowired
    private IOEXBBehaviourExtractorService ioexbBehaviourExtractorService;

    @Autowired
    private CHCExtractorService chcExtractorService;

    @Autowired
    private DpDetailExtractorService dpDetailExtractorService;

    @Autowired
    private DataTransmissionExtractorService dataTransmissionExtractorService;

    @Autowired
    private EthernetDetailExtractorService ethernetDetailExtractorService;

    // ==================== GIVEN STEPS ====================

    @Given("the extractor service is initialized")
    public void theExtractorServiceIsInitialized() {
        // Services are autowired via Spring context
    }

    @Given("I have a parsed config file {string} with trackSectionDetails={word}:")
    public void iHaveAParsedConfigFileWithTrackSectionDetails(String fileName, String flag, String jsonContent) throws Exception {
        ParsedConfigFile file = DataHelper.parseParsedConfigFile(jsonContent);
        file.setFileName(fileName);
        file.setTrackSectionDetails(Boolean.parseBoolean(flag));
        TestContext.get().addParsedConfigFile(file);
    }

    @Given("I have a parsed config file {string} with ioexbDetails={word}:")
    public void iHaveAParsedConfigFileWithIoexbDetails(String fileName, String flag, String jsonContent) throws Exception {
        ParsedConfigFile file = DataHelper.parseParsedConfigFile(jsonContent);
        file.setFileName(fileName);
        file.setIoexbDetails(Boolean.parseBoolean(flag));
        TestContext.get().addParsedConfigFile(file);
    }

    @Given("I have a parsed config file {string} with comDetails={word}:")
    public void iHaveAParsedConfigFileWithComDetails(String fileName, String flag, String jsonContent) throws Exception {
        ParsedConfigFile file = DataHelper.parseParsedConfigFile(jsonContent);
        file.setFileName(fileName);
        file.setComDetails(Boolean.parseBoolean(flag));
        TestContext.get().addParsedConfigFile(file);
    }

    // ==================== WHEN STEPS ====================

    @When("I extract track section details")
    public void iExtractTrackSectionDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<TrackSectionDetail> results = trackSectionExtractorService.extractTrackSectionDetails(files);
        TestContext.get().setTrackSectionDetails(results);
    }

    @When("I extract supervisor details")
    public void iExtractSupervisorDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<SupervisorDetail> results = supervisorExtractorService.extractSupervisorDetails(files);
        TestContext.get().setSupervisorDetails(results);
    }

    @When("I extract IOEXB ACO details")
    public void iExtractIOEXBACODetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<IOEXBAcoDetail> results = ioexbAcoExtractorService.extractIOEXBAcoDetails(files);
        TestContext.get().setIoexbAcoDetails(results);
    }

    @When("I extract IOEXB behaviour details")
    public void iExtractIOEXBBehaviourDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<IOEXBBehaviourDetail> results = ioexbBehaviourExtractorService.extractIOEXBBehaviourDetails(files);
        TestContext.get().setIoexbBehaviourDetails(results);
    }

    @When("I extract CHC details")
    public void iExtractCHCDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<CHCDetail> results = chcExtractorService.extractCHCDetails(files);
        TestContext.get().setChcDetails(results);
    }

    @When("I extract DP details")
    public void iExtractDPDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<DpDetail> results = dpDetailExtractorService.extractDpDetails(files);
        TestContext.get().setDpDetails(results);
    }

    @When("I extract data transmission details")
    public void iExtractDataTransmissionDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<DataTransmissionDetail> results = dataTransmissionExtractorService.extractDataTransmissionDetails(files);
        TestContext.get().setDataTransmissionDetails(results);
    }

    @When("I extract ethernet details")
    public void iExtractEthernetDetails() {
        List<ParsedConfigFile> files = TestContext.get().getParsedConfigFiles();
        List<EthernetDetail> results = ethernetDetailExtractorService.extractEthernetDetails(files);
        TestContext.get().setEthernetDetails(results);
    }

    // ==================== THEN STEPS - Track Section ====================

    @Then("the extraction should return {int} track section\\(s)")
    public void theExtractionShouldReturnTrackSections(int count) {
        List<TrackSectionDetail> results = TestContext.get().getTrackSectionDetails();
        assertThat(results).hasSize(count);
    }

    @Then("track section {int} should have tsName {string}")
    public void trackSectionShouldHaveTsName(int index, String expectedName) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getTsName()).isEqualTo(expectedName);
    }

    @Then("track section {int} should have fma {string}")
    public void trackSectionShouldHaveFma(int index, String expectedFma) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getFma()).isEqualTo(expectedFma);
    }

    @Then("track section {int} should have dpId {string}")
    public void trackSectionShouldHaveDpId(int index, String expectedDpId) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getDpId()).isEqualTo(expectedDpId);
    }

    @Then("track section {int} should have dpName {string}")
    public void trackSectionShouldHaveDpName(int index, String expectedDpName) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getDpName()).isEqualTo(expectedDpName);
    }

    @Then("track section {int} should have chDpId containing {string}")
    public void trackSectionShouldHaveChDpIdContaining(int index, String expectedId) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getChDpId()).contains(expectedId);
    }

    @Then("track section {int} should have chSlctTimeout containing {string}")
    public void trackSectionShouldHaveChSlctTimeoutContaining(int index, String expectedTimeout) {
        TrackSectionDetail detail = TestContext.get().getTrackSectionDetails().get(index);
        assertThat(detail.getChSlctTimeout()).contains(expectedTimeout);
    }

    // ==================== THEN STEPS - Supervisor ====================

    @Then("the extraction should return {int} supervisor\\(s)")
    public void theExtractionShouldReturnSupervisors(int count) {
        List<SupervisorDetail> results = TestContext.get().getSupervisorDetails();
        assertThat(results).hasSize(count);
    }

    @Then("supervisor {int} should have supName {string}")
    public void supervisorShouldHaveSupName(int index, String expectedName) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getSupName()).isEqualTo(expectedName);
    }

    @Then("supervisor {int} should have dpId {string}")
    public void supervisorShouldHaveDpId(int index, String expectedDpId) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getDpId()).isEqualTo(expectedDpId);
    }

    @Then("supervisor {int} should have dpName {string}")
    public void supervisorShouldHaveDpName(int index, String expectedDpName) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getDpName()).isEqualTo(expectedDpName);
    }

    @Then("supervisor {int} should have supByTs containing {string}")
    public void supervisorShouldHaveSupByTsContaining(int index, String expectedTs) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getSupByTs()).contains(expectedTs);
    }

    @Then("supervisor {int} should have supByTsFma containing {string}")
    public void supervisorShouldHaveSupByTsFmaContaining(int index, String expectedFma) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getSupByTsFma()).contains(expectedFma);
    }

    @Then("supervisor {int} should have timeOut containing {string}")
    public void supervisorShouldHaveTimeOutContaining(int index, String expectedTimeout) {
        SupervisorDetail detail = TestContext.get().getSupervisorDetails().get(index);
        assertThat(detail.getTimeOut()).contains(expectedTimeout);
    }

    // ==================== THEN STEPS - IOEXB ACO ====================

    @Then("the extraction should return {int} IOEXB ACO detail\\(s)")
    public void theExtractionShouldReturnIOEXBACODetails(int count) {
        List<IOEXBAcoDetail> results = TestContext.get().getIoexbAcoDetails();
        assertThat(results).hasSize(count);
    }

    @Then("IOEXB ACO detail {int} should have dpId {string}")
    public void ioexbAcoDetailShouldHaveDpId(int index, String expectedDpId) {
        IOEXBAcoDetail detail = TestContext.get().getIoexbAcoDetails().get(index);
        assertThat(detail.getDpId()).isEqualTo(expectedDpId);
    }

    @Then("IOEXB ACO detail {int} should have acoFma1 {string}")
    public void ioexbAcoDetailShouldHaveAcoFma1(int index, String expectedAcoFma1) {
        IOEXBAcoDetail detail = TestContext.get().getIoexbAcoDetails().get(index);
        assertThat(detail.getAcoFma1()).isEqualTo(expectedAcoFma1);
    }

    // ==================== THEN STEPS - Generic ====================

    @Then("the extraction should return {int} IOEXB behaviour detail\\(s)")
    public void theExtractionShouldReturnIOEXBBehaviourDetails(int count) {
        List<IOEXBBehaviourDetail> results = TestContext.get().getIoexbBehaviourDetails();
        assertThat(results).hasSize(count);
    }

    @Then("the extraction should return {int} CHC detail\\(s)")
    public void theExtractionShouldReturnCHCDetails(int count) {
        List<CHCDetail> results = TestContext.get().getChcDetails();
        assertThat(results).hasSize(count);
    }

    @Then("the extraction should return {int} DP detail\\(s)")
    public void theExtractionShouldReturnDPDetails(int count) {
        List<DpDetail> results = TestContext.get().getDpDetails();
        assertThat(results).hasSize(count);
    }

    @Then("the extraction should return {int} data transmission detail\\(s)")
    public void theExtractionShouldReturnDataTransmissionDetails(int count) {
        List<DataTransmissionDetail> results = TestContext.get().getDataTransmissionDetails();
        assertThat(results).hasSize(count);
    }

    @Then("the extraction should return {int} ethernet detail\\(s)")
    public void theExtractionShouldReturnEthernetDetails(int count) {
        List<EthernetDetail> results = TestContext.get().getEthernetDetails();
        assertThat(results).hasSize(count);
    }
}
