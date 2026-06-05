package com.frauscher.ConfigurationValidationService.testsupport;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Single source of truth for the shared PDQ test workbook fixtures. */
public final class PdqFixtures {

    /** Primary PDQ workbook (Data Transmission Inputs sheet is empty). */
    public static final String PDQ_WORKBOOK = "/fixtures/pdq-phase2-sample.xlsx";
    /** File name for the primary PDQ test workbook. */
    public static final String PDQ_WORKBOOK_FILE_NAME = "pdq-phase2-sample.xlsx";

    /** Secondary fixture: PDQ workbook with a populated Data Transmission Inputs sheet. */
    public static final String DTIO_WORKBOOK = "/fixtures/pdq-phase2-sample-dtio.xlsx";

    private PdqFixtures() {
    }

    /** Raw stream for a given classpath fixture. */
    private static InputStream openWorkbook(String resource) {
        InputStream in = PdqFixtures.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing test fixture on classpath: " + resource);
        }
        return in;
    }

    /** Raw stream for the primary PDQ workbook (e.g. MockMultipartFile in controller tests). */
    public static InputStream openWorkbook() {
        return openWorkbook(PDQ_WORKBOOK);
    }

    public static InputStream openDtioWorkbook() {
        return openWorkbook(DTIO_WORKBOOK);
    }
    
    /** Fully-populated DTIO fixture as bytes, for byte-stream parsing in BDD steps. */
    public static byte[] dtioWorkbookBytes() {
        try (InputStream in = openDtioWorkbook()) {
            return in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Could not read test fixture: " + DTIO_WORKBOOK, e);
                 }
    }
    

    /** Parsed POI workbook for the primary PDQ fixture. */
    public static Workbook loadWorkbook() {
        try (InputStream in = openWorkbook(PDQ_WORKBOOK)) {
            return WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open test fixture: " + PDQ_WORKBOOK, e);
        }
    }

    /** Parsed POI workbook for the populated-DTIO fixture. */
    public static Workbook loadDtioWorkbook() {
        try (InputStream in = openWorkbook(DTIO_WORKBOOK)) {
            return WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open test fixture: " + DTIO_WORKBOOK, e);
        }
    }

    /** Primary fixture as bytes, for MockMultipartFile uploads. */
    public static byte[] workbookBytes() {
        try (InputStream in = openWorkbook(PDQ_WORKBOOK)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read test fixture: " + PDQ_WORKBOOK, e);
        }
    }
}