package com.frauscher.ConfigurationValidationService.exception.handler;

import java.time.Instant;

import com.frauscher.ConfigurationValidationService.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.frauscher.ConfigurationValidationService.dto.ApiErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -----------------------------
    // Client / input errors
    // -----------------------------

    @ExceptionHandler(InvalidUserValidationInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(
            InvalidUserValidationInputException ex) {

        return ResponseEntity.badRequest().body(error(ex));
    }

    @ExceptionHandler(PdqInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handlePdqInvalid(
            PdqInvalidException ex) {

        // External response carries only the PDQ_INVALID code; the reason is for logs.
        log.warn("PDQ upload rejected [{}]", ex.getReason());
        return ResponseEntity.badRequest().body(error(ex));
    }

    @ExceptionHandler(FctInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleFctInvalid(
            FctInvalidException ex) {

        // External response carries one of the two FCT codes; the reason is for logs.
        log.warn("FCT upload rejected [{}]", ex.getReason());
        return ResponseEntity.badRequest().body(error(ex));
    }



    @ExceptionHandler(IncompleteBaselineInputException.class)
    public ResponseEntity<ApiErrorResponse> handleIncompleteBaselineInput(
            IncompleteBaselineInputException ex) {

        // Coupled-artifacts gate failure: surfaces PHASE2_INPUTS_INCOMPLETE.
        log.warn("v2 validate rejected [{}]", ex.getErrorCode());
        return ResponseEntity.badRequest().body(error(ex));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingMultipartPart(
            MissingServletRequestPartException ex) {

        ApiErrorResponse response = ApiErrorResponse.builder()
                .errorCode("INVALID_PAYLOAD")
                .message(
                        "Required multipart part '" +
                                ex.getRequestPartName() +
                                "' is missing"
                )
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(
            MaxUploadSizeExceededException ex) {

        ApiErrorResponse response = ApiErrorResponse.builder()
                .errorCode("PAYLOAD_TOO_LARGE")
                .message("Uploaded file exceeds the maximum allowed size")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    // -----------------------------
    // Rule / config errors
    // -----------------------------

    @ExceptionHandler(RuleConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleRuleConfig(
            RuleConfigurationException ex) {

        log.error("Rule configuration error", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(ex));
    }


    // -----------------------------
    // File / report errors
    // -----------------------------

    @ExceptionHandler(FileParsingException.class)
    public ResponseEntity<ApiErrorResponse> handleFileParsing(
            FileParsingException ex) {

        log.error("File parsing failed", ex);

        return ResponseEntity.badRequest()
                .body(error(ex));
    }

    // -----------------------------
    // Engine errors
    // -----------------------------

    @ExceptionHandler(ValidationEngineException.class)
    public ResponseEntity<ApiErrorResponse> handleEngine(
            ValidationEngineException ex) {

        log.error("Validation engine failure", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(ex));
    }


    // -----------------------------
    // Last-resort fallback
    // -----------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex) {

        log.error("Unexpected error", ex);

        ApiErrorResponse response = ApiErrorResponse.builder()
                .errorCode("UNEXPECTED_ERROR")
                .message("Unexpected error occurred")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }


    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {

        ApiErrorResponse response = ApiErrorResponse.builder()
                .errorCode("INVALID_PAYLOAD")
                .message("Invalid JSON in payload")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }


    // -----------------------------
    // Helper
    // -----------------------------

    private ApiErrorResponse error(ConfigValidationException ex) {
        return ApiErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}