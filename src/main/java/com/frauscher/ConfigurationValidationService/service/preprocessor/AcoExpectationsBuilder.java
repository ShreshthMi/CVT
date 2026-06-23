package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Derives the {@code CFG_SECTION_OUT} ACO output-routing instanced expectations
 * (v2-expectations-contract.md §5.3). ACO is the {@code POSITIONAL} exception: the
 * {@code CFG_SECTION_OUT} blocks map by position to physical IO-EXB card slots, so a complete-but-
 * re-sequenced config must fail.
 *
 * <p>Per host AEB ({@code fileId = aeb.dpId}) the expected block sequence is generated from the FCT
 * {@code acoIoExbs[]} (ordered cards), 2 positions per card: slot-1 = {@code outputFma1}
 * (mandatory), slot-2 = {@code outputFma2} when present else a <b>filler duplicate of track-1</b>.
 * Never flattened across card boundaries, so positions align with the runtime's even, card-paired
 * layout by construction. At each position the validated values are {@code ID} (= aco_fmaId =
 * {@code outputFmaXDpId}), {@code SECTION} (= {@code outputFmaXId}, 0-based) and {@code SLCT_TIMEOUT}
 * (same/diff COM chain of the output DP vs the host DP). The aux fields (CLR_OCC, TYPE_AUXn,
 * AUXn_OUT, AUXn_NO_NC) are scalar (M3's bucket), not emitted here.</p>
 */
@Component
public class AcoExpectationsBuilder {

    private static final String BLOCK = "CFG_SECTION_OUT";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";

    public List<InstancedExpectation> build(ComAebMap fct) {
        if (fct == null || fct.chains() == null) {
            return List.of();
        }
        Map<Integer, Integer> chainByDpId = chainByDpId(fct);

        List<InstancedExpectation> out = new ArrayList<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.acoIoExbs() == null || aeb.acoIoExbs().isEmpty()) {
                    continue;
                }
                int fileId = parseId(aeb.dpId(), "ACO host DP " + aeb.dpName());
                Integer fileChain = chainByDpId.get(fileId);
                int position = 0;
                for (AcoIoExb card : aeb.acoIoExbs()) {
                    // slot-1: track-1 (mandatory)
                    position = addPosition(out, fileId, position,
                            card.outputFma1DpId(), card.outputFma1Id(), fileChain, chainByDpId, aeb.dpName());
                    // slot-2: track-2 when present, else a filler duplicate of track-1
                    if (card.outputFma2DpId() != null) {
                        position = addPosition(out, fileId, position,
                                card.outputFma2DpId(), card.outputFma2Id(), fileChain, chainByDpId, aeb.dpName());
                    } else {
                        position = addPosition(out, fileId, position,
                                card.outputFma1DpId(), card.outputFma1Id(), fileChain, chainByDpId, aeb.dpName());
                    }
                }
            }
        }
        return out;
    }

    private int addPosition(
            List<InstancedExpectation> out,
            int fileId,
            int position,
            String outputDpId,
            String outputFmaId,
            Integer fileChain,
            Map<Integer, Integer> chainByDpId,
            String hostDpName) {

        int outDpId = parseId(outputDpId, "ACO output DP on host " + hostDpName);
        String section = outputFmaId == null ? "" : outputFmaId.trim();
        int slctTimeout = Objects.equals(fileChain, chainByDpId.get(outDpId)) ? 0 : 1;

        out.add(InstancedExpectation.positional(fileId, BLOCK, position, ID, String.valueOf(outDpId)));
        out.add(InstancedExpectation.positional(fileId, BLOCK, position, SECTION, section));
        out.add(InstancedExpectation.positional(fileId, BLOCK, position, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        return position + 1;
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
