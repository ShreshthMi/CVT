package com.frauscher.ConfigurationValidationService.dto.fct;

/** The surviving COM of a CAN segment (post-redundancy collapse). IDs serialised as strings. */
public record FctCom(String comId, String comName) {
}