package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataSafetyLevel;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataTransmission;
import com.frauscher.ConfigurationValidationService.dto.pdq.OutputDataTransmission;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Derives the {@code CFG_DATA_OUT} instanced expectations for Cluster 1 (VTF-338) — the DT slice Cluster 1
 * owns: each output's referenced source DP ({@code ID}) and its {@code SLCT_TIMEOUT} (physical/virtual by
 * CAN-segment membership, design §8.1). The DT payload proper (safety levels, {@code NMBR_OUT},
 * {@code POSITION}) stays with Cluster 6 / BE-14.
 *
 * <p>The PDQ Data Transmission sheet is a single table split into two index-aligned sub-tables (design
 * §6.6): {@code dataSafetyLevels[i]} carries the <b>receiving</b> DP ({@code dpName}) and
 * {@code outputDataTransmission[i]} the <b>source</b> DP ({@code sourceDpName}) of the same row. So per
 * paired row: {@code fileId = idOf(receivingDp)}, {@code linkedId = {ID: idOf(sourceDp)}},
 * {@code SLCT_TIMEOUT = chain(source) == chain(receiver) ? 0 : 1}, {@code matchMode = BY_IDENTITY}. A
 * length mismatch between the two sub-tables (they can only be paired by row) or a DP name that resolves to
 * no FCT AEB is a malformed baseline (§3.3) → {@code PHASE2_BASELINE_INCONSISTENT}.</p>
 */
@Component
public class DataTransmissionExpectationsBuilder {

    private static final String BLOCK = "CFG_DATA_OUT";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String ID = "ID";

    public List<InstancedExpectation> build(ComAebMap fct, DataTransmission dt) {
        if (fct == null || fct.chains() == null || dt == null) {
            return List.of();
        }
        List<DataSafetyLevel> levels = dt.dataSafetyLevels();
        List<OutputDataTransmission> outputs = dt.outputDataTransmission();
        if (levels == null || outputs == null || levels.isEmpty() || outputs.isEmpty()) {
            return List.of();
        }
        if (levels.size() != outputs.size()) {
            throw new BaselineInconsistentException(
                    "Data Transmission sub-tables cannot be paired: " + levels.size()
                            + " data-safety-level row(s) vs " + outputs.size() + " output row(s)");
        }

        Map<String, Integer> idByDpName = idByDpName(fct);
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);

        List<InstancedExpectation> out = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            int receivingId = resolve(idByDpName, levels.get(i).dpName(), "receiving DP");
            int sourceId = resolve(idByDpName, outputs.get(i).sourceDpName(), "source DP");
            int slctTimeout = Objects.equals(chainByDpId.get(receivingId), chainByDpId.get(sourceId)) ? 0 : 1;
            out.add(InstancedExpectation.byIdentity(
                    receivingId, BLOCK, Map.of(ID, String.valueOf(sourceId)), SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
        return out;
    }

    private int resolve(Map<String, Integer> idByDpName, String dpName, String role) {
        Integer id = dpName == null ? null : idByDpName.get(dpName);
        if (id == null) {
            throw new BaselineInconsistentException(
                    "Data Transmission " + role + " '" + dpName + "' has no matching DP in the FCT");
        }
        return id;
    }

    private Map<String, Integer> idByDpName(ComAebMap fct) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.dpName() != null) {
                    map.put(aeb.dpName(), parseId(aeb.dpId(), "DP " + aeb.dpName()));
                }
            }
        }
        return map;
    }

    private Map<Integer, Integer> chainByDpId(ComAebMap fct) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        List<com.frauscher.ConfigurationValidationService.dto.fct.Chain> chains = fct.chains();
        for (int i = 0; i < chains.size(); i++) {
            var chain = chains.get(i);
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                map.put(parseId(aeb.dpId(), "DP " + aeb.dpName()), i);
            }
        }
        return map;
    }

    private int parseId(String value, String what) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BaselineInconsistentException("Non-numeric id for " + what + ": " + value);
        }
    }
}
