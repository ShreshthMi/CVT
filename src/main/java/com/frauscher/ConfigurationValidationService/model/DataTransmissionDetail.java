package com.frauscher.ConfigurationValidationService.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTransmissionDetail {

    @JsonProperty("dp_id")
    private String dpId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("safety_level_in")
    private String safetyLevelIn;

    @JsonProperty("safety_level_out")
    private String safetyLevelOut;

    @JsonProperty("safe_out_fdbck_quad")
    private String safeOutFdbckQuad;

    @JsonProperty("source_dp_id")
    private String sourceDpId;

    @JsonProperty("source_dp_name")
    private String sourceDpName;

    @JsonProperty("timeout")
    private String timeout;

    @JsonProperty("nmbr_out")
    private String nmbrOut;

    @JsonProperty("position")
    private String position;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}