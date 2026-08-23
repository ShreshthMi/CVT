package com.frauscher.ConfigurationValidationService.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation.Kind;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.validation.config.JacksonConfig;

/**
 * {@code POST /api/report/download} takes the {@link ValidationSummary} back as a {@code @RequestBody} — the
 * FE posts the validate response to it verbatim — so every field the exported workbook renders has to survive
 * a serialize/deserialize cycle through the application's own {@link ObjectMapper}.
 *
 * <p>Two v2 gaps were invisible without this test. {@link MismatchAnnotation} had no no-args constructor and
 * no {@code @JsonCreator}, and {@link JacksonConfig} replaces Boot's auto-configured mapper with a hand-built
 * one carrying no {@code ParameterNamesModule} — so the download failed outright as soon as any cell was
 * annotated. And {@link ValidationResult}'s {@code fileName}/{@code ruleType}/{@code blockName}/
 * {@code entryKey} are getter-only, so Jackson treated them as read-only and dropped them silently, blanking
 * four columns of the regenerated Validation Results sheet.</p>
 */
class ReportDownloadRoundTripTest {

    /** The real production mapper, not a default one — the defect lived in this configuration. */
    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    @Test
    void validationResultKeepsEveryColumnThroughTheRoundTrip() throws Exception {
        ValidationResult result = new ValidationResult(
                "C0727_00.ADC", "InputMatch", "CFG_SECTION", "RESET_OUT", "1", "2", "FAIL");
        result.setId("r0");

        ValidationResult back = reserialize(result, ValidationResult.class);

        assertEquals("r0", back.getId());
        assertEquals("C0727_00.ADC", back.getFileName());
        assertEquals("InputMatch", back.getRuleType());
        assertEquals("CFG_SECTION", back.getBlockName());
        assertEquals("RESET_OUT", back.getEntryKey());
        assertEquals("1", back.getExpectedValue());
        assertEquals("2", back.getActualValue());
        assertEquals("FAIL", back.getStatus());
    }

    @Test
    void mismatchAnnotationsSurviveTheRoundTrip() throws Exception {
        ValidationSummary summary = annotatedSummary();

        ValidationSummary back = reserialize(summary, ValidationSummary.class);

        List<MismatchAnnotation> mismatches = back.getChcDetails().get(0).getMismatches();
        assertNotNull(mismatches, "_mismatches must survive the report round-trip");
        assertEquals(2, mismatches.size());

        MismatchAnnotation value = mismatches.get(0);
        assertEquals("timeout_1", value.getField());
        assertEquals(Kind.VALUE, value.getKind());
        assertEquals("620", value.getExpected());
        assertEquals("1200", value.getActual());
        assertEquals("r0", value.getResultId());

        // UNEXPECTED carries a null expected by construction — the half of the contract UAT hit.
        MismatchAnnotation unexpected = mismatches.get(1);
        assertEquals("dp_name_2", unexpected.getField());
        assertEquals(Kind.UNEXPECTED, unexpected.getKind());
        assertNull(unexpected.getExpected());
        assertEquals("DP4B", unexpected.getActual());
    }

    /** A clean run must stay byte-identical to Phase 1 — {@code _mismatches} is {@code NON_EMPTY}. */
    @Test
    void cleanSummaryEmitsNoMismatchesProperty() throws Exception {
        ValidationSummary summary = new ValidationSummary();
        summary.setChcDetails(List.of(CHCDetail.builder().dpId("5").dpName("DP1A").build()));

        String json = mapper.writeValueAsString(summary);

        assertEquals(-1, json.indexOf("_mismatches"), "a clean run must not emit _mismatches");
    }

    private <T> T reserialize(T value, Class<T> type) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(value), type);
    }

    static ValidationSummary annotatedSummary() {
        CHCDetail chc = CHCDetail.builder()
                .dpId("5").dpName("DP1A")
                .tsName1("TS1").timeout1("1200").dpId1("7").dpName1("DP2A").fmaDtl1("1")
                .tsName2("TS2").timeout2("620").dpId2("9").dpName2("DP4B").fmaDtl2("2")
                .interval("100").supervisCount("2").systemCount("3").partialCount("1")
                .build();
        chc.setMismatches(List.of(
                MismatchAnnotation.value("timeout_1", null, "620", "1200", "r0"),
                MismatchAnnotation.unexpected("dp_name_2", null, "DP4B", "r1")));

        ValidationResult result = new ValidationResult(
                "C0727_00.ADC", "InputMatch", "CFG_CONTROL", "SLCT_TIMEOUT", "620", "1200", "FAIL");
        result.setId("r0");

        ValidationSummary summary = new ValidationSummary();
        summary.setResults(List.of(result));
        summary.setChcDetails(List.of(chc));
        return summary;
    }
}
