package com.frauscher.ConfigurationValidationService.testsupport;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Single source of truth for the shared PDQ test workbook fixture. */
public final class PdqFixtures {

    /** Classpath location — set this to the file name you uploaded under src/test/resources/fixtures. */
    public static final String PDQ_WORKBOOK = "/fixtures/pdq-phase2-sample.xlsx";
    /** File name for the PDQ test workbook. */
    public static final String PDQ_WORKBOOK_FILE_NAME = "pdq-phase2-sample.xlsx";

        private PdqFixtures() {
    }

    /** Raw stream, for callers that need bytes (e.g. MockMultipartFile in controller tests). */
    public static InputStream openWorkbook() {
        InputStream in = PdqFixtures.class.getResourceAsStream(PDQ_WORKBOOK);
        if (in == null) {
            throw new IllegalStateException("Missing test fixture on classpath: " + PDQ_WORKBOOK);
        }
        return in;
    }

    /** Parsed POI workbook, for the parser/service tests. */
    public static Workbook loadWorkbook() {
        try (InputStream in = openWorkbook()) {
            return WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open test fixture: " + PDQ_WORKBOOK, e);
        }
    }

    /** Fixture as bytes, for MockMultipartFile uploads. */
    public static byte[] workbookBytes() {
        try (InputStream in = openWorkbook()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read test fixture: " + PDQ_WORKBOOK, e);
        }
    }
}