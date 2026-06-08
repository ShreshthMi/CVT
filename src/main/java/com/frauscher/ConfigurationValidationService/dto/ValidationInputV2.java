package com.frauscher.ConfigurationValidationService.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The {@code userInput} of a v2 validate request (design §4). Unlike the Phase 1
 * {@link UserValidationInputCriteria} (a flat block→entry map), the v2 input is upload-sourced:
 * {@code fctData} (the FCT {@link ComAebMap}) and {@code pdqData} (the parsed {@link PdqUploadResponse})
 * are the mandatory baseline sources, and the optional tpf blocks (CFG_TROLLEY_SUPP,
 * CFG_PARAM_TROLLEY_SUPP, CFG_RSR_TYPE, CFG_TYPE_PRTCT) — present only when a .tpf was uploaded —
 * are collected verbatim via {@link JsonAnySetter}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ValidationInputV2 {

    private ComAebMap fctData;
    private PdqUploadResponse pdqData;

    /** Optional tpf-sourced blocks, keyed by block name; empty when no .tpf was uploaded. */
    private final Map<String, Map<String, Object>> tpfSections = new HashMap<>();

    @JsonAnySetter
    public void addTpfSection(String blockName, Map<String, Object> entries) {
        tpfSections.put(blockName, entries);
    }
}
