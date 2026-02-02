package com.frauscher.ConfigurationValidationService;

import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConfigurationValidationServiceApplicationTests {

	@Test
	void contextLoads() {
		assertTrue(true == true);
	}

}
