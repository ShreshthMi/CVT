package com.frauscher.ConfigurationValidationService.cucumber.stepdefs;

import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.DataHelper;
import com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common.TestContext;
import com.frauscher.ConfigurationValidationService.dto.RuleExecutionResult;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.context.FileContext;
import com.frauscher.ConfigurationValidationService.validation.context.ResolvedPayloadContext;
import com.frauscher.ConfigurationValidationService.validation.context.ValidationKey;
import com.frauscher.ConfigurationValidationService.validation.engine.DefaultRuleExecutor;
import com.frauscher.ConfigurationValidationService.validation.engine.RuleExecutionEngine;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationContext;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecision;
import com.frauscher.ConfigurationValidationService.validation.spec.ValidationDecisionEngine;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Validation Engine Core testing
 */
public class EngineSteps {

    @Autowired
    private RuleExecutionEngine ruleExecutionEngine;

    @Autowired
    private DefaultRuleExecutor defaultRuleExecutor;

    // ==================== GIVEN STEPS ====================

    @Given("the validation engine is initialized")
    public void theValidationEngineIsInitialized() {
        // Engine is ready - autowired via Spring context
    }

    @Given("I have a parsed config file for engine testing:")
    public void iHaveAParsedConfigFileForEngineTesting(String jsonContent) throws Exception {
        ParsedConfigFile file = DataHelper.parseParsedConfigFile(jsonContent);
        TestContext.get().setParsedConfigFile(file);
    }

    @Given("the file has trackSectionDetails marker set to {word}")
    public void theFileHasTrackSectionDetailsMarker(String flag) {
        TestContext.get().getParsedConfigFile().setTrackSectionDetails(Boolean.parseBoolean(flag));
    }

    @Given("the file has ioexbDetails marker set to {word}")
    public void theFileHasIoexbDetailsMarker(String flag) {
        TestContext.get().getParsedConfigFile().setIoexbDetails(Boolean.parseBoolean(flag));
    }

    @Given("the file has comDetails marker set to {word}")
    public void theFileHasComDetailsMarker(String flag) {
        TestContext.get().getParsedConfigFile().setComDetails(Boolean.parseBoolean(flag));
    }

    @Given("I have a FileContext from the parsed file")
    public void iHaveAFileContextFromTheParsedFile() {
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();
        FileContext fileContext = new FileContext(file);
        TestContext.get().setFileContext(fileContext);
    }

    @Given("I have a ValidationKey with block {string} and entry {string}")
    public void iHaveAValidationKey(String block, String entry) {
        ValidationKey key = new ValidationKey(block, entry);
        TestContext.get().setValidationKey(key);
    }

    @Given("I have a ResolvedPayloadContext with no payload")
    public void iHaveAResolvedPayloadContextWithNoPayload() {
        ResolvedPayloadContext context = new ResolvedPayloadContext(new HashMap<>());
        TestContext.get().setResolvedPayloadContext(context);
    }

    @Given("I have a ResolvedPayloadContext with payload for the validation key")
    public void iHaveAResolvedPayloadContextWithPayload() {
        // This will be implemented based on specific test scenarios
        // For now, creating an empty context
        ResolvedPayloadContext context = new ResolvedPayloadContext(new HashMap<>());
        TestContext.get().setResolvedPayloadContext(context);
    }

    @Given("I have a ValidationContext with payloadPresent={word} and rule={word}")
    public void iHaveAValidationContext(String payloadPresentStr, String ruleStr) {
        boolean payloadPresent = Boolean.parseBoolean(payloadPresentStr);
        RuleConfig rule = "null".equals(ruleStr) ? null : TestContext.get().getRuleConfigs().get(0);
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();

        ValidationContext ctx = new ValidationContext(payloadPresent, rule, file);
        TestContext.get().setValidationContext(ctx);
    }

