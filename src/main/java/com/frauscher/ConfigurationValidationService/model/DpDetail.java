package com.frauscher.ConfigurationValidationService.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DpDetail {

    @JsonProperty("dp_can_id")
    private String dpCanId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("time_out")
    private List<String> timeOut;

    @JsonProperty("comm_fail")
    private String commFail;

    @JsonProperty("behav_ge")
    private String behavGe;

    @JsonProperty("clr_track")
    private String clrTrack;

    @JsonProperty("reset_in")
    private String resetIn;

    @JsonProperty("reset_out")
    private String resetOut;

    @JsonProperty("behav_reset")
    private String behavReset;

    @JsonProperty("behav_simul")
    private String behavSimul;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}
