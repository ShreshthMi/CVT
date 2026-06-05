package com.frauscher.ConfigurationValidationService.service.pdq;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Service;

/**
 * The PDQ "workbook contract" — sheet names and the content anchors (header labels, Sl. No.
 * keys, the Project Code label) the parser locates things by. Loaded from
 * {@code pdq-workbook.properties}; edit there when AE renames a tab or rewords a header.
 *
 * <p>Deliberately about <em>where</em> things live, distinct from
 * {@link PdqMappingService} (<em>how</em> values transform). Columns/rows are still found by
 * scanning for these labels, so layout shifts (insert/move rows or columns) need no change.</p>
 */
@Service
public class PdqWorkbookContract {

    private static final String RESOURCE = "pdq-workbook.properties";

    private final Properties properties = new Properties();

    public PdqWorkbookContract() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }

    private String require(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing " + RESOURCE + " key: " + key);
        }
        return value.trim();
    }

    public String pdqSheet() { return require("sheet.pdq"); }
    public String cqirSheet() { return require("sheet.cqir"); }
    public String controlTableSheet() { return require("sheet.controlTable"); }
    public String dataTransmissionSheet() { return require("sheet.dataTransmission"); }

    public String cqirConfigWordHeader() { return require("cqir.header.configWord"); }
    public String cqirResponseHeader() { return require("cqir.header.response"); }

    public String pdqSlNoHeader() { return require("pdq.header.slNo"); }
    public String pdqResponseHeader() { return require("pdq.header.response"); }

    public String projectCodeLabel() { return require("pdq.projectCode.label"); }
    public String systemRedundancySlNo() { return require("pdq.slNo.systemRedundancy"); }
    public String aebVersionSlNo() { return require("pdq.slNo.aebEquipmentVersion"); }

    // Data Transmission Inputs sub-table section anchors (BE-02).
    public String dtDataSafetyLevelsLabel() { return require("dt.section.dataSafetyLevels"); }
    public String dtOutputDataTransmissionLabel() { return require("dt.section.outputDataTransmission"); }
}