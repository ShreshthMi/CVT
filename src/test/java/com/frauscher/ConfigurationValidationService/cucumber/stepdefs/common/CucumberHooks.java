package com.frauscher.ConfigurationValidationService.cucumber.stepdefs.common;

import io.cucumber.java.After;
import io.cucumber.java.Before;

/**
 * Cucumber hooks for test setup and cleanup.
 */
public class CucumberHooks {

    @Before
    public void setUp() {
        TestContext.get().reset();
    }

    @After
    public void tearDown() {
        TestContext.clear();
    }
}
