package com.frauscher.ConfigurationValidationService.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorDetail {

    @JsonProperty("sup_name")
    private String supName;

    @JsonProperty("dp_id")
    private String dpId;

    @JsonProperty("dp_name")
    private String dpName;

    @JsonProperty("sup_by_ts")
    private List<String> supByTs;

    @JsonProperty("sup_by_ts_dp_id")
    private List<String> supByTsDpId;

    @JsonProperty("sup_by_ts_dp_name")
    private List<String> supByTsDpName;

    @JsonProperty("sup_by_ts_fma")
    private List<String> supByTsFma;

    @JsonProperty("time_out")
    private List<String> timeOut;

    @JsonProperty("logic_type")
    private List<String> logicType;

    @JsonProperty("reset_type")
    private String resetType;

    @JsonProperty("reset_delay")
    private String resetDelay;

    @JsonProperty("auto_reset_type")
    private String autoResetType;

    @JsonProperty("reset_timer")
    private String resetTimer;
}