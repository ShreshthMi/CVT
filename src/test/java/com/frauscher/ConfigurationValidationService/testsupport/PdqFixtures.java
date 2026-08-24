package com.frauscher.ConfigurationValidationService.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

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


    /**
     * A copy of {@code parsed} whose Control table Reset Types all agree.
     *
     * <p>The shipped Ver14 fixture is internally inconsistent against {@code fct-phase2-aco.fct2}: AEB
     * DP1A evaluates {@code 1AXT1} (RESTRICTED RESET WITH LV) as CFG_ZP_FMA1 and {@code SUP1-AXT1}
     * (RESTRICTED PREPARATORY RESET WITH LV) as CFG_ZP_FMA2 — and DP1B the same for
     * {@code 1AXT2}/{@code SUP1-AXT2}. The derived {@code CFG_SECTION.RESET_OUT} is one value per AEB, so
     * that pair has two candidates and the baseline is (correctly) rejected.</p>
     *
     * <p>Which side of each pair carries the right value is an open AE question, so this aligns every Reset
     * Type to the first row's rather than guessing: a happy-path test needs a <i>consistent</i> baseline,
     * not a <i>correct</i> one. The conflict itself is covered by {@code ResetOutDerivationTest}. Remove
     * this once AE corrects the workbook.</p>
     */
    public static PdqUploadResponse withConsistentResetTypes(PdqUploadResponse parsed) {
        ControlTable ct = parsed.getControlTable();
        if (ct == null || ct.trackSections() == null || ct.trackSections().isEmpty()) {
            return parsed;
        }
        String agreed = ct.trackSections().get(0).resetType();
        List<TrackSection> aligned = ct.trackSections().stream()
                .map(t -> new TrackSection(t.serialNo(), t.name(), t.dpIn(), t.dpOut(), agreed,
                        t.trackType(), t.fadcAutoReset(), t.autoResetByTimer()))
                .toList();
        parsed.setControlTable(new ControlTable(aligned, ct.dpTable()));
        return parsed;
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