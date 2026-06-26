package com.frauscher.ConfigurationValidationService.service.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

/**
 * Unit tests for the CQ-IR value normalizer (BE-01 / VTF-331), exercising the design §6.3/§6.4
 * transforms in isolation against the real {@code pdq-mappings.properties}.
 */
class CqIrValueNormalizerTest {

    private CqIrValueNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new CqIrValueNormalizer(new PdqMappingService());
    }

    @Test
    void stepDividesTimeValuedFields() {
        assertEquals("26", normalizer.normalize("OCC_EXT", "2600"));
        assertEquals("0", normalizer.normalize("OCC_DELAY", "0"));
        assertEquals("50", normalizer.normalize("RESET_OP_TIME", "500"));
        assertEquals("1", normalizer.normalize("RESET_LD_TIME", "100"));
        assertEquals("180", normalizer.normalize("PRERESET_ACT_TIME", "1800"));
        assertEquals("30", normalizer.normalize("RESET_DELAY", "30")); // step 1 -> unchanged
    }

    @Test
    void splitsValueLabelOnFirstColon() {
        assertEquals("3", normalizer.normalize("AUX1_OUT", "3: Clearing of track or partial traversing error"));
        assertEquals("5", normalizer.normalize("RESET_IN", "5 : Default"));
        assertEquals("5", normalizer.normalize("RESET_IN", "5 :Default"));
    }

    @Test
    void passesThroughPlainEnumValues() {
        assertEquals("0", normalizer.normalize("COMM_FAIL", "0"));
        assertEquals("1", normalizer.normalize("BEHAV_GE", "1"));
    }

    @Test
    void mapsIntervalMsToCode() {
        assertEquals("0", normalizer.normalize("INTERVAL", "10"));
        assertEquals("1", normalizer.normalize("INTERVAL", "40"));
        assertEquals("2", normalizer.normalize("INTERVAL", "80"));
        assertEquals("3", normalizer.normalize("INTERVAL", "160"));
    }

    @Test
    void buildsTimeoutArrayStepDividedAndPaddedTo8() {
        Object result = normalizer.normalize("TIMEOUT_VALUE", "340 & 610");
        assertEquals(List.of("34", "61", "0", "0", "0", "0", "0", "0"), result);
    }

    @Test
    void buildsIdentificationRangeAsStrings() {
        Object result = normalizer.normalize("IDENTIFICATION", "1 to 4095");
        assertEquals(Map.of("min", "1", "max", "4095"), result);
    }

    @Test
    void rejectsEmptyResponse() {
        PdqInvalidException ex = assertThrows(PdqInvalidException.class,
                () -> normalizer.normalize("OCC_EXT", "   "));
        assertEquals(PdqInvalidReason.PARAMETRIC_ROW_EMPTY_RESPONSE, ex.getReason());
    }

    @Test
    void rejectsValueNotDivisibleByStep() {
        PdqInvalidException ex = assertThrows(PdqInvalidException.class,
                () -> normalizer.normalize("OCC_EXT", "2650"));
        assertEquals(PdqInvalidReason.VALUE_NOT_DIVISIBLE_BY_STEP, ex.getReason());
    }

    @Test
    void rejectsUnmappedInterval() {
        PdqInvalidException ex = assertThrows(PdqInvalidException.class,
                () -> normalizer.normalize("INTERVAL", "999"));
        assertEquals(PdqInvalidReason.MAPPING_LOOKUP_FAILED, ex.getReason());
    }

    @Test
    void rejectsNonNumericStepValue() {
        PdqInvalidException ex = assertThrows(PdqInvalidException.class,
                () -> normalizer.normalize("OCC_EXT", "abc"));
        assertEquals(PdqInvalidReason.NON_NUMERIC_VALUE, ex.getReason());
    }
}