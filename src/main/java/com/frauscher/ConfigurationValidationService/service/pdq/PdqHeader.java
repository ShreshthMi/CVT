package com.frauscher.ConfigurationValidationService.service.pdq;

/**
 * Values read from the PDQ sheet header / metadata rows.
 *
 * @param projectCode         from the cell right of the "Project Code" label; blank -&gt; "0"
 * @param aebEquipmentVersion PDQ row 1.09 AEB board version (e.g. "GS05", "GS07")
 * @param gs06Plus            true when {@code aebEquipmentVersion} is GS06 or above (version-aware group included)
 */
public record PdqHeader(String projectCode, String aebEquipmentVersion, boolean gs06Plus) {
}