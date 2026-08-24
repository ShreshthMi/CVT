package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Maps a Control table <b>Reset Type</b> to its {@code CFG_SECTION.RESET_OUT} code, from
 * {@code reset-out-mappings.properties}.
 *
 * <p>{@code RESET_OUT} has no CQ-IR row of its own — {@code fcvt-phase2-design.md} §361 and
 * {@code pdq-upload-contract-v1.4.wiki} §130 both state it is derived from the control table at validate
 * time — so this is the lookup that derivation needs. It is the enumeration carried as open item A3 in
 * three documents, supplied by AE.</p>
 *
 * <p>Deliberately separate from {@link com.frauscher.ConfigurationValidationService.service.pdq.PdqMappingService},
 * whose file is scoped to the CQ-IR parser's numeric transforms; this table is consumed at validate time.
 * Loaded once at construction and fail-fast if the resource is absent, matching that class.</p>
 */
@Service
public class ResetOutMappingService {

    private static final String RESOURCE = "reset-out-mappings.properties";
    private static final String PREFIX = "resetOut.";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Properties properties = new Properties();

    public ResetOutMappingService() {
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
     * The raw ADC {@code RESET_OUT} code for a Reset Type, or {@code null} when the catalog has no entry
     * (the caller records a baseline inconsistency rather than guessing).
     *
     * @param resetType the Control table column E text, passed through verbatim by the parser
     */
    public String codeFor(String resetType) {
        String key = normalize(resetType);
        if (key == null) {
            return null;
        }
        String code = properties.getProperty(PREFIX + key);
        return (code == null) ? null : code.trim();
    }

    /**
     * The catalog key for a Reset Type: trimmed, upper-cased, internal whitespace runs collapsed to one
     * underscore. The parser emits column E verbatim, so this absorbs the casing and spacing drift free
     * text picks up. {@code null} for a blank value.
     */
    public static String normalize(String resetType) {
        if (resetType == null || resetType.isBlank()) {
            return null;
        }
        return WHITESPACE.matcher(resetType.trim().toUpperCase()).replaceAll("_");
    }
}
