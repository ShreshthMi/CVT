package com.frauscher.ConfigurationValidationService.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.frauscher.ConfigurationValidationService.model.DpDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.service.extractors.ValueMappingService;

/**
 * Utility class for extracting and mapping configuration values
 * Provides reusable static methods for configuration extraction operations
 */
@Component
public class ConfigExtractionUtil {

    private static ValueMappingService valueMappingService;

    /**
     * Injects ValueMappingService for static access
     */
    @Autowired
    public void setValueMappingService(ValueMappingService valueMappingService) {
        ConfigExtractionUtil.valueMappingService = valueMappingService;
    }

    /**
     * Generic method to extract and map values from configuration blocks
     */
    public static void extractAndMapValue(ParsedConfigFile file, DpDetail.DpDetailBuilder builder,
                                          String blockName, String entryKey, String fieldKey,
                                          java.util.function.BiConsumer<DpDetail.DpDetailBuilder, String> setter) {
        file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .findFirst()
                .ifPresentOrElse(
                        entry -> setter.accept(builder, valueMappingService.mapValue(fieldKey, entry.getValue() != null ? entry.getValue() : "")),
                        () -> setter.accept(builder, "")
                );
    }

    /**
     * Generic method to extract and map comments from configuration blocks
     */
    public static void extractAndMapComment(ParsedConfigFile file, DpDetail.DpDetailBuilder builder,
                                            String blockName, String entryKey, String fieldKey,
                                            java.util.function.BiConsumer<DpDetail.DpDetailBuilder, String> setter) {
        file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .findFirst()
                .ifPresentOrElse(
                        entry -> setter.accept(builder, valueMappingService.mapValue(fieldKey, entry.getComment() != null ? entry.getComment() : "")),
                        () -> setter.accept(builder, "")
                );
    }

    /**
     * Generic method to extract and map arrays from configuration blocks
     */
    public static void extractAndMapArray(ParsedConfigFile file, DpDetail.DpDetailBuilder builder,
                                          String blockName, String entryKey, String fieldKey,
                                          java.util.function.BiConsumer<DpDetail.DpDetailBuilder, java.util.List<String>> setter) {
        java.util.List<String> values = file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> valueMappingService.mapValue(fieldKey, entry.getValue() != null ? entry.getValue() : ""))
                .toList();
        setter.accept(builder, values);
    }

    /**
     * Extracts and maps timeout values with special processing (multiply by 10)
     */
    public static void extractTimeoutValues(ParsedConfigFile file, DpDetail.DpDetailBuilder builder,
                                            String blockName, String entryKey, String fieldKey,
                                            java.util.function.BiConsumer<DpDetail.DpDetailBuilder, java.util.List<String>> setter) {
        // Extract raw timeout values and apply special processing (multiply by 10)
        java.util.List<String> values = file.getBlocks().stream()
                .filter(block -> blockName.equals(block.getName()))
                .flatMap(block -> block.getEntries().stream())
                .filter(entry -> entryKey.equals(entry.getKey()))
                .map(entry -> {
                    String value = entry.getValue() != null ? entry.getValue() : "0";
                    try {
                        int timeout = Integer.parseInt(value);
                        return String.valueOf(timeout * 10);
                    } catch (NumberFormatException e) {
                        return value;
                    }
                })
                .toList();

        // Map each processed timeout value using the mapping service
        java.util.List<String> mappedTimeoutValues = values.stream()
                .map(value -> valueMappingService.mapValue(fieldKey, value))
                .toList();
        setter.accept(builder, mappedTimeoutValues);
    }

    /**
     * Extracts timeout value from CFG_TIMEOUT block by index and multiplies by 10
     */
    /**
     * The FMA display value for a block's {@code SECTION} entry, which every detail table shows 1-based.
     *
     * <p>Degrades instead of throwing: the extractors' {@code extractValueFromBlock} yields {@code ""} for
     * an absent entry, and {@link Integer#parseInt} on that throws inside
     * {@code SummaryService.generateSummary} -- failing the whole validate request over one missing cell.
     * An absent SECTION gives {@code ""} (which is also the extractors' initial value, so parallel arrays
     * stay aligned) and a non-numeric one is passed through unchanged.</p>
     */
    public static String fmaFromSection(String section) {
        if (section == null || section.trim().isEmpty()) {
            return "";
        }
        try {
            return String.valueOf(Integer.parseInt(section.trim()) + 1);
        } catch (NumberFormatException e) {
            return section;
        }
    }

    public static String extractTimeoutValue(ParsedConfigFile file, String timeoutIndex) {
        // Look up CFG_TIMEOUT block with matching index and get TIMEOUT_VALUE
        String timeoutValue = file.getBlocks().stream()
                .filter(timeoutBlock -> "CFG_TIMEOUT".equals(timeoutBlock.getName()))
                .filter(timeoutBlock -> timeoutIndex.equals(String.valueOf(timeoutBlock.getBlockIndex())))
                .flatMap(timeoutBlock -> timeoutBlock.getEntries().stream())
                .filter(entry -> "TIMEOUT_VALUE".equals(entry.getKey()))
                .map(entry -> entry.getValue() != null ? entry.getValue() : "0")
                .findFirst()
                .orElse("0");

        // Multiply by 10
        try {
            int timeout = Integer.parseInt(timeoutValue);
            return String.valueOf(timeout * 10);
        } catch (NumberFormatException e) {
            return timeoutValue;
        }
    }
}
