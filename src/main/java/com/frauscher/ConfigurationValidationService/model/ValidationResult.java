package com.frauscher.ConfigurationValidationService.model;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One validation-log entry. The v2 path additionally assigns a response-scoped opaque {@code id}
 * (the navigation target a highlighted detail cell links to — FCVT-v2-Validation-Response-Contract.md §2/§5);
 * it is {@code null} on the Phase 1 path and omitted from that response ({@link JsonInclude.Include#NON_NULL}),
 * so a Phase 1 response is byte-unchanged.
 */
@Getter
@NoArgsConstructor
@JsonPropertyOrder({"id", "fileName", "ruleType", "blockName", "entryKey", "expectedValue", "actualValue", "status"})
public class ValidationResult {

    /** Opaque, response-scoped id (e.g. {@code "r0".."rN"}); assigned by the v2 path, {@code null} in Phase 1. */
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String id;

    private String fileName;
    private String ruleType;
    private String blockName;
    private String entryKey;
    private String expectedValue;
    private String actualValue;
    private String status; // PASS / FAIL

    public ValidationResult(String fileName, String ruleType, String blockName, String entryKey,
            String expectedValue, String actualValue, String status) {
        this.fileName = fileName;
        this.ruleType = ruleType;
        this.blockName = blockName;
        this.entryKey = entryKey;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.status = status;
    }
}
