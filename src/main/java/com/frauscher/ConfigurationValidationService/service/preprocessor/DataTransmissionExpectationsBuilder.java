package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.pdq.DataSafetyLevel;
import com.frauscher.ConfigurationValidationService.dto.pdq.DataTransmission;
import com.frauscher.ConfigurationValidationService.dto.pdq.OutputDataTransmission;

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
 * {@code SLCT_TIMEOUT = chain(source) == chain(receiver) ? 0 : 1}, {@code matchMode = BY_IDENTITY}.</p>
 *
 * <p>Malformed baselines (VTF-360): a length mismatch between the two sub-tables skips the <b>whole</b>
 * {@code CFG_DATA_OUT} slice (they can only be paired by row — prefix-pairing would silently pair wrong);
 * an unresolvable DP name skips just that row. Both are recorded in the {@link BaselineInconsistencies}.</p>
 */
@Component
public class DataTransmissionExpectationsBuilder {

    private static final String BLOCK = "CFG_DATA_OUT";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String ID = "ID";

    public List<InstancedExpectation> build(DataTransmission dt, BaselineIndex index, BaselineInconsistencies problems) {
        if (dt == null) {
            return List.of();
        }
        List<DataSafetyLevel> levels = dt.dataSafetyLevels();
        List<OutputDataTransmission> outputs = dt.outputDataTransmission();
        if (levels == null || outputs == null || levels.isEmpty() || outputs.isEmpty()) {
            return List.of();
        }
        if (levels.size() != outputs.size()) {
            problems.add("Data Transmission sub-tables cannot be paired: " + levels.size()
                    + " data-safety-level row(s) vs " + outputs.size() + " output row(s)");
            return List.of(); // whole-slice skip — index-pairing misaligned rows would derive wrong expectations.
        }

        List<InstancedExpectation> out = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            Integer receivingId = resolve(index, levels.get(i).dpName(), "receiving DP", problems);
            Integer sourceId = resolve(index, outputs.get(i).sourceDpName(), "source DP", problems);
            if (receivingId == null || sourceId == null) {
                continue; // skip just this row; rows are independent.
            }
            int slctTimeout = Objects.equals(index.chainOfDpId(receivingId), index.chainOfDpId(sourceId)) ? 0 : 1;
            out.add(InstancedExpectation.byIdentity(
                    receivingId, BLOCK, Map.of(ID, String.valueOf(sourceId)), SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        }
        return out;
    }

    private Integer resolve(BaselineIndex index, String dpName, String role, BaselineInconsistencies problems) {
        Integer id = index.idOfDp(dpName);
        if (id == null) {
            problems.add("Data Transmission " + role + " '" + dpName + "' has no matching DP in the FCT");
        }
        return id;
    }
}
