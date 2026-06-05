package com.frauscher.ConfigurationValidationService.service.pdq;

/**
 * Values read from the PDQ sheet header / metadata rows.
 *
 * @param projectCode         from the cell right of the "Project Code" label; blank -&gt; "0"
 * @param aebEquipmentVersion PDQ row 1.09 (e.g. "GS05 and below" / "GS06 and above")
 * @param gs06Plus            true when {@code aebEquipmentVersion} is GS06-and-above (version-aware group included)
 * @param blockExists         "true"/"false" derived from PDQ row 1.08 System Redundancy (Single/Dual)
 */
public record PdqHeader(String projectCode, String aebEquipmentVersion, boolean gs06Plus, String blockExists) {
}