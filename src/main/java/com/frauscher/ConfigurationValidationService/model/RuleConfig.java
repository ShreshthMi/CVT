package com.frauscher.ConfigurationValidationService.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.frauscher.ConfigurationValidationService.validation.RuleOrigin;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = false) // FAIL on unknown fields
@NoArgsConstructor
public class RuleConfig {

    @JsonProperty("RuleType")
    private String ruleType;

    @JsonProperty("ConfigBlockName")
    private String configBlockName;

    @JsonProperty("ConfigEntryKey")
    private String configEntryKey;

    @JsonProperty("UIInputRequired")
    private String uiInputRequired;

    @JsonProperty("ValidateOnlyInFilesWith")
    private String validateOnlyInFilesWith;

    // -------- RangeCheck specific --------
    @JsonProperty("min")
    private Integer min;

    @JsonProperty("max")
    private Integer max;

    @JsonProperty("SkipComFile")
    private Boolean skipComFile;

    private RuleOrigin origin;

    public RuleOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(RuleOrigin origin) {
        this.origin = origin;
    }
}