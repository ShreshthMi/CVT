package com.frauscher.ConfigurationValidationService.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationSummary {

    @JsonProperty("validation_results")
    List<ValidationResult> results;

    @JsonProperty("dp_details")
    private List<DpDetail> dpDetails;

    @JsonProperty("track_section_details")
    private List<TrackSectionDetail> trackSectionDetails;

    @JsonProperty("chc_details")
    private List<CHCDetail> chcDetails;

    @JsonProperty("supervisor_details")
    private List<SupervisorDetail> supervisorDetail;

    @JsonProperty("ioexb_behaviour_details")
    private List<IOEXBBehaviourDetail> ioexbBehaviourDetails;

    @JsonProperty("ioexb_aco_details")
    private List<IOEXBAcoDetail> ioexbAcoDetails;

    @JsonProperty("data_transmission_details")
    private List<DataTransmissionDetail> dataTransmissionDetail;

    @JsonProperty("ethernet_details")
    private List<EthernetDetail> ethernetDetails;
}
