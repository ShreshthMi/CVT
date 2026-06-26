package com.frauscher.ConfigurationValidationService.dto.fct;

import java.util.List;

/** One CAN segment: its (collapsed) COM, the redundancy flag, and its physical AEB members. */
public record Chain(FctCom com, boolean redundantComPresent, List<FctAeb> aebs) {
}