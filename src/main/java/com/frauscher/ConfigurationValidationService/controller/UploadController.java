package com.frauscher.ConfigurationValidationService.controller;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.service.ConfigParsingService;
import com.frauscher.ConfigurationValidationService.util.XmlToJsonTransformer;

import lombok.RequiredArgsConstructor;

/**
 * REST Controller for handling file upload operations.
 *
 * <p>This controller provides endpoints for uploading and processing configuration files.
 * It supports two main operations:</p>
 * <ul>
 *   <li>Upload and parse ADC configuration files</li>
 *   <li>Translate XML configuration files to JSON format</li>
 * </ul>
 *
 * <p>All endpoints validate input files and return appropriate HTTP responses.
 * Validation failures result in {@link InvalidUserValidationInputException} with descriptive messages.</p>
 *
 * @author Config Validation Team
 * @version 1.0
 * @since 1.0
 *
 * @see ConfigParsingService
 * @see InvalidUserValidationInputException
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    /** Service responsible for parsing configuration files */
    private final ConfigParsingService configParsingService;
   
    /**
     * Uploads and parses ADC configuration files.
     * 
     * <p>This endpoint accepts multiple ADC configuration files, validates them,
     * and returns parsed results. Each file is processed to extract configuration
     * blocks and entries.</p>
     * 
     * <p><strong>Request:</strong> multipart/form-data with files parameter</p>
     * <p><strong>Response:</strong> 200 OK with list of parsed files</p>
     * <p><strong>Errors:</strong> 400 Bad Request if no files provided</p>
     * 
     * @param files Array of MultipartFile objects containing ADC configuration files
     * @return ResponseEntity containing list of parsed configuration files
     * @throws InvalidUserValidationInputException if no files are provided
     * 
     * @see ParsedConfigFile
     * @see ConfigParsingService
     */
    @PostMapping(value = "/adcfiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ParsedConfigFile>> adcFiles(
            @RequestPart("files") MultipartFile[] files) {

        // -------------------------------------------------
        // Files validation
        // -------------------------------------------------
        if (files == null || files.length == 0) {
            throw new InvalidUserValidationInputException(
                    "At least one config file is required");
        }

        // Parse files and return results as JSON
        List<ParsedConfigFile> parsedFiles = configParsingService.parseFiles(files);
        return ResponseEntity.ok(parsedFiles);
    }

    
    /**
     * Translates XML configuration files to JSON format.
     * 
     * <p>This endpoint accepts a single XML file, validates its format and content,
     * and converts it to a structured JSON representation. The transformation:</p>
     * <ul>
     *   <li>Validates the file is XML format (.xml extension)</li>
     *   <li>Ensures the XML contains configuration blocks</li>
     *   <li>Removes known prefixes (AEB_, COM_) from block names</li>
     *   <li>Preserves nested structure and element order</li>
     *   <li>Converts all values to strings for consistency</li>
     * </ul>
     * 
     * <p><strong>Request:</strong> multipart/form-data with file parameter</p>
     * <p><strong>Response:</strong> 200 OK with JSON string representation</p>
     * <p><strong>Errors:</strong> 400 Bad Request for validation failures</p>
     * 
     * @param file MultipartFile containing XML configuration data
     * @return ResponseEntity containing JSON string representation of the XML
     * @throws InvalidUserValidationInputException if file validation fails
     * 
     * @see XmlToJsonTransformer
     * @see MultipartFile
     */
    @PostMapping(value = "/translate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> transformXmlFile(@RequestPart("file") MultipartFile file) {
        
        // -------------------------------------------------
        // File validation
        // -------------------------------------------------
        if (file == null || file.isEmpty()) {
            throw new InvalidUserValidationInputException(
                    "XML file is required");
        }
        
        // Validate file type
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xml")) {
            throw new InvalidUserValidationInputException(
                    "File must be an XML file with .xml extension");
        }
        
        try {
            // Read XML content as string
            String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            
            // Validate XML content format
            if (!XmlToJsonTransformer.containsConfigBlocks(xmlContent)) {
                throw new InvalidUserValidationInputException(
                        "Invalid XML format: No configuration blocks found in the XML content");
            }
            
            // Transform XML to JSON
            String json = XmlToJsonTransformer.transformXmlToJson(xmlContent);
            
            return ResponseEntity.ok(json);
            
        } catch (IOException e) {
            throw new InvalidUserValidationInputException(
                    "Failed to read XML file: " + e.getMessage());
        }
    }

}
