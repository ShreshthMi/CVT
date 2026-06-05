package com.frauscher.ConfigurationValidationService.service.pdq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.exception.PdqInvalidException;
import com.frauscher.ConfigurationValidationService.exception.PdqInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Normalises a single CQ-IR parametric Response cell to its stored ADC representation, per
 * {@code fcvt-phase2-design.md} §6.3 / §6.4.
 *
 * <p>Pure logic — no workbook access — so it is unit-testable in isolation. The pipeline:</p>
 * <ol>
 *   <li>{@code IDENTIFICATION} -&gt; split on {@code to} -&gt; {@code {min,max}} (integers).</li>
 *   <li>{@code TIMEOUT_VALUE} -&gt; split on {@code  &amp; }, step-divide each, pad to width 8.</li>
 *   <li>Otherwise: split on {@code  &amp; } (arrays), and per element take the value left of the
 *       first {@code :} (trimmed), then apply the per-field INTERVAL enum / step divisor.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class CqIrValueNormalizer {

    private static final String IDENTIFICATION = "IDENTIFICATION";
    private static final String TIMEOUT_VALUE = "TIMEOUT_VALUE";
    private static final String INTERVAL = "INTERVAL";
    private static final int TIMEOUT_WIDTH = 8;
    private static final Pattern ARRAY_SEPARATOR = Pattern.compile(Pattern.quote(" & "));
    private static final Pattern RANGE_SEPARATOR = Pattern.compile("(?i)\\s+to\\s+");

    private final PdqMappingService mappingService;

    /**
     * @param configWord  the Configuration Word (ADC config key) the row emits under
     * @param rawResponse the raw Response cell text
     * @return a {@code String}, a {@code List<String>}, or a {@code Map<String,Integer>}
     *         ({@code {min,max}}) — the value stored under {@code configWord} in cqIrParameters
     * @throws PdqInvalidException if the cell is empty or fails a transform
     */
    public Object normalize(String configWord, String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new PdqInvalidException(PdqInvalidReason.PARAMETRIC_ROW_EMPTY_RESPONSE,
                    "Configuration Word '" + configWord + "' has an empty Response");
        }
        String raw = rawResponse.strip();

        if (IDENTIFICATION.equals(configWord)) {
            return range(configWord, raw);
        }
        if (TIMEOUT_VALUE.equals(configWord)) {
            return timeoutArray(configWord, raw);
        }

        List<String> tokens = splitArray(raw);
        if (tokens.size() > 1) {
            List<String> out = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                out.add(scalar(configWord, token));
            }
            return out;
        }
        return scalar(configWord, tokens.get(0));
    }

    /** One scalar token: value/label colon-split, then per-key INTERVAL / step transform. */
    private String scalar(String configWord, String token) {
        String value = leftOfFirstColon(token);

        if (INTERVAL.equals(configWord)) {
            String code = mappingService.intervalCode(value);
            if (code == null) {
                throw new PdqInvalidException(PdqInvalidReason.MAPPING_LOOKUP_FAILED,
                        "No INTERVAL mapping for ms value '" + value + "'");
            }
            return code;
        }

        Integer step = mappingService.stepFor(configWord);
        return (step == null) ? value : divideByStep(configWord, value, step);
    }

    private List<String> timeoutArray(String configWord, String raw) {
        Integer step = mappingService.stepFor(TIMEOUT_VALUE);
        if (step == null) {
            throw new PdqInvalidException(PdqInvalidReason.MAPPING_LOOKUP_FAILED,
                    "No step configured for TIMEOUT_VALUE");
        }
        List<String> tokens = splitArray(raw);
        if (tokens.size() > TIMEOUT_WIDTH) {
            throw new PdqInvalidException(PdqInvalidReason.RANGE_INVALID,
                    "TIMEOUT_VALUE has more than " + TIMEOUT_WIDTH + " values: " + raw);
        }
        List<String> out = new ArrayList<>(TIMEOUT_WIDTH);
        for (String token : tokens) {
            out.add(divideByStep(configWord, leftOfFirstColon(token), step));
        }
        while (out.size() < TIMEOUT_WIDTH) {
            out.add("0");
        }
        return out;
    }

    private Map<String, String> range(String configWord, String raw) {
        String[] parts = RANGE_SEPARATOR.split(raw);
        if (parts.length != 2) {
            throw new PdqInvalidException(PdqInvalidReason.RANGE_INVALID,
                    "'" + configWord + "' is not a 'min to max' range: " + raw);
        }
        String min = parts[0].strip();
        String max = parts[1].strip();
        parseInt(configWord, min); // validate numeric; emit as String to match the cqIrParameters sample shape
        parseInt(configWord, max);
        Map<String, String> bounds = new LinkedHashMap<>();
        bounds.put("min", min);
        bounds.put("max", max);
        return bounds;
    }

    private String divideByStep(String configWord, String value, int step) {
        int n = parseInt(configWord, value);
        if (step == 0 || n % step != 0) {
            throw new PdqInvalidException(PdqInvalidReason.VALUE_NOT_DIVISIBLE_BY_STEP,
                    "'" + configWord + "' value " + value + " not divisible by step " + step);
        }
        return Integer.toString(n / step);
    }

    private int parseInt(String configWord, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PdqInvalidException(PdqInvalidReason.NON_NUMERIC_VALUE,
                    "'" + configWord + "' has a non-numeric value: " + value);
        }
    }

    private List<String> splitArray(String raw) {
        List<String> tokens = new ArrayList<>();
        for (String part : ARRAY_SEPARATOR.split(raw)) {
            tokens.add(part.strip());
        }
        return tokens;
    }

    private String leftOfFirstColon(String token) {
        int idx = token.indexOf(':');
        return ((idx >= 0) ? token.substring(0, idx) : token).strip();
    }
}