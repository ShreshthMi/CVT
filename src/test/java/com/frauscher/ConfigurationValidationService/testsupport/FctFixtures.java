package com.frauscher.ConfigurationValidationService.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Central access to the committed FCT ({@code .fct2}) test fixtures on the test classpath
 * ({@code /fixtures/...}).
 *
 * <p>Mirrors {@link PdqFixtures}: classpath lookups so tests are self-contained and CI-safe. The {@code .fct2} archive is the single
 * source of truth — {@code *Bytes()} returns the raw archive (for {@code FctParsingService} /
 * {@code MockMultipartFile}); {@code open*ProjectXml()} extracts {@code project.xml} from it (for the
 * isolated {@code FctProjectXmlParser} unit test). The caller closes returned streams.
 */
public final class FctFixtures {

    private static final String ACO = "/fixtures/fct-phase2-aco.fct2";
    private static final String REDUNDANT = "/fixtures/fct-phase2-redundant.fct2";
    private static final String DT = "/fixtures/fct-phase2-dt.fct2";
    private static final String DUPLICATE_ID = "/fixtures/fct-phase2-duplicate-id.fct2";

    private FctFixtures() {
    }

    /** Single-COM ACO sample: two CAN segments (COM100 / COM200). */
    public static byte[] acoBytes() {
        return archiveBytes(ACO);
    }

    /** MASTER/SLAVE redundant-COM FCT: collapses to one chain with redundantComPresent=true. */
    public static byte[] redundantBytes() {
        return archiveBytes(REDUNDANT);
    }

    /** FCT carrying DT-mode IoExbs. */
    public static byte[] dtBytes() {
        return archiveBytes(DT);
    }

    /** Malformed baseline: two AEBs share an Id (rejected as DUPLICATE_ENTITY_ID). */
    public static byte[] duplicateIdBytes() {
        return archiveBytes(DUPLICATE_ID);
    }

    /** project.xml extracted from the ACO archive. */
    public static InputStream openAcoProjectXml() {
        return openProjectXml(ACO);
    }

    /** project.xml extracted from the redundant-COM archive. */
    public static InputStream openRedundantProjectXml() {
        return openProjectXml(REDUNDANT);
    }

    /** project.xml extracted from the DT archive. */
    public static InputStream openDtProjectXml() {
        return openProjectXml(DT);
    }

    /** project.xml extracted from the duplicate-Id archive. */
    public static InputStream openDuplicateIdProjectXml() {
        return openProjectXml(DUPLICATE_ID);
    }

    private static byte[] archiveBytes(String resource) {
        try (InputStream in = open(resource)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read FCT fixture: " + resource, e);
        }
    }

    private static InputStream openProjectXml(String resource) {
        try (ZipInputStream zip = new ZipInputStream(open(resource))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("project.xml".equals(entry.getName())) {
                    return new ByteArrayInputStream(zip.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract project.xml from " + resource, e);
        }
        throw new IllegalStateException("project.xml not found in fixture: " + resource);
    }

    private static InputStream open(String resource) {
        InputStream in = FctFixtures.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing test fixture on classpath: " + resource);
        }
        return in;
    }
}