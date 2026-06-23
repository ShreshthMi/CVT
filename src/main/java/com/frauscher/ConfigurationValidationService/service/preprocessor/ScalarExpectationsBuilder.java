package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;

/**
 * Builds the {@code scalarExpectations} bucket (v2-expectations-contract.md §4) — the
 * {@code block → entry → value} map the reused Phase 1 engine validates every-occurrence. The PDQ
 * {@code cqIrParameters} is already in that shape (and already carries {@code CFG_PROJECT_AEB} /
 * {@code CFG_PROJECT_COM}, appended by {@code PdqParsingService}, plus the cqIR scalar blocks incl.
 * {@code CFG_RSR_TYPE}, {@code CFG_SWITCH}, {@code CFG_IP_SWITCH_TIME} and the {@code CFG_SUPERVIS_FMA*}
 * RESET_TYPE/RESET_DELAY scalars), so this is mostly a copy with two adjustments:
 * <ol>
 *   <li><b>{@code IDENTIFICATION} → {@code ID}:</b> the cqIR block holds the {@code {min,max}} map at
 *       block level; the engine's RangeCheck rule is keyed {@code ID → ID}, so the map becomes the
 *       value of {@code ID.ID}.</li>
 *   <li><b>tpf blocks:</b> the optional {@code .tpf} sections (e.g. {@code CFG_TROLLEY_SUPP},
 *       {@code CFG_PARAM_TROLLEY_SUPP}, {@code CFG_TYPE_PRTCT}, and {@code CFG_RSR_TYPE} when the PDQ
 *       did not carry it) are merged in. The PDQ value wins on any overlap — the gate (§3.2) has
 *       already proven RSR_TYPE consistent where both are present.</li>
 * </ol>
 * Instanced-only blocks (counting heads, ACO identity, CHC, forwarding) are NOT here — they live in
 * the instanced bucket (§5, M5).
 */
@Component
public class ScalarExpectationsBuilder {

    private static final String IDENTIFICATION = "IDENTIFICATION";
    private static final String ID = "ID";

    public Map<String, Map<String, Object>> build(ValidationInputV2 userInput) {
        Map<String, Map<String, Object>> scalar = new LinkedHashMap<>();

        Map<String, Map<String, Object>> cqIr = userInput.getPdqData().getCqIrParameters();
        if (cqIr != null) {
            cqIr.forEach((block, entries) -> {
                if (entries == null) {
                    return;
                }
                if (IDENTIFICATION.equals(block)) {
                    // The {min,max} map sits at block level in cqIR; the engine wants it under ID.ID.
                    Map<String, Object> idBlock = new LinkedHashMap<>();
                    idBlock.put(ID, entries);
                    scalar.put(ID, idBlock);
                } else {
                    scalar.put(block, new LinkedHashMap<>(entries));
                }
            });
        }

        Map<String, Map<String, Object>> tpf = userInput.getTpfSections();
        if (tpf != null) {
            tpf.forEach((block, entries) -> {
                if (entries != null) {
                    scalar.putIfAbsent(block, new LinkedHashMap<>(entries));
                }
            });
        }

        return scalar;
    }
}
