package com.frauscher.ConfigurationValidationService.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

/**
 * Model representing track section details extracted from configuration files
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
@JsonPropertyOrder({
        "ts_name",
        "e_dp_id",
        "e_dp_name",
        "fma_1_2",
        "ch_dp_id",
        "ch_dp_name",
        "ch_slct_timeout",
        "i_ch_dp_id",
        "i_ch_dp_name",
        "i_ch_slct_timeout"
})
public class TrackSectionDetail {

    /**
     * Track section name (e.g., "T1", "T2")
     */
    @JsonProperty("ts_name")
    private String tsName;

    /**
     * FMA value (1 for CFG_ZP_FMA1, 2 for CFG_ZP_FMA2)
     */
    @JsonProperty("fma_1_2")
    private String fma;

    /**
     * Evaluating DP ID
     */
    @JsonProperty("e_dp_id")
    private String dpId;

    /**
     */
    @JsonProperty("e_dp_name")
    private String dpName;

    /**
     * Counting head DP IDs (DIR_INV = 0)
     */
    @JsonProperty("ch_dp_id")
    private List<String> chDpId;

    /**
     * Counting head DP names (DIR_INV = 0)
     */
    @JsonProperty("ch_dp_name")
    private List<String> chDpName;

    /**
     * Counting head select timeouts (DIR_INV = 0)
     */
    @JsonProperty("ch_slct_timeout")
    private List<String> chSlctTimeout;

    /**
     * Inverse counting head DP IDs (DIR_INV = 1)
     */
    @JsonProperty("i_ch_dp_id")
    private List<String> iChDpId;

    /**
     * Inverse counting head DP names (DIR_INV = 1)
     */
    @JsonProperty("i_ch_dp_name")
    private List<String> iChDpName;

    /**
     * Inverse counting head select timeouts (DIR_INV = 1)
     */
    @JsonProperty("i_ch_slct_timeout")
    private List<String> iChSlctTimeout;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}