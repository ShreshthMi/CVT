package com.frauscher.ConfigurationValidationService.service.pdq;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Service;

/**
 * Supplies the PDQ CQ-IR parser's numeric transforms from {@code pdq-mappings.properties}:
 * per-field step divisors and the INTERVAL ms-&gt;code enum.
 *
 * <p>Deliberately distinct from {@link com.frauscher.ConfigurationValidationService.service.extractors.ValueMappingService}
 * (display-only, raw-&gt;label). Loaded once at construction and fail-fast if the resource is
 * absent — consistent with the project's fail-fast-on-boot stance. See design §6.3.</p>
 */
@Service
public class PdqMappingService {

    private static final String RESOURCE = "pdq-mappings.properties";
    private static final String STEP_PREFIX = "step.";
    private static final String INTERVAL_PREFIX = "interval.";
    private static final String VERSION_DEFAULT_PREFIX = "versionDefault.";

    private final Properties properties = new Properties();

    public PdqMappingService() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }

    /**
     * Step divisor for a time-valued Configuration Word, or {@code null} if the field is not
     * step-divided (caller emits the value unchanged).
     */
    public Integer stepFor(String configWord) {
        String raw = properties.getProperty(STEP_PREFIX + configWord);
        return (raw == null) ? null : Integer.valueOf(raw.trim());
    }

    /**
     * ADC code for an INTERVAL value expressed in ms ({@code 10/40/80/160}), or {@code null}
     * if there is no mapping (caller rejects as {@code PDQ_INVALID}).
     */
    public String intervalCode(String intervalMs) {
        String code = properties.getProperty(INTERVAL_PREFIX + intervalMs);
        return (code == null) ? null : code.trim();
    }

    /**
     * Default value for a version-aware key (GS06-and-above group), or {@code null} if none is
     * configured. These keys are not read from the PDQ — they carry property-file defaults.
     */
    public String versionDefault(String configWord) {
        String value = properties.getProperty(VERSION_DEFAULT_PREFIX + configWord);
        return (value == null) ? null : value.trim();
    }
}