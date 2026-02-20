package com.frauscher.ConfigurationValidationService.util;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.frauscher.ConfigurationValidationService.exception.ReportGenerationException;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.util.excel.ExcelDataProcessor;
import com.frauscher.ConfigurationValidationService.util.excel.ExcelHeaderProcessor;
import com.frauscher.ConfigurationValidationService.util.excel.ExcelSheetBuilder;
import com.frauscher.ConfigurationValidationService.util.excel.ExcelStyleManager;

/**
 * Excel report generator for validation results and configuration details.
 * Creates formatted Excel workbooks with multiple sheets containing validation summaries,
 * device configurations, and system details.
 */
public class ExcelSummaryUtil {

    /**
     * Creates Excel report from validation summary data.
     * 
     * @param summary validation data to export
     * @return Excel file as byte array
     * @throws ReportGenerationException if report generation fails
     */
    public static byte[] generate(ValidationSummary summary) throws ReportGenerationException {
        try (Workbook wb = new XSSFWorkbook()) {
            
            // Set up Excel components
            ExcelStyleManager styleManager = new ExcelStyleManager(wb);
            ExcelHeaderProcessor headerProcessor = new ExcelHeaderProcessor(styleManager);
            ExcelDataProcessor dataProcessor = new ExcelDataProcessor(styleManager);
            ExcelSheetBuilder sheetBuilder = new ExcelSheetBuilder(headerProcessor, dataProcessor);
            
            // Apply workbook configuration
            configureWorkbook(wb);
            
            // Build all sheets
            createValidationResultsSheet(wb, summary.getResults(), sheetBuilder, styleManager, headerProcessor, dataProcessor);
            createDetailSheets(wb, summary, sheetBuilder, styleManager, headerProcessor, dataProcessor);
            
            // Apply final formatting to all sheets
            sheetBuilder.applyFinalFormatting(wb);
            
            // Write to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new ReportGenerationException(e);
        }
    }
    
    /**
     * Sets up workbook configuration for better performance.
     */
    private static void configureWorkbook(Workbook wb) {
        if (wb instanceof XSSFWorkbook) {
            XSSFWorkbook xssfWb = (XSSFWorkbook) wb;
            
            // Disable automatic formula recalculation
            xssfWb.setForceFormulaRecalculation(false);
            
            // Turn off Excel error checking features
            try {
                org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbook ctWorkbook = xssfWb.getCTWorkbook();
                if (ctWorkbook != null && ctWorkbook.getWorkbookPr() != null) {
                    ctWorkbook.getWorkbookPr().setShowInkAnnotation(false);
                }
            } catch (Exception e) {
                // Skip workbook config if it fails
                System.err.println("Could not configure workbook properties: " + e.getMessage());
            }
        }
    }
    
    /**
     * Builds the Validation Results sheet with summary information.
     */
    private static void createValidationResultsSheet(Workbook wb, List<?> validationResults, 
                                                  ExcelSheetBuilder sheetBuilder, 
                                                  ExcelStyleManager styleManager,
                                                  ExcelHeaderProcessor headerProcessor,
                                                  ExcelDataProcessor dataProcessor) {
        if (validationResults == null || validationResults.isEmpty()) {
            return;
        }
        
        // Extract field information from first result
        Object firstObject = validationResults.get(0);
        Field[] fields = getOrderedFields(firstObject);
        
        // Build the validation results sheet
        sheetBuilder.buildValidationResultsSheet(wb, validationResults, fields);
    }
    
