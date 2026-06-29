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
public class IOEXBAcoDetail implements Annotatable {

    @JsonProperty("dp_id")
    private String dpId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("aco_fma1")
    private String acoFma1;

    @JsonProperty("clr_occ")
    private String clrOcc;

    @JsonProperty("type_aux1")
    private String typeAux1;

    @JsonProperty("type_aux2")
    private String typeAux2;

    @JsonProperty("aux1_out")
    private String aux1Out;

    @JsonProperty("aux1_no_nc")
    private String aux1NoNc;

    @JsonProperty("aux2_out")
    private String aux2Out;

    @JsonProperty("aux2_no_nc")
    private String aux2NoNc;

    @JsonProperty("fma_1_2")
    private String fma12;

    @JsonProperty("time_out")
    private String timeOut;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}
