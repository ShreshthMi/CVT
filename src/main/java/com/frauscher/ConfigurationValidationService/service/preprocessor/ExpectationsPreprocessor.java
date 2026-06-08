package com.frauscher.ConfigurationValidationService.service.preprocessor;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;

/**
 * Validate-time preprocessor seam (design §4.1): cross-correlates the FCT {@link ComAebMap} and the
 * parsed {@link PdqUploadResponse} into {@link Expectations} keyed by (file, block, instance, entry).
 * The real implementation (project-block / dual-FMA / CFG_IP_SWITCH rules) lands in VTF-335 (BE-05);
 * until then a stub returns no expectations so the v2 path stays shape-complete.
 */
public interface ExpectationsPreprocessor {

    Expectations preprocess(ComAebMap fctData, PdqUploadResponse pdqData);
}
