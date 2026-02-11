package com.frauscher.ConfigurationValidationService.cucumber;


import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import com.frauscher.ConfigurationValidationService.ConfigurationValidationServiceApplication;

@CucumberContextConfiguration
@SpringBootTest(classes = ConfigurationValidationServiceApplication.class)
public class CucumberSpringConfiguration {
}
