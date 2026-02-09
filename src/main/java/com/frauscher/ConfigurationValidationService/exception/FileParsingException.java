package com.frauscher.ConfigurationValidationService.exception;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileParsingException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public FileParsingException(String filename, Throwable cause) {
        super("FILE_PARSE_ERROR", "Failed to parse file: " + filename);
        log.error("FILE_PARSE_ERROR", "Failed to parse file: " + filename);
        initCause(cause);
    }
}