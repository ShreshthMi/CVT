package com.frauscher.ConfigurationValidationService.service.extractors;

import com.frauscher.ConfigurationValidationService.model.DpDetail;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DpDetailExtractorService {


    /**
     * Extracts DP details from multiple parsed config files
     */
    public List<DpDetail> extractDpDetails(List<ParsedConfigFile> files) {
        return files.stream()
                .map(this::extractDpDetail)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Extracts a single DP detail from a parsed config file
     */
    private DpDetail extractDpDetail(ParsedConfigFile file) {
        DpDetail.DpDetailBuilder builder = DpDetail.builder();
        
        // Extract basic ID information using reusable methods
        ConfigExtractionUtil.extractAndMapValue(file, builder, "ID", "ID", "ID", DpDetail.DpDetailBuilder::dpCanId);
        ConfigExtractionUtil.extractAndMapComment(file, builder, "ID", "ID", "ID", DpDetail.DpDetailBuilder::dpName);
        
        // Extract single values using value extraction method
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_SECTION", "COMM_FAIL", "COMM_FAIL", DpDetail.DpDetailBuilder::commFail);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_SECTION", "BEHAV_GE", "BEHAV_GE", DpDetail.DpDetailBuilder::behavGe);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_SECTION", "CLR_TRACK", "CLR_TRACK", DpDetail.DpDetailBuilder::clrTrack);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_SECTION", "RESET_IN", "RESET_IN", DpDetail.DpDetailBuilder::resetIn);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_SECTION", "RESET_OUT", "RESET_OUT", DpDetail.DpDetailBuilder::resetOut);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_BEHAV_TGGL", "BEHAV_RESET", "BEHAV_RESET", DpDetail.DpDetailBuilder::behavReset);
        ConfigExtractionUtil.extractAndMapValue(file, builder, "CFG_BEHAV_TGGL", "BEHAV_SIMUL", "BEHAV_SIMUL", DpDetail.DpDetailBuilder::behavSimul);
        
        // Extract timeout values (array with special processing)
        ConfigExtractionUtil.extractTimeoutValues(file, builder, "CFG_TIMEOUT", "TIMEOUT_VALUE", "TIMEOUT_VALUE", DpDetail.DpDetailBuilder::timeOut);
        
        // Build the DP detail
        return builder.build();
    }

}