    /**
     * Creates detail sheets for all configuration data.
     */
    private static void createDetailSheets(Workbook wb, ValidationSummary summary,
                                         ExcelSheetBuilder sheetBuilder,
                                         ExcelStyleManager styleManager,
                                         ExcelHeaderProcessor headerProcessor,
                                         ExcelDataProcessor dataProcessor) {
        
        // Define sheet configurations using ExcelSheetBuilder.SheetConfig in the desired order
        Map<String, ExcelSheetBuilder.SheetConfig> sheetConfigs = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        sheetConfigs.put("DP Details", new ExcelSheetBuilder.SheetConfig(false, false));
        sheetConfigs.put("Track Section Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("Supervisor Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("CHC Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("IOEXB Behaviour Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("IOEXB ACO Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("Data Transmission Details", new ExcelSheetBuilder.SheetConfig(true, false));
        sheetConfigs.put("Ethernet Details", new ExcelSheetBuilder.SheetConfig(true, false));
        
        // Create sheet data and fields maps
        Map<String, List<?>> sheetDataMap = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        Map<String, Field[]> sheetFieldsMap = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        
        // Populate data maps in the desired order
        addSheetData(sheetDataMap, sheetFieldsMap, "DP Details", summary.getDpDetails());
        addSheetData(sheetDataMap, sheetFieldsMap, "Track Section Details", summary.getTrackSectionDetails());
        addSheetData(sheetDataMap, sheetFieldsMap, "Supervisor Details", summary.getSupervisorDetail());
        addSheetData(sheetDataMap, sheetFieldsMap, "CHC Details", summary.getChcDetails());
        addSheetData(sheetDataMap, sheetFieldsMap, "IOEXB Behaviour Details", summary.getIoexbBehaviourDetails());
        addSheetData(sheetDataMap, sheetFieldsMap, "IOEXB ACO Details", summary.getIoexbAcoDetails());
        addSheetData(sheetDataMap, sheetFieldsMap, "Data Transmission Details", summary.getDataTransmissionDetail());
        addSheetData(sheetDataMap, sheetFieldsMap, "Ethernet Details", summary.getEthernetDetails());
        
        // Build all sheets using the sheet builder
        sheetBuilder.buildMultipleSheets(wb, sheetDataMap, sheetFieldsMap, sheetConfigs);
    }
    
    /**
     * Adds sheet data to the data maps.
     */
    private static void addSheetData(Map<String, List<?>> sheetDataMap, 
                                   Map<String, Field[]> sheetFieldsMap,
                                   String sheetName, List<?> dataList) {
        if (dataList != null && !dataList.isEmpty()) {
            sheetDataMap.put(sheetName, dataList);
            Object firstObject = dataList.get(0);
            sheetFieldsMap.put(sheetName, getOrderedFields(firstObject));
        }
    }
    
    /**
     * Gets fields in the order specified by @JsonPropertyOrder.
     */
    private static Field[] getOrderedFields(Object obj) {
        Class<?> clazz = obj.getClass();
        Field[] allFields = clazz.getDeclaredFields();
        
        // Check for JsonPropertyOrder annotation
        if (clazz.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonPropertyOrder.class)) {
            com.fasterxml.jackson.annotation.JsonPropertyOrder orderAnnotation = 
                clazz.getAnnotation(com.fasterxml.jackson.annotation.JsonPropertyOrder.class);
            String[] order = orderAnnotation.value();
            
            // Build ordered field list
            List<Field> orderedFieldList = new ArrayList<>();
            Map<String, Field> fieldMap = new HashMap<>();
            
            // Map all fields by name
            for (Field field : allFields) {
                fieldMap.put(field.getName(), field);
            }
            
            // Add fields in specified order
            for (String fieldName : order) {
                Field field = fieldMap.get(fieldName);
                if (field != null) {
                    orderedFieldList.add(field);
                }
            }
            
            // Add any remaining fields
            for (Field field : allFields) {
                if (!orderedFieldList.contains(field)) {
                    orderedFieldList.add(field);
                }
            }
            
            return orderedFieldList.toArray(new Field[0]);
        }
        
        return allFields;
    }
    
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use the new generate() method instead.
     */
    @Deprecated
    public static byte[] generateLegacy(ValidationSummary summary) {
        return generate(summary);
    }
}
