package com.frauscher.ConfigurationValidationService.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;

/**
 * One failing-cell annotation carried on a detail row's {@code _mismatches} array
 * (FCVT-v2-Validation-Response-Contract.md §4). It pinpoints the cell, gives its Expected/Actual in
 * <b>display</b> form, and links to the validation-result log entry by {@code result_id}.
 *
 * <p>A cell is a failure <i>iff</i> it is referenced by one of these — a correct cell has no entry.
 * Three kinds:</p>
 * <ul>
 *   <li>{@code VALUE} — the cell is present but its value is wrong ({@code expected} + {@code actual} set).</li>
 *   <li>{@code UNEXPECTED} — the element is present but not in the baseline ({@code expected == null}).</li>
 *   <li>{@code MISSING} — the baseline expects an item that is absent ({@code actual == null}); for an
 *       array column it is rendered as an empty slot appended at a real {@code index}.</li>
 * </ul>
 * Nulls are emitted explicitly (no {@code @JsonInclude}) so the FE can read {@code kind} without inferring.
 */
@Getter
@JsonPropertyOrder({"field", "index", "kind", "expected", "actual", "result_id"})
public class MismatchAnnotation {

    /** Mismatch kind, per the contract. */
    public enum Kind { VALUE, UNEXPECTED, MISSING }

    @JsonProperty("field")
    private final String field;

    @JsonProperty("index")
    private final Integer index;

    @JsonProperty("kind")
    private final Kind kind;

    @JsonProperty("expected")
    private final String expected;

    @JsonProperty("actual")
    private final String actual;

    @JsonProperty("result_id")
    private final String resultId;

    public MismatchAnnotation(String field, Integer index, Kind kind, String expected, String actual, String resultId) {
        this.field = field;
        this.index = index;
        this.kind = kind;
        this.expected = expected;
        this.actual = actual;
        this.resultId = resultId;
    }

    /** A value check on a scalar cell ({@code index == null}) or an array element ({@code index} set). */
    public static MismatchAnnotation value(String field, Integer index, String expected, String actual, String resultId) {
        return new MismatchAnnotation(field, index, Kind.VALUE, expected, actual, resultId);
    }

    /** An array element present in the configuration but not in the baseline. */
    public static MismatchAnnotation unexpected(String field, Integer index, String actual, String resultId) {
        return new MismatchAnnotation(field, index, Kind.UNEXPECTED, null, actual, resultId);
    }

    /** A baseline-expected item that is absent; rendered as an empty slot appended at {@code index}. */
    public static MismatchAnnotation missing(String field, Integer index, String expected, String resultId) {
        return new MismatchAnnotation(field, index, Kind.MISSING, expected, null, resultId);
    }
}
