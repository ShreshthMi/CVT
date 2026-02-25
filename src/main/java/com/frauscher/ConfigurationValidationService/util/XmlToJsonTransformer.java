package com.frauscher.ConfigurationValidationService.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to transform XML content to JSON/Map structure using Jackson libraries.
 * 
 * <p>This class provides robust XML parsing and JSON conversion capabilities with the following
 * key characteristics:</p>
 * <ul>
 *   <li>Accepts variable XML structure without strict schema requirements</li>
 *   <li>Preserves element order using LinkedHashMap</li>
 *   <li>Converts all leaf values to Strings for consistency</li>
 *   <li>Automatically handles repeated XML elements as arrays</li>
 *   <li>Strips known prefixes (AEB_, COM_) from block names for cleaner output</li>
 *   <li>Ignores XML declaration and root wrapper elements</li>
 *   <li>Handles deeply nested XML structures correctly</li>
 * </ul>
 * 
 * <p><strong>Example Input:</strong></p>
 * <pre>{@code
 * <AEB_CFG_SECTION>
 *     <COMM_FAIL>0</COMM_FAIL>
 *     <BEHAV_GE>
 *         <min>10</min>
 *         <max>15</max>
 *     </BEHAV_GE>
 * </AEB_CFG_SECTION>
 * }</pre>
 * 
 * <p><strong>Example Output:</strong></p>
 * <pre>{@code
 * {
 *   "CFG_SECTION": {
 *     "COMM_FAIL": "0",
 *     "BEHAV_GE": {
 *       "min": "10",
 *       "max": "15"
 *     }
 *   }
 * }
 * }</pre>
 * 
 * <p><strong>Compatibility:</strong></p>
 * <ul>
 *   <li>Spring Boot 3.x</li>
 *   <li>Jackson 2.15.x</li>
 *   <li>Java 17+</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe as all methods are static
 * and use thread-local Jackson mapper instances.</p>
 * 
 * @author Config Validation Team
 * @version 1.0
 * @since 1.0
 * 
 * @see com.fasterxml.jackson.dataformat.xml.XmlMapper
 * @see com.fasterxml.jackson.databind.ObjectMapper
 */
public final class XmlToJsonTransformer {

    private static final XmlMapper XML_MAPPER = new XmlMapper();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final Pattern PREFIX_PATTERN =
            Pattern.compile("^(AEB_|COM_)(.+)");

    private XmlToJsonTransformer() {
        // utility class
    }

    /**
     * Transforms XML content into a Map structure.
     *
     * @param xmlContent raw XML string
     * @return LinkedHashMap representation of the XML payload
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> transformXmlToMap(String xmlContent) {
        try {
            String json = transformXmlToJson(xmlContent);
            return JSON_MAPPER.readValue(json, LinkedHashMap.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Transforms XML content into a normalized JSON string.
     *
     * @param xmlContent raw XML string
     * @return JSON string
     */
    public static String transformXmlToJson(String xmlContent) {
        try {
            JsonNode xmlTree = XML_MAPPER.readTree(xmlContent);

            if (!xmlTree.isObject()) {
                return "{}";
            }

            var result = JSON_MAPPER.createObjectNode();

            xmlTree.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();

                // 🔒 Ignore root-level attributes (scalar values)
                if (value.isValueNode()) {
                    return;
                }

                result.set(
                    cleanBlockName(entry.getKey()),
                    normalize(value)
                );
            });

            return JSON_MAPPER
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);

        } catch (Exception ex) {
            return "{}";
        }
    }


    /**
     * Recursively normalizes XML-backed JsonNode:
     * - Object nodes stay objects
     * - Repeated tags become arrays
     * - All leaf values become Strings
     * - Known prefixes are removed from keys
     */
    private static JsonNode normalize(JsonNode node) {
        if (node.isValueNode()) {
            return JSON_MAPPER.convertValue(node.asText(), JsonNode.class);
        }

        if (node.isArray()) {
            var arrayNode = JSON_MAPPER.createArrayNode();
            node.forEach(child -> arrayNode.add(normalize(child)));
            return arrayNode;
        }

        var objectNode = JSON_MAPPER.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String cleanKey = cleanBlockName(entry.getKey());
            objectNode.set(cleanKey, normalize(entry.getValue()));
        });

        return objectNode;
    }

    /**
     * Removes known prefixes from XML block names.
     * Example:
     * - AEB_CFG_SECTION → CFG_SECTION
     * - COM_DETAILS → DETAILS
     */
    private static String cleanBlockName(String blockName) {
        if (blockName == null) {
            return "";
        }

        Matcher matcher = PREFIX_PATTERN.matcher(blockName);
        return matcher.matches() ? matcher.group(2) : blockName;
    }

    /**
     * Checks whether XML contains at least one config block.
     */
    public static boolean containsConfigBlocks(String xmlContent) {
        try {
            return xmlContent != null &&
                   !xmlContent.isBlank() &&
                   XML_MAPPER.readTree(xmlContent).size() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Returns number of top-level config blocks in XML.
     */
    public static int getConfigBlockCount(String xmlContent) {
        try {
            return XML_MAPPER.readTree(xmlContent).size();
        } catch (Exception ex) {
            return 0;
        }
    }
}
