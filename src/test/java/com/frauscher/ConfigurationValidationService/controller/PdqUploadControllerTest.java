package com.frauscher.ConfigurationValidationService.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * First servlet-stack (MockMvc) test in the repo: drives {@code POST /api/upload/pdq} end-to-end
 * against the real fixture workbook through the full Spring context. Builds MockMvc from the web
 * context (no Boot MockMvc slice, which moved packages in Spring Boot 4.0).
 */
@SpringBootTest
class PdqUploadControllerTest {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void uploadsValidPdqAndReturnsParsedJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", PdqFixtures.PDQ_WORKBOOK_FILE_NAME, XLSX_CONTENT_TYPE, PdqFixtures.workbookBytes());

        mockMvc.perform(multipart("/api/upload/pdq").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aebEquipmentVersion").value("GS07"))
                .andExpect(jsonPath("$.projectCode").exists())
                .andExpect(jsonPath("$.cqIrParameters.IDENTIFICATION.min").value("1"))
                .andExpect(jsonPath("$.cqIrParameters.CFG_SECTION.COMM_FAIL").value("0"))
                .andExpect(jsonPath("$.cqIrParameters.CFG_TIMEOUT.TIMEOUT_VALUE[0]").value("34"))
                .andExpect(jsonPath("$.cqIrParameters.CFG_PROJECT_AEB.BLOCK_EXISTS").value("true"))
                // GS07 -> version-aware key included
                .andExpect(jsonPath("$.cqIrParameters.CFG_ZP.SUPERVIS_COUNT_LMT").value("0"));
    }

    @Test
    void rejectsNonXlsxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/upload/pdq").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/upload/pdq"))
                .andExpect(status().isBadRequest());
    }
}