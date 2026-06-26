package com.frauscher.ConfigurationValidationService.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.service.fct.FctParsingService;

import lombok.RequiredArgsConstructor;

/**
 * Phase 2 Baseline FCT upload endpoint. {@code POST /api/upload/fct} accepts a single {@code .fct2}
 * archive, parses {@code Project.xml} into the ComAebMap. Pre-business failures (missing/empty/wrong
 * type, oversize) surface as HTTP 400/413; parse/invariant failures collapse to {@code FCT_TAMPERED}
 * or {@code FCT_INCOMPLETE_BASELINE} (HTTP 400).
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FctUploadController {

    private final FctParsingService fctParsingService;

    @PostMapping(value = "/fct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComAebMap> uploadFct(@RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidUserValidationInputException("FCT2 archive file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".fct2")) {
            throw new InvalidUserValidationInputException("File must be an .fct2 archive");
        }

        try {
            return ResponseEntity.ok(fctParsingService.parse(file.getBytes()));
        } catch (IOException e) {
            throw new InvalidUserValidationInputException("Failed to read the uploaded file: " + e.getMessage());
        }
    }
}