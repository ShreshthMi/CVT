package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import com.frauscher.ConfigurationValidationService.model.ConfigOptions;
import com.frauscher.ConfigurationValidationService.model.OptionMapping;
import com.frauscher.ConfigurationValidationService.service.ConfigOptionsService;
import com.frauscher.ConfigurationValidationService.controller.ConfigOptionsController;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigOptionsSteps {

    @Autowired
    private ConfigOptionsService configOptionsService;

    @Autowired
    private ConfigOptionsController configOptionsController;

    private List<ConfigOptions> configOptionsList;
    private ResponseEntity<List<ConfigOptions>> httpResponse;

    @Given("the config options service is initialized")
    public void theConfigOptionsServiceIsInitialized() {
        assertThat(configOptionsService).isNotNull();
    }

    @When("I request all config options")
    public void iRequestAllConfigOptions() {
        configOptionsList = configOptionsService.getConfigOptions();
    }

    @When("I call GET \\/v1\\/configoptions")
    public void iCallGetConfigOptions() {
        httpResponse = configOptionsController.getConfigOptions();
        configOptionsList = httpResponse.getBody();
    }

    @Then("the response should contain parameter groups")
    public void theResponseShouldContainParameterGroups() {
        assertThat(configOptionsList).isNotNull().isNotEmpty();
    }

    @Then("each parameter group should have a key and option mappings")
    public void eachParameterGroupShouldHaveKeyAndOptionMappings() {
        for (ConfigOptions option : configOptionsList) {
            assertThat(option.getKey()).isNotBlank();
            assertThat(option.getOptionMappings()).isNotNull().isNotEmpty();
        }
    }

    @Then("the parameter {string} should have {int} option mappings")
    public void theParameterShouldHaveOptionMappings(String paramKey, int count) {
        ConfigOptions param = findParam(paramKey);
        assertThat(param.getOptionMappings()).hasSize(count);
    }

    @Then("the parameter {string} should contain option {string} with value {string}")
    public void theParameterShouldContainOptionWithValue(String paramKey, String optionKey, String expectedValue) {
        ConfigOptions param = findParam(paramKey);
        OptionMapping match = param.getOptionMappings().stream()
                .filter(om -> om.getKey().equals(optionKey))
                .findFirst()
                .orElse(null);
        assertThat(match).as("Option '%s' in parameter '%s'", optionKey, paramKey).isNotNull();
        assertThat(match.getValue()).isEqualTo(expectedValue);
    }

    @Then("the HTTP response status should be {int}")
    public void theHttpResponseStatusShouldBe(int expectedStatus) {
        assertThat(httpResponse.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Then("the response body should be a non-empty JSON array")
    public void theResponseBodyShouldBeNonEmptyJsonArray() {
        assertThat(httpResponse.getBody()).isNotNull().isNotEmpty();
    }

    private ConfigOptions findParam(String paramKey) {
        ConfigOptions param = configOptionsList.stream()
                .filter(co -> co.getKey().equals(paramKey))
                .findFirst()
                .orElse(null);
        assertThat(param).as("Parameter '%s' not found", paramKey).isNotNull();
        return param;
    }
}
