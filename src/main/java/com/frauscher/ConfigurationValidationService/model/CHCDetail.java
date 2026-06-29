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
public class CHCDetail implements Annotatable {

    @JsonProperty("dp_id")
    private String dpId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("ts_name_1")
    private String tsName1;

    @JsonProperty("timeout_1")
    private String timeout1;

    @JsonProperty("dp_id_1")
    private String dpId1;

    @JsonProperty("dp_name_1")
    private String dpName1;

    @JsonProperty("fma_dtl_1")
    private String fmaDtl1;

    @JsonProperty("ts_name_2")
    private String tsName2;

    @JsonProperty("timeout_2")
    private String timeout2;

    @JsonProperty("dp_id_2")
    private String dpId2;

    @JsonProperty("dp_name_2")
    private String dpName2;

    @JsonProperty("fma_dtl_2")
    private String fmaDtl2;

    @JsonProperty("interval")
    private String interval;

    @JsonProperty("supervis_count")
    private String supervisCount;

    @JsonProperty("system_count")
    private String systemCount;

    @JsonProperty("partial_count")
    private String partialCount;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}
