package com.frauscher.ConfigurationValidationService.service.preprocessor;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;

/**
 * Placeholder {@link ExpectationsPreprocessor} for BE-04: returns no expectations. VTF-335 (BE-05)
 * replaces this with the real cross-correlation (project-block consistency, dual-FMA consistency,
 * CFG_IP_SWITCH derivation from {@code redundantComPresent}).
 */
@Service
public class StubExpectationsPreprocessor implements ExpectationsPreprocessor {

    @Override
    public Expectations preprocess(ComAebMap fctData, PdqUploadResponse pdqData) {
        return Expectations.empty();
    }
}
