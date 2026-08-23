package com.frauscher.ConfigurationValidationService.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** Mutable so the Cluster 1 (BE-08) post-pass can refine a verdict into a named sentinel. */
    @Setter
    private String expectedValue;
    @Setter
    private String actualValue;
    @Setter
    private String status; // PASS / FAIL / INVALID

    /**
     * Also the Jackson creator: {@code fileName}/{@code ruleType}/{@code blockName}/{@code entryKey} have no
     * setter, so without this the {@code /api/report/download} round-trip (the FE POSTs the summary back)
     * silently drops those four columns from the regenerated workbook.
     */
    @JsonCreator
    public ValidationResult(
            @JsonProperty("fileName") String fileName,
            @JsonProperty("ruleType") String ruleType,
            @JsonProperty("blockName") String blockName,
            @JsonProperty("entryKey") String entryKey,
            @JsonProperty("expectedValue") String expectedValue,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("status") String status) {
        this.fileName = fileName;
        this.ruleType = ruleType;
        this.blockName = blockName;
        this.entryKey = entryKey;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.status = status;
    }
}
