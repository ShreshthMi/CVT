package com.frauscher.ConfigurationValidationService.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.DataTransmissionDetail;
import com.frauscher.ConfigurationValidationService.model.DpDetail;
import com.frauscher.ConfigurationValidationService.model.EthernetDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBBehaviourDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBAcoDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.SupervisorDetail;
import com.frauscher.ConfigurationValidationService.model.TrackSectionDetail;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.extractors.CHCExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.DataTransmissionExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.DpDetailExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.EthernetDetailExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.IOEXBAcoExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.IOEXBBehaviourExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.SupervisorExtractorService;
import com.frauscher.ConfigurationValidationService.service.extractors.TrackSectionExtractorService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final ConfigParsingService configParsingService;
    private final DpDetailExtractorService dpDetailExtractorService;
    private final TrackSectionExtractorService trackSectionExtractorService;
    private final CHCExtractorService chcExtractorService;
    private final SupervisorExtractorService supervisorExtractorService;
    private final IOEXBBehaviourExtractorService ioexbBehaviourExtractorService;
    private final IOEXBAcoExtractorService ioexbAcoExtractorService;
    private final DataTransmissionExtractorService dataTransmissionExtractorService;
    private final EthernetDetailExtractorService ethernetDetailExtractorService;

    private List<ValidationResult> validationResults;
    
    /**
     * Generates validation summary from uploaded files
     */
    public ValidationSummary generateSummary(MultipartFile[] files) {

        // Parse files with sorting and validation
        List<ParsedConfigFile> parsedFiles = configParsingService.parseFiles(files);
        
        return generateSummary(parsedFiles);
    }
    
    /**
     * Generates validation summary from parsed files
     */
    public ValidationSummary generateSummary(List<ParsedConfigFile> parsedFiles) {
        
        // Extract DP details
        List<DpDetail> dpDetails = extractDpDetails(parsedFiles);
        
        // Extract track section details
        List<TrackSectionDetail> trackSectionDetails = trackSectionExtractorService.extractTrackSectionDetails(parsedFiles);
        
        // Extract CHC details
        List<CHCDetail> chcDetails = chcExtractorService.extractCHCDetails(parsedFiles);
        
        // Extract Supervisor details
        List<SupervisorDetail> supervisorDetail = supervisorExtractorService.extractSupervisorDetails(parsedFiles);
        
        // Extract IOEXB behaviour details
        List<IOEXBBehaviourDetail> ioexbBehaviourDetails = ioexbBehaviourExtractorService.extractIOEXBBehaviourDetails(parsedFiles);
        
        // Extract IOEXB ACO details
        List<IOEXBAcoDetail> ioexbAcoDetails = ioexbAcoExtractorService.extractIOEXBAcoDetails(parsedFiles);
        
        // Extract Data Transmission details
        List<DataTransmissionDetail> dataTransmissionDetail = dataTransmissionExtractorService.extractDataTransmissionDetails(parsedFiles);
        
        // Extract Ethernet details
        List<EthernetDetail> ethernetDetails = ethernetDetailExtractorService.extractEthernetDetails(parsedFiles);
        
        // Create ValidationSummary using setters
        ValidationSummary summary = new ValidationSummary();
        summary.setResults(validationResults);
        summary.setDpDetails(dpDetails);
        summary.setTrackSectionDetails(trackSectionDetails);
        summary.setChcDetails(chcDetails);
        summary.setSupervisorDetail(supervisorDetail);
        summary.setIoexbBehaviourDetails(ioexbBehaviourDetails);
        summary.setIoexbAcoDetails(ioexbAcoDetails);
        summary.setDataTransmissionDetail(dataTransmissionDetail);
        summary.setEthernetDetails(ethernetDetails);
        
        return summary;
    }
    
    

    /**
     * Extracts DP details from parsed files
     */
    private List<DpDetail> extractDpDetails(List<ParsedConfigFile> parsedFiles) {
        List<ParsedConfigFile> filteredFiles = parsedFiles.stream()
                .filter(file -> !file.isComDetails())
                .toList();
        
        return dpDetailExtractorService.extractDpDetails(filteredFiles);
    }


	public List<ValidationResult> getValidationResults() {
		return validationResults;
	}
	
	public void setValidationResults(List<ValidationResult> validationResults) {
		this.validationResults = validationResults;
	}
}