    @Given("I have a ValidationContext with payloadPresent={word}, no rule, and the parsed file")
    public void iHaveAValidationContextWithNoRule(String payloadPresentStr) {
        boolean payloadPresent = Boolean.parseBoolean(payloadPresentStr);
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();

        ValidationContext ctx = new ValidationContext(payloadPresent, null, file);
        TestContext.get().setValidationContext(ctx);
    }

    @Given("I have a ValidationContext with payloadPresent={word}, the configured rule, and the parsed file")
    public void iHaveAValidationContextWithRule(String payloadPresentStr) {
        boolean payloadPresent = Boolean.parseBoolean(payloadPresentStr);
        RuleConfig rule = TestContext.get().getRuleConfigs() != null && !TestContext.get().getRuleConfigs().isEmpty()
            ? TestContext.get().getRuleConfigs().get(0)
            : null;
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();

        ValidationContext ctx = new ValidationContext(payloadPresent, rule, file);
        TestContext.get().setValidationContext(ctx);
    }

    // ==================== WHEN STEPS ====================

    @When("I execute the rule execution engine")
    public void iExecuteTheRuleExecutionEngine() {
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();
        FileContext fileContext = TestContext.get().getFileContext();
        ValidationKey key = TestContext.get().getValidationKey();
        List<RuleConfig> rules = TestContext.get().getRuleConfigs();
        // RuleExecutionEngine expects a non-null list (can be empty)
        if (rules == null) {
            rules = new ArrayList<>();
        }
        ResolvedPayloadContext inputContext = TestContext.get().getResolvedPayloadContext();

        RuleExecutionResult result = ruleExecutionEngine.execute(file, fileContext, key, rules, inputContext);
        TestContext.get().setRuleExecutionResult(result);
    }

    @When("I execute the default rule executor")
    public void iExecuteTheDefaultRuleExecutor() {
        ParsedConfigFile file = TestContext.get().getParsedConfigFile();
        FileContext fileContext = TestContext.get().getFileContext();
        ValidationKey key = TestContext.get().getValidationKey();
        ResolvedPayloadContext inputContext = TestContext.get().getResolvedPayloadContext();

        List<ValidationResult> results = defaultRuleExecutor.executeDefault(file, fileContext, key, inputContext);
        TestContext.get().setValidationResults(results);
    }

    @When("I invoke the validation decision engine")
    public void iInvokeTheValidationDecisionEngine() {
        ValidationContext ctx = TestContext.get().getValidationContext();
        ValidationDecision decision = ValidationDecisionEngine.decide(ctx);
        TestContext.get().setValidationDecision(decision);
    }

    // ==================== THEN STEPS ====================

    @Then("the rule execution result should have ruleExecuted={word}")
    public void theRuleExecutionResultShouldHaveRuleExecuted(String flag) {
        RuleExecutionResult result = TestContext.get().getRuleExecutionResult();
        assertThat(result.isRuleExecuted()).isEqualTo(Boolean.parseBoolean(flag));
    }

    @Then("the rule execution result should have {int} validation results")
    public void theRuleExecutionResultShouldHaveValidationResults(int count) {
        RuleExecutionResult result = TestContext.get().getRuleExecutionResult();
        assertThat(result.getResults()).hasSize(count);
    }

    @Then("the rule should be filtered out due to file eligibility")
    public void theRuleShouldBeFilteredOut() {
        RuleExecutionResult result = TestContext.get().getRuleExecutionResult();
        assertThat(result.getResults()).isEmpty();
    }

    @Then("the default InputMatch rule should be executed")
    public void theDefaultInputMatchRuleShouldBeExecuted() {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getRuleType()).isEqualTo("InputMatch");
    }

    @Then("no validation results should be returned from default executor")
    public void noValidationResultsShouldBeReturnedFromDefaultExecutor() {
        List<ValidationResult> results = TestContext.get().getValidationResults();
        assertThat(results).isEmpty();
    }

    @Then("the validation decision should be {word}")
    public void theValidationDecisionShouldBe(String decision) {
        ValidationDecision actual = TestContext.get().getValidationDecision();
        assertThat(actual).isEqualTo(ValidationDecision.valueOf(decision));
    }
}
