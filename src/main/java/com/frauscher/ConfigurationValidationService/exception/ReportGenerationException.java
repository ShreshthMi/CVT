package com.frauscher.ConfigurationValidationService.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReportGenerationException extends ConfigValidationException {

	private static final long serialVersionUID = 1L;

	public ReportGenerationException(Throwable cause) {
		super("REPORT_GENERATION_FAILED", "Excel report generation failed");
		log.error("REPORT_GENERATION_FAILED", "Excel report generation failed", cause);
		initCause(cause);
	}
}
