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

import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;

/** Drives {@code POST /api/upload/fct} end-to-end against the real .fct2 fixtures. */
@SpringBootTest
class FctUploadControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void uploadsValidFctAndReturnsComAebMap() throws Exception {
        mockMvc.perform(multipart("/api/upload/fct").file(fct("fct-phase2-aco.fct2", FctFixtures.acoBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chains.length()").value(2))
                .andExpect(jsonPath("$.chains[0].com.comName").exists())
                .andExpect(jsonPath("$.chains[0].aebs[0].dpId").exists());
    }

    @Test
    void rejectsNonFct2Extension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/upload/fct").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTamperedArchive() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.fct2", "application/octet-stream", "not a zip".getBytes());
        mockMvc.perform(multipart("/api/upload/fct").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FCT_TAMPERED"));
    }

    @Test
    void rejectsIncompleteBaseline() throws Exception {
        mockMvc.perform(multipart("/api/upload/fct").file(fct("fct-phase2-duplicate-id.fct2", FctFixtures.duplicateIdBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FCT_INCOMPLETE_BASELINE"));
    }

    private static MockMultipartFile fct(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "application/octet-stream", bytes);
    }
}