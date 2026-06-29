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
public class IOEXBBehaviourDetail implements Annotatable {

    @JsonProperty("dp_id")
    private String dpId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("behav_input1")
    private String behavInput1;

    @JsonProperty("type_in1")
    private String typeIn1;

    @JsonProperty("behav_input2")
    private String behavInput2;

    @JsonProperty("type_in2")
    private String typeIn2;

    @JsonProperty("behav_input3")
    private String behavInput3;

    @JsonProperty("type_in3")
    private String typeIn3;

    @JsonProperty("behav_ioexb")
    private String behavIoexb;

    @JsonProperty("type_ioexb")
    private String typeIoexb;

    @JsonProperty("is_coop_reset")
    private Boolean isCoopReset;

    @JsonProperty("coop_reset_type")
    private String coopResetType;

    @JsonProperty("coop_control_type")
    private String coopControlType;

    @JsonProperty("reset_timeout")
    private String resetTimeout;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}