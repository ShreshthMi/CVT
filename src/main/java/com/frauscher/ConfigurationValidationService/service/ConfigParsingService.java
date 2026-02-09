package com.frauscher.ConfigurationValidationService.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.exception.InvalidUserValidationInputException;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.util.CfgParserUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for parsing configuration files with consistent sorting
 */
@Slf4j
@Service
public class ConfigParsingService {

    @Value("${file.parser.extension:.cfg}")
    private String fileExtension;

    /**
     * Parses uploaded files with validation and sorting by filename
     *
     * @param files the uploaded files
     * @return list of parsed config files sorted by filename
     * @throws InvalidUserValidationInputException if no valid files provided
     */
    public List<ParsedConfigFile> parseFiles(MultipartFile[] files) {
        // Validate input
        if (files == null || files.length == 0) {
            throw new InvalidUserValidationInputException("At least one config file is required");
        }

        // Filter, sort, and parse files
        List<ParsedConfigFile> parsedFiles = Arrays.stream(files)
                .filter(f -> f.getOriginalFilename() != null)
                .filter(f -> f.getOriginalFilename().toLowerCase().endsWith(fileExtension.toLowerCase()))
                .sorted((f1, f2) -> {
                    // Sort uploaded files by filename before parsing
                    String name1 = f1.getOriginalFilename() != null ? f1.getOriginalFilename() : "";
                    String name2 = f2.getOriginalFilename() != null ? f2.getOriginalFilename() : "";
                    return name1.compareTo(name2);
                })
                .map(CfgParserUtil::parse)
                .toList();

        //JsonPrinterUtil.printJson(parsedFiles);

        if (parsedFiles.isEmpty()) {
            throw new InvalidUserValidationInputException("At least one valid config file is required");
        }

        log.debug("Successfully parsed {} config files", parsedFiles.size());
        return parsedFiles;
    }
}