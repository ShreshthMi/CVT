package com.frauscher.ConfigurationValidationService.dto.pdq;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parsed PDQ workbook response — the body of {@code POST /api/upload/pdq}.
 *
 * <p>Shape matches {@code UploadPDQResponse.json} and the {@code pdqData} block of the v2
 * validate request. {@code cqIrParameters} mirrors the Phase 1 {@code userInput} structure
 * ({@link com.frauscher.ConfigurationValidationService.dto.UserValidationInputCriteria}):
 * {@code block -> (Configuration Word -> value)}, where a value is a {@code String}, a
 * {@code List<String>} (e.g. {@code TIMEOUT_VALUE}), or a {@code {min,max}} map
 * ({@code IDENTIFICATION}).</p>
 *
 * <p>BE-01 (VTF-331) populates {@code projectCode}, {@code aebEquipmentVersion} and
 * {@code cqIrParameters}; {@code controlTable} and {@code dataTransmission} are filled by
 * BE-02 (VTF-332) and remain {@code null} until then.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdqUploadResponse {

    private String projectCode;

    private String aebEquipmentVersion;

    /** Block-grouped CQ-IR parameters. BE-01 (VTF-331). */
    private Map<String, Map<String, Object>> cqIrParameters;

    /** ConfigControlTable — trackSections[] + dpTable[]. */
    private ControlTable controlTable;

    /** Data Transmission inputs; null when the DT sheet is absent or carries only headers. */
    private DataTransmission dataTransmission;
}