package com.frauscher.ConfigurationValidationService.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqParsingService;

import lombok.RequiredArgsConstructor;

/**
 * Phase 2 PDQ workbook upload endpoint. Kept separate from the Phase 1 {@link UploadController}
 * so the Phase 1 surface is untouched.
 *
 * <p>{@code POST /api/upload/pdq} accepts a single {@code .xlsx} part, parses the in-scope sheets,
 * and returns the parsed PDQ JSON. Pre-business-logic failures (missing/empty/wrong-type, oversize)
 * surface as HTTP 400/413; parse/validation failures collapse to {@code PDQ_INVALID} (HTTP 400).</p>
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class PdqUploadController {

    private final PdqParsingService pdqParsingService;

    @PostMapping(value = "/pdq", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdqUploadResponse> uploadPdq(@RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidUserValidationInputException("PDQ workbook file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new InvalidUserValidationInputException("File must be an .xlsx workbook");
        }

        try {
            return ResponseEntity.ok(pdqParsingService.parse(file.getInputStream()));
        } catch (IOException e) {
            throw new InvalidUserValidationInputException("Failed to read the uploaded file: " + e.getMessage());
        }
    }
}