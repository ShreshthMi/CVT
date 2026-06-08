package com.frauscher.ConfigurationValidationService.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.service.fct.FctParsingService;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqParsingService;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * Drives {@code POST /api/config/v2/validate} through the full Spring context: the coupled-artifacts
 * gate (both / only-one / neither) and the v2 response shape. fctData/pdqData are built by parsing
 * the committed FCT and PDQ fixtures through the real services.
 */
@SpringBootTest
class ConfigValidationV2ControllerTest {

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
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FctParsingService fctParsingService;
    @Autowired
    private PdqParsingService pdqParsingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void bothInputsPresentRunsV2AndReturnsSummaryShape() throws Exception {
        String body = requestBody(fctData(), pdqData());
        mockMvc.perform(post("/api/config/v2/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation_results").isArray())
                .andExpect(jsonPath("$.dp_details").exists())
                .andExpect(jsonPath("$.ethernet_details").exists());
    }

    @Test
    void onlyFctPresentIsRejectedAsIncomplete() throws Exception {
        String body = requestBody(fctData(), null);
        mockMvc.perform(post("/api/config/v2/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PHASE2_INPUTS_INCOMPLETE"));
    }

    @Test
    void onlyPdqPresentIsRejectedAsIncomplete() throws Exception {
        String body = requestBody(null, pdqData());
        mockMvc.perform(post("/api/config/v2/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PHASE2_INPUTS_INCOMPLETE"));
    }

    @Test
    void neitherInputPresentIsRejectedAsIncomplete() throws Exception {
        String body = requestBody(null, null);
        mockMvc.perform(post("/api/config/v2/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PHASE2_INPUTS_INCOMPLETE"));
    }

    private ComAebMap fctData() {
        return fctParsingService.parse(FctFixtures.acoBytes());
    }

    private PdqUploadResponse pdqData() {
        return pdqParsingService.parse(new ByteArrayInputStream(PdqFixtures.workbookBytes()));
    }

    private String requestBody(ComAebMap fctData, PdqUploadResponse pdqData) throws Exception {
        Map<String, Object> userInput = new LinkedHashMap<>();
        if (fctData != null) {
            userInput.put("fctData", fctData);
        }
        if (pdqData != null) {
            userInput.put("pdqData", pdqData);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("parsedConfigFiles", objectMapper.readTree(PARSED_CONFIG_FILES));
        request.put("userInput", userInput);
        return objectMapper.writeValueAsString(request);
    }
}
