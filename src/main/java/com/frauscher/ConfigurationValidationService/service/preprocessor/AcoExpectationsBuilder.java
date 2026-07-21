package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;

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
 *
 * <p>Unresolvable ids (VTF-360) skip the <b>whole host AEB</b> — POSITIONAL slots must never shift,
 * so a single bad slot invalidates the host's entire sequence — recorded in the
 * {@link BaselineInconsistencies}.</p>
 */
@Component
public class AcoExpectationsBuilder {

    private static final String BLOCK = "CFG_SECTION_OUT";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";

    public List<InstancedExpectation> build(ComAebMap fct, BaselineIndex index, BaselineInconsistencies problems) {
        if (fct == null || fct.chains() == null) {
            return List.of();
        }

        List<InstancedExpectation> out = new ArrayList<>();
        for (var chain : fct.chains()) {
            if (chain.aebs() == null) {
                continue;
            }
            for (var aeb : chain.aebs()) {
                if (aeb.acoIoExbs() == null || aeb.acoIoExbs().isEmpty()) {
                    continue;
                }
                addHost(out, aeb.dpId(), aeb.dpName(), aeb.acoIoExbs(), index, problems);
            }
        }
        return out;
    }

    /** Emits the host's full positional sequence, or nothing at all if any slot is unresolvable. */
    private void addHost(
            List<InstancedExpectation> out,
            String hostDpId,
            String hostDpName,
            List<AcoIoExb> cards,
            BaselineIndex index,
            BaselineInconsistencies problems) {

        Integer fileId = BaselineIndex.parseId(hostDpId, "ACO host DP " + hostDpName, problems);
        if (fileId == null) {
            return;
        }
        Integer fileChain = index.chainOfDpId(fileId);

        List<InstancedExpectation> host = new ArrayList<>();
        int position = 0;
        for (AcoIoExb card : cards) {
            // slot-1: track-1 (mandatory)
            Integer next = addPosition(host, fileId, position,
                    card.outputFma1DpId(), card.outputFma1Id(), fileChain, index, problems, hostDpName);
            if (next == null) {
                return; // whole-host skip: a shifted POSITIONAL sequence would corrupt the comparison.
            }
            position = next;
            // slot-2: track-2 when present, else a filler duplicate of track-1
            String slot2DpId = card.outputFma2DpId() != null ? card.outputFma2DpId() : card.outputFma1DpId();
            String slot2FmaId = card.outputFma2DpId() != null ? card.outputFma2Id() : card.outputFma1Id();
            next = addPosition(host, fileId, position, slot2DpId, slot2FmaId, fileChain, index, problems, hostDpName);
            if (next == null) {
                return;
            }
            position = next;
        }
        out.addAll(host);
    }

    private Integer addPosition(
            List<InstancedExpectation> out,
            int fileId,
            int position,
            String outputDpId,
            String outputFmaId,
            Integer fileChain,
            BaselineIndex index,
            BaselineInconsistencies problems,
            String hostDpName) {

        Integer outDpId = BaselineIndex.parseId(outputDpId, "ACO output DP on host " + hostDpName, problems);
        if (outDpId == null) {
            return null;
        }
        String section = outputFmaId == null ? "" : outputFmaId.trim();
        int slctTimeout = Objects.equals(fileChain, index.chainOfDpId(outDpId)) ? 0 : 1;

        out.add(InstancedExpectation.positional(fileId, BLOCK, position, ID, String.valueOf(outDpId)));
        out.add(InstancedExpectation.positional(fileId, BLOCK, position, SECTION, section));
        out.add(InstancedExpectation.positional(fileId, BLOCK, position, SLCT_TIMEOUT, String.valueOf(slctTimeout)));
        return position + 1;
    }
}
