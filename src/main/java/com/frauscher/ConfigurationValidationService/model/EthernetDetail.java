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
public class EthernetDetail implements Annotatable {

    @JsonProperty("com")
    private String com;

    @JsonProperty("id")
    private String id;

    @JsonProperty("ip_nw_1")
    private String ipNw1;

    @JsonProperty("subnet_mask_1")
    private String subnetMask1;

    @JsonProperty("ip_nw_2")
    private String ipNw2;

    @JsonProperty("subnet_mask_2")
    private String subnetMask2;

    @JsonProperty("dest_ip_nw_1")
    private List<String> destIpNw1;

    @JsonProperty("dest_ip_nw_2")
    private List<String> destIpNw2;

    @JsonProperty("fwrd_acd_to_dp_ids")
    private List<String> fwrdAcdToDpIds;

    @JsonProperty("fwrd_acd_to_dp_dtls")
    private List<String> fwrdAcdToDpDtls;

    @JsonProperty("interval")
    private String interval;

    /** v2 per-cell mismatch annotations (omitted when none); see {@link MismatchAnnotation}. */
    @JsonProperty("_mismatches")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<MismatchAnnotation> mismatches;
}