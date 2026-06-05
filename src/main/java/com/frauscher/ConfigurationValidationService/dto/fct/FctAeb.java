package com.frauscher.ConfigurationValidationService.dto.fct;

import java.util.List;

/**
 * A physical AEB member of a CAN segment, identified by its DP. Design §5.5.
 *
 * @param dpId          from {@code Aeb<Id>}
 * @param dpName        from {@code Aeb@cpName}
 * @param evaluatedFmas the AEB's own FMAs
 * @param acoIoExbs     ACO-mode IoExbs whose {@code RefAeb} is this AEB
 * @param dtIoExbCount  count of DT-mode IoExbs whose {@code RefAeb} is this AEB
 */
public record FctAeb(
        String dpId,
        String dpName,
        List<EvaluatedFma> evaluatedFmas,
        List<AcoIoExb> acoIoExbs,
        int dtIoExbCount) {
}