package com.frauscher.ConfigurationValidationService.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.util.ExcelSummaryUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

	@PostMapping("/download")
	public ResponseEntity<byte[]> downloadReport(@RequestBody ValidationSummary validationSummary) {

		// -------------------------------------------------
	    // ValidationSummary validation
	    // -------------------------------------------------
	    if (validationSummary == null) {
	        throw new InvalidUserValidationInputException(
	                "ValidationSummary is required");
	    }
	    
	    // Validate that summary has content
	    if (validationSummary.getResults() == null || validationSummary.getResults().isEmpty()) {
	        throw new InvalidUserValidationInputException(
	                "ValidationSummary must contain validation results");
	    }
	    
	    byte[] excelOutput = ExcelSummaryUtil.generate(validationSummary);
	    return ResponseEntity.ok()
	        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"validation-summary.xlsx\"")
	        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
	        .body(excelOutput);

	}
}