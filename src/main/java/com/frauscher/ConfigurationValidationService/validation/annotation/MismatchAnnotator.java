package com.frauscher.ConfigurationValidationService.validation.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.model.Annotatable;
import com.frauscher.ConfigurationValidationService.model.CHCDetail;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.DataTransmissionDetail;
import com.frauscher.ConfigurationValidationService.model.DpDetail;
import com.frauscher.ConfigurationValidationService.model.EthernetDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBAcoDetail;
import com.frauscher.ConfigurationValidationService.model.IOEXBBehaviourDetail;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.SupervisorDetail;
import com.frauscher.ConfigurationValidationService.model.TrackSectionDetail;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.model.ValidationSummary;
import com.frauscher.ConfigurationValidationService.service.extractors.ValueMappingService;
import com.frauscher.ConfigurationValidationService.service.preprocessor.BaselineInconsistencies;
import com.frauscher.ConfigurationValidationService.util.ConfigExtractionUtil;
import com.frauscher.ConfigurationValidationService.validation.instanced.ForwardingDestinationResolver;
import com.frauscher.ConfigurationValidationService.validation.instanced.ForwardingDestinationResolver.ForwardMember;
import com.frauscher.ConfigurationValidationService.validation.instanced.InstancedFinding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BE-07 post-pass: maps each failing {@link ValidationResult} onto the detail-table cell it concerns and
 * attaches a {@link MismatchAnnotation} ({@code _mismatches}) carrying the kind, Expected/Actual (display
 * form), and the {@code result_id} the FE navigates by (FCVT-v2-Validation-Response-Contract.md §4–§5).
 * It runs after {@code SummaryService.generateSummary}, mutating the v2 summary in place; a clean run adds
 * nothing, so the response stays Phase-1-shaped.
 *
 * <p>The instanced join is coordinate-driven: each {@link InstancedFinding} carries the raw block /
 * identity / position the evaluator checked (no result-string re-parsing). The annotator owns the
 * detail-table layout — which column, which array index, the union-array padding, and the per-field
 * raw→display translation that mirrors each extractor (DP id→name, {@link ValueMappingService} enums,
 * {@code SLCT_TIMEOUT}→{@code CFG_TIMEOUT}×10, {@code SECTION+1}). For set columns it matches the result's
 * raw identity against the row's parallel raw-id array (e.g. {@code ch_dp_id}), so no display round-trip is
 * needed to locate an element.</p>
 *
 * <p>Coverage notes (results-only, no faithful cell — they surface in {@code validation_results} only):
 * a counting-head {@code DIR_INV} value (the ch/i_ch split axis, not a column); an ACO {@code ID}
 * (the {@code aco_fmaId} is not a displayed column); an ACO/CHC {@code MISSING} where the table has no
 * array slot to append to; scalar cross-rules with no detail column (project / RSR / switch). DT has no
 * validation results yet (BE-14), so nothing to annotate there.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MismatchAnnotator {

    private static final String FWRD_BLOCK = "CFG_FWRD_ACD";
    private static final String SECTION_OUT = "CFG_SECTION_OUT";
    private static final String ID = "ID";
    private static final String SECTION = "SECTION";
    private static final String DIR_INV = "DIR_INV";
    private static final String SLCT_TIMEOUT = "SLCT_TIMEOUT";
    private static final String LOGIC_TYPE = "LOGIC_TYPE";
    private static final String CAN_TX_ID = "CAN_TX_ID";
    private static final String DEST_COM = "DEST_COM";

    /** (block,entry) → ioexb_aco_details column for the scalar CFG_SECTION_OUT aux checks. */
    private static final Map<String, String> ACO_AUX_FIELD = Map.of(
            "CLR_OCC", "clr_occ",
            "TYPE_AUX1", "type_aux1",
            "TYPE_AUX2", "type_aux2",
            "AUX1_OUT", "aux1_out",
            "AUX1_NO_NC", "aux1_no_nc",
            "AUX2_OUT", "aux2_out",
            "AUX2_NO_NC", "aux2_no_nc");

    private static final String CFG_ZP = "CFG_ZP";
    private static final List<String> SUPERVIS_ORDER = List.of("CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2");
    private static final Set<String> SUPERVIS_BLOCKS = Set.copyOf(SUPERVIS_ORDER);

    /** (entry) to supervisor_details column, for the scalar CFG_SUPERVIS_FMA* checks. */
    private static final Map<String, String> SUPERVISOR_SCALAR_FIELD = Map.of(
            "RESET_TYPE", "reset_type",
            "RESET_DELAY", "reset_delay");

    /** (entry) to chc_details column, for the scalar CFG_ZP checks. */
    private static final Map<String, String> CHC_ZP_FIELD = Map.of(
            "INTERVAL", "interval",
            "SUPERVIS_COUNT", "supervis_count",
            "SYSTEM_COUNT", "system_count",
            "PARTIAL_COUNT", "partial_count");

    /**
     * Entries whose detail column carries the {@link ValueMappingService} label. The rest are stored raw by
     * their extractor -- CHC's three counts ({@code CHCExtractorService} maps only {@code INTERVAL}) and the
     * supervisor {@code RESET_DELAY} -- and a mapped annotation would disagree with the cell it points at.
     */
    private static final Set<String> DISPLAY_MAPPED_ENTRIES = Set.of(
            "CLR_OCC", "TYPE_AUX1", "TYPE_AUX2", "AUX1_OUT", "AUX1_NO_NC", "AUX2_OUT", "AUX2_NO_NC",
            "RESET_TYPE", "INTERVAL");

    private final ValueMappingService valueMappingService;
    private final ForwardingDestinationResolver forwardingResolver;

    /**
     * Annotates the detail tables of {@code summary} in place.
     *
     * @param summary      the freshly built v2 summary (results already carry their {@code id})
     * @param scalarResults the Phase-1-engine results of the scalar bucket (for the few that map to a cell)
     * @param findings     the instanced findings (raw cell coordinates)
     * @param parsedFiles  the uploaded ADCs (for id→name resolution and forwarding re-resolution)
     */
    public void annotate(ValidationSummary summary, List<ValidationResult> scalarResults,
            List<InstancedFinding> findings, List<ParsedConfigFile> parsedFiles) {

        Map<Integer, ParsedConfigFile> filesById = new LinkedHashMap<>();
        Map<String, Integer> fileIdByName = new LinkedHashMap<>();
        if (parsedFiles != null) {
            for (ParsedConfigFile f : parsedFiles) {
                filesById.putIfAbsent(f.getId(), f);
                fileIdByName.putIfAbsent(f.getFileName(), f.getId());
            }
        }

        // Instanced findings (forwarding handled as a grouped pass below).
        Map<Integer, List<InstancedFinding>> forwardingByCom = new LinkedHashMap<>();
        if (findings != null) {
            for (InstancedFinding f : findings) {
                if (FWRD_BLOCK.equals(f.block())) {
                    forwardingByCom.computeIfAbsent(f.fileId(), k -> new ArrayList<>()).add(f);
                    continue;
                }
                if (!f.isAnnotatable()) {
                    continue;
                }
                dispatchInstanced(summary, filesById, f);
            }
        }
        forwardingByCom.forEach((comId, group) -> annotateForwarding(summary, filesById, parsedFiles, comId, group));

        // Scalar bucket: every block whose keys own a detail cell is routed; the rest are results-only.
        if (scalarResults != null) {
            for (ValidationResult r : scalarResults) {
                if ("FAIL".equals(r.getStatus())) {
                    dispatchScalar(summary, filesById, fileIdByName, r);
                }
            }
        }
    }

    private void dispatchInstanced(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f) {
        switch (f.block()) {
            case "CFG_ZP_FMA1" -> annotateTrackSection(summary, filesById, f, "1");
            case "CFG_ZP_FMA2" -> annotateTrackSection(summary, filesById, f, "2");
            case "CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2" -> annotateSupervisor(summary, filesById, f);
            case "CFG_CONTROL" -> annotateChc(summary, filesById, f);
            case SECTION_OUT -> annotateAco(summary, filesById, f);
            case "CFG_AXCNT" -> annotateIoexbBehaviour(summary, f);
            case "CFG_DATA_OUT" -> annotateDataTransmission(summary, filesById, f);
            case "CFG_SECTION" -> annotateDpDetail(summary, f);
            default -> { /* CFG_IP_SWITCH and any other instanced block has no detail cell */ }
        }
    }

    // ---- Track section (counting heads): CFG_ZP_FMA1/2 → track_section_details ----

    private void annotateTrackSection(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f, String fma) {

        TrackSectionDetail row = first(summary.getTrackSectionDetails(),
                r -> eq(f.fileId(), r.getDpId()) && fma.equals(r.getFma()));
        if (row == null) {
            return;
        }
        ParsedConfigFile file = filesById.get(f.fileId());
        String rid = f.result().getId();

        switch (f.kind()) {
            case VALUE -> {
                if (!SLCT_TIMEOUT.equals(f.entryKey())) {
                    return; // DIR_INV is the ch/i_ch split axis, not a displayed column → results-only
                }
                String headId = f.linkedId().get(ID);
                int i = idxOf(row.getChDpId(), headId);
                if (i >= 0) {
                    add(row, MismatchAnnotation.value("ch_slct_timeout", i,
                            timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
                    return;
                }
                int j = idxOf(row.getIChDpId(), headId);
                if (j >= 0) {
                    add(row, MismatchAnnotation.value("i_ch_slct_timeout", j,
                            timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
                }
            }
            case UNEXPECTED -> {
                String headId = f.linkedId().get(ID);
                int i = idxOf(row.getChDpId(), headId);
                if (i >= 0) {
                    add(row, MismatchAnnotation.unexpected("ch_dp_name", i, at(row.getChDpName(), i), rid));
                    return;
                }
                int j = idxOf(row.getIChDpId(), headId);
                if (j >= 0) {
                    add(row, MismatchAnnotation.unexpected("i_ch_dp_name", j, at(row.getIChDpName(), j), rid));
                }
            }
            case MISSING -> {
                boolean inverse = "1".equals(f.memberExpected().get(DIR_INV));
                String name = dpName(filesById, f.linkedId().get(ID));
                if (inverse) {
                    int idx = append(row.getIChDpId(), row::setIChDpId, "");
                    append(row.getIChSlctTimeout(), row::setIChSlctTimeout, "");
                    append(row.getIChDpName(), row::setIChDpName, "");
                    add(row, MismatchAnnotation.missing("i_ch_dp_name", idx, name, rid));
                } else {
                    int idx = append(row.getChDpId(), row::setChDpId, "");
                    append(row.getChSlctTimeout(), row::setChSlctTimeout, "");
                    append(row.getChDpName(), row::setChDpName, "");
                    add(row, MismatchAnnotation.missing("ch_dp_name", idx, name, rid));
                }
            }
        }
    }

    // ---- Supervisor: CFG_SUPERVIS_FMA1/2 → supervisor_details ----

    private void annotateSupervisor(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f) {

        String rid = f.result().getId();
        String id = f.linkedId().get(ID);
        String fmaPlus1 = plus1(f.linkedId().get(SECTION));

        if (f.kind() == MismatchAnnotation.Kind.MISSING) {
            SupervisorDetail row = first(summary.getSupervisorDetail(), r -> eq(f.fileId(), r.getDpId()));
            if (row == null) {
                return;
            }
            int idx = append(row.getSupByTsDpId(), row::setSupByTsDpId, "");
            append(row.getSupByTs(), row::setSupByTs, "");
            append(row.getSupByTsDpName(), row::setSupByTsDpName, "");
            append(row.getSupByTsFma(), row::setSupByTsFma, "");
            append(row.getTimeOut(), row::setTimeOut, "");
            append(row.getLogicType(), row::setLogicType, "");
            add(row, MismatchAnnotation.missing("sup_by_ts_dp_name", idx, dpName(filesById, id), rid));
            return;
        }

        // VALUE / UNEXPECTED: locate the row + member index by (ID, SECTION) membership (disambiguates FMA1/FMA2).
        for (SupervisorDetail row : nonNull(summary.getSupervisorDetail())) {
            if (!eq(f.fileId(), row.getDpId())) {
                continue;
            }
            int i = supMemberIndex(row, id, fmaPlus1);
            if (i < 0) {
                continue;
            }
            ParsedConfigFile file = filesById.get(f.fileId());
            if (f.kind() == MismatchAnnotation.Kind.UNEXPECTED) {
                add(row, MismatchAnnotation.unexpected("sup_by_ts_dp_name", i, at(row.getSupByTsDpName(), i), rid));
            } else if (LOGIC_TYPE.equals(f.entryKey())) {
                add(row, MismatchAnnotation.value("logic_type", i,
                        mapped(LOGIC_TYPE, f.rawExpected()), mapped(LOGIC_TYPE, f.rawActual()), rid));
            } else if (SLCT_TIMEOUT.equals(f.entryKey())) {
                add(row, MismatchAnnotation.value("time_out", i,
                        timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
            }
            return;
        }
    }

    // ---- CHC (external counting heads): CFG_CONTROL → chc_details (fixed _1/_2 slots) ----

    private void annotateChc(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f) {

        CHCDetail row = first(summary.getChcDetails(), r -> eq(f.fileId(), r.getDpId()));
        if (row == null) {
            return;
        }
        ParsedConfigFile file = filesById.get(f.fileId());
        String rid = f.result().getId();
        String id = f.linkedId().get(ID);
        String fmaPlus1 = plus1(f.linkedId().get(SECTION));

        boolean slot1 = eqTrim(row.getDpId1(), id) && eqTrim(row.getFmaDtl1(), fmaPlus1);
        boolean slot2 = eqTrim(row.getDpId2(), id) && eqTrim(row.getFmaDtl2(), fmaPlus1);

        switch (f.kind()) {
            case VALUE -> {
                if (!SLCT_TIMEOUT.equals(f.entryKey())) {
                    return; // only the timeout column is displayed for a control entry value
                }
                if (slot1) {
                    add(row, MismatchAnnotation.value("timeout_1", null,
                            timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
                } else if (slot2) {
                    add(row, MismatchAnnotation.value("timeout_2", null,
                            timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
                }
            }
            case UNEXPECTED -> {
                if (slot1) {
                    add(row, MismatchAnnotation.unexpected("dp_name_1", null, row.getDpName1(), rid));
                } else if (slot2) {
                    add(row, MismatchAnnotation.unexpected("dp_name_2", null, row.getDpName2(), rid));
                }
            }
            case MISSING -> {
                String name = dpName(filesById, id);
                if (isBlank(row.getDpId1())) {
                    add(row, MismatchAnnotation.missing("dp_name_1", null, name, rid));
                } else if (isBlank(row.getDpId2())) {
                    add(row, MismatchAnnotation.missing("dp_name_2", null, name, rid));
                }
                // both slots filled but identity absent → no slot to render; results-only.
            }
        }
    }

    // ---- ACO (CFG_SECTION_OUT, POSITIONAL) → ioexb_aco_details ----

    private void annotateAco(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f) {

        ParsedConfigFile file = filesById.get(f.fileId());
        if (file == null || f.position() == null) {
            return;
        }
        IOEXBAcoDetail row = acoRowForPosition(summary, file, f.fileId(), f.position());
        if (row == null) {
            return; // slot past the configured blocks, or a slot the extractor deduped away → results-only
        }
        String rid = f.result().getId();
        switch (f.kind()) {
            case VALUE -> {
                if (SECTION.equals(f.entryKey())) {
                    add(row, MismatchAnnotation.value("fma_1_2", null, plus1(f.rawExpected()), plus1(f.rawActual()), rid));
                } else if (SLCT_TIMEOUT.equals(f.entryKey())) {
                    add(row, MismatchAnnotation.value("time_out", null,
                            timeout(file, f.rawExpected()), timeout(file, f.rawActual()), rid));
                }
                // ID (aco_fmaId) is not a displayed column → results-only.
            }
            case UNEXPECTED -> add(row, MismatchAnnotation.unexpected("aco_fma1", null, row.getAcoFma1(), rid));
            case MISSING -> { /* unreachable here (position < occ.size); MISSING handled above */ }
        }
    }

    /**
     * The {@code ioexb_aco_details} row belonging to CFG_SECTION_OUT slot {@code position}, or {@code null}
     * when that slot has no row of its own.
     *
     * <p>ACO is POSITIONAL: blocks are paired per IO-EXB card ({@code 2i-1, 2i} = card i's track-1/track-2)
     * and the slot IS the output-FMA identity. But {@code IOEXBAcoExtractorService} builds rows with a
     * dedup on the block's header comment, so two slots sharing an FMA name collapse into one row --
     * routinely, not just for a single-track card's filler duplicate. In a captured production package 113
     * rows were built from 118 blocks, and one AEB whose card is {@code (250BT, 250BT)} produced a single
     * row for its two blocks.</p>
     *
     * <p>Resolving the row by re-deriving that comment (what this used to do) therefore returned the SAME
     * row for both slots, and a finding on the second slot was painted onto the first slot's cell -- a
     * mismatch badge on a value that is actually correct, while the block that failed appears nowhere.</p>
     *
     * <p>The shared extractor cannot be changed: it feeds Phase 1's {@code generateSummary} too, and
     * dropping the dedup there would break the locked Phase-1 byte-unchanged constraint. {@code
     * vtf-337-scope.md} 2 prescribes the alternative used here -- annotator-side reconstruction: replay the
     * extractor's own walk over the blocks in file order, counting which slot created which row, so a slot
     * maps to the row it actually built. A slot that was deduped away has no cell and stays results-only,
     * which is the honest outcome; inventing one would mean annotating a row built from a different block.</p>
     */
    private IOEXBAcoDetail acoRowForPosition(ValidationSummary summary, ParsedConfigFile file, int fileId,
            int position) {

        List<ConfigBlock> occ = occurrences(file, SECTION_OUT);
        if (position < 0 || position >= occ.size()) {
            return null;
        }
        List<IOEXBAcoDetail> rows = new ArrayList<>();
        for (IOEXBAcoDetail candidate : nonNull(summary.getIoexbAcoDetails())) {
            if (eq(fileId, candidate.getDpId())) {
                rows.add(candidate);
            }
        }

        // Mirrors IOEXBAcoExtractorService: file order, and only a non-blank repeated comment is dropped.
        Set<String> seen = new LinkedHashSet<>();
        int rowIndex = -1;
        for (int slot = 0; slot <= position; slot++) {
            String comment = entryComment(occ.get(slot), SECTION_OUT);
            boolean blank = comment == null || comment.isEmpty();
            if (!blank && seen.contains(comment)) {
                if (slot == position) {
                    return null; // this slot built no row of its own
                }
                continue;
            }
            rowIndex++;
            if (!blank) {
                seen.add(comment);
            }
        }
        return rowIndex < rows.size() ? rows.get(rowIndex) : null;
    }

    // ---- DP details: CFG_SECTION RESET_OUT (SINGLE, control-table derived) → dp_details ----

    /**
     * {@code RESET_OUT} is derived from the Control table's Reset Type rather than read from the CQ-IR, so
     * it arrives as an instanced finding. Its cell is the existing DP Details column
     * ({@code ExcelColumnMapper} "INACTIVE RESET RESTRICTION / RESET_OUT"), whose extractor stores the
     * mapped label -- hence the {@code value-mappings} display transform on both sides.
     */
    private void annotateDpDetail(ValidationSummary summary, InstancedFinding f) {
        if (f.kind() != MismatchAnnotation.Kind.VALUE || !"RESET_OUT".equals(f.entryKey())) {
            return;
        }
        DpDetail row = first(summary.getDpDetails(), r -> eq(f.fileId(), r.getDpCanId()));
        if (row == null) {
            return;
        }
        add(row, MismatchAnnotation.value("reset_out", null,
                mapped("RESET_OUT", f.rawExpected()), mapped("RESET_OUT", f.rawActual()), f.result().getId()));
    }

    // ---- IOEXB behaviour: CFG_AXCNT BEHAV_INPUT3 (SINGLE) → ioexb_behaviour_details ----

    private void annotateIoexbBehaviour(ValidationSummary summary, InstancedFinding f) {
        if (f.kind() != MismatchAnnotation.Kind.VALUE || !"BEHAV_INPUT3".equals(f.entryKey())) {
            return;
        }
        IOEXBBehaviourDetail row = first(summary.getIoexbBehaviourDetails(), r -> eq(f.fileId(), r.getDpId()));
        if (row == null) {
            return;
        }
        add(row, MismatchAnnotation.value("behav_input3", null,
                mapped("BEHAV_INPUT3", f.rawExpected()), mapped("BEHAV_INPUT3", f.rawActual()), f.result().getId()));
    }

    // ---- Data Transmission: CFG_DATA_OUT (BY_IDENTITY on source DP) → data_transmission_details ----

    private void annotateDataTransmission(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            InstancedFinding f) {

        String sourceId = f.linkedId().get(ID);
        DataTransmissionDetail row = first(summary.getDataTransmissionDetail(),
                r -> eq(f.fileId(), r.getDpId()) && eqTrim(r.getSourceDpId(), sourceId));
        if (row == null) {
            return; // MISSING source → no row; the scalar-row table has no slot to append → results-only
        }
        String rid = f.result().getId();
        switch (f.kind()) {
            case VALUE -> {
                if (SLCT_TIMEOUT.equals(f.entryKey())) {
                    add(row, MismatchAnnotation.value("timeout", null,
                            timeout(filesById.get(f.fileId()), f.rawExpected()),
                            timeout(filesById.get(f.fileId()), f.rawActual()), rid));
                }
            }
            case UNEXPECTED -> add(row, MismatchAnnotation.unexpected("source_dp_name", null, row.getSourceDpName(), rid));
            case MISSING -> { /* no row to append; results-only */ }
        }
    }

    // ---- Forwarding: CFG_FWRD_ACD → ethernet_details (set-equality, re-resolved by index) ----

    private void annotateForwarding(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            List<ParsedConfigFile> parsedFiles, int comId, List<InstancedFinding> group) {

        EthernetDetail row = first(summary.getEthernetDetails(), r -> eq(comId, r.getId()));
        ParsedConfigFile com = filesById.get(comId);
        if (row == null || com == null) {
            return;
        }

        java.util.Set<String> expectedKeys = new java.util.HashSet<>();
        Map<String, String> unexpectedRid = new LinkedHashMap<>();
        List<InstancedFinding> missing = new ArrayList<>();
        for (InstancedFinding f : group) {
            String key = f.linkedId().get(CAN_TX_ID) + "|" + f.linkedId().get(DEST_COM);
            if (f.kind() == null || f.kind() == MismatchAnnotation.Kind.MISSING) {
                expectedKeys.add(key); // matched (PASS) + missing members make up the expected set
            }
            if (f.kind() == MismatchAnnotation.Kind.MISSING) {
                missing.add(f);
            } else if (f.kind() == MismatchAnnotation.Kind.UNEXPECTED) {
                unexpectedRid.put(key, f.result().getId());
            }
        }

        List<String> ids = mutable(row.getFwrdAcdToDpIds());
        List<String> dtls = mutable(row.getFwrdAcdToDpDtls());

        // UNEXPECTED: re-resolve actual forwards (block order == ethernet array order) and flag the strays.
        // Annotation runs only after evaluation proved the resolution clean (VTF-360 end-check), so the
        // throwaway collector stays empty; annotation is best-effort either way.
        List<ForwardMember> resolved = forwardingResolver.resolveActualForwards(
                com, parsedFiles, new BaselineInconsistencies());
        for (int i = 0; i < resolved.size(); i++) {
            String key = resolved.get(i).canTxId() + "|" + resolved.get(i).destCom();
            if (!expectedKeys.contains(key) && unexpectedRid.containsKey(key)) {
                add(row, MismatchAnnotation.unexpected("fwrd_acd_to_dp_dtls", i, at(dtls, i), unexpectedRid.get(key)));
            }
        }

        // MISSING: append an empty slot to both parallel arrays at a real index.
        for (InstancedFinding f : missing) {
            ids.add("");
            dtls.add("");
            add(row, MismatchAnnotation.missing("fwrd_acd_to_dp_dtls", dtls.size() - 1,
                    dpName(filesById, f.linkedId().get(CAN_TX_ID)), f.result().getId()));
        }
        row.setFwrdAcdToDpIds(ids);
        row.setFwrdAcdToDpDtls(dtls);
    }

    // ---- Scalar results that own a detail cell ----

    /**
     * Routes a failing scalar result onto its detail cell. Every route resolves the row by the result's
     * {@code fileName} first: a scalar {@link ValidationResult} carries no file id, only the name, and
     * without that gate one file's verdict is stamped onto every DP's rows -- the broadcast shape the
     * VTF-359..362 series spent four tickets removing from the rules.
     */
    private void dispatchScalar(ValidationSummary summary, Map<Integer, ParsedConfigFile> filesById,
            Map<String, Integer> fileIdByName, ValidationResult r) {

        Integer fileId = fileIdByName.get(r.getFileName());
        if (fileId == null) {
            return; // result cannot be attributed to an uploaded file -> results-only
        }
        String block = r.getBlockName();
        String entry = r.getEntryKey();
        if (SECTION_OUT.equals(block) && ACO_AUX_FIELD.containsKey(entry)) {
            annotateAcoScalar(summary, fileId, r, ACO_AUX_FIELD.get(entry));
        } else if (SUPERVIS_BLOCKS.contains(block) && SUPERVISOR_SCALAR_FIELD.containsKey(entry)) {
            annotateSupervisorScalar(summary, filesById.get(fileId), fileId, r,
                    SUPERVISOR_SCALAR_FIELD.get(entry));
        } else if (CFG_ZP.equals(block) && CHC_ZP_FIELD.containsKey(entry)) {
            annotateChcScalar(summary, fileId, r, CHC_ZP_FIELD.get(entry));
        }
    }

    /**
     * ACO aux (CFG_SECTION_OUT) to ioexb_aco_details. A file can hold several CFG_SECTION_OUT occurrences
     * and {@code InputMatchRule} flattens them into one result, so the offending row(s) <em>within</em> the
     * file are still picked by value; the file gate is what stops that comparison reaching other DPs.
     */
    private void annotateAcoScalar(ValidationSummary summary, int fileId, ValidationResult r, String field) {
        String expected = display(r.getEntryKey(), r.getExpectedValue());
        boolean annotated = false;
        for (IOEXBAcoDetail row : nonNull(summary.getIoexbAcoDetails())) {
            if (!eq(fileId, row.getDpId())) {
                continue;
            }
            String actual = acoField(row, field);
            if (!expected.equals(actual)) {
                add(row, MismatchAnnotation.value(field, null, expected, actual, r.getId()));
                annotated = true;
            }
        }
        if (!annotated) {
            noCell(r, "ioexb_aco_details");
        }
    }

    /** Supervisor scalars (CFG_SUPERVIS_FMA1/2 RESET_TYPE, RESET_DELAY) to supervisor_details. */
    private void annotateSupervisorScalar(ValidationSummary summary, ParsedConfigFile file, int fileId,
            ValidationResult r, String field) {

        SupervisorDetail row = supervisorRowFor(summary, file, fileId, r.getBlockName());
        if (row == null) {
            noCell(r, "supervisor_details");
            return;
        }
        addScalar(row, field, r);
    }

    /** CFG_ZP count scalars to chc_details (one row per file, per {@code CHCExtractorService}). */
    private void annotateChcScalar(ValidationSummary summary, int fileId, ValidationResult r, String field) {
        CHCDetail row = first(summary.getChcDetails(), c -> eq(fileId, c.getDpId()));
        if (row == null) {
            noCell(r, "chc_details");
            return;
        }
        addScalar(row, field, r);
    }

    /**
     * Attaches a scalar result to a cell using the verdict the engine already reached. The row is never
     * re-compared against the expected value: CHC's counts are stored raw while {@code value-mappings}
     * defines labels for them, so a display re-compare would flag every row, correct ones included.
     */
    private <T extends Annotatable> void addScalar(T row, String field, ValidationResult r) {
        add(row, MismatchAnnotation.value(field, null,
                display(r.getEntryKey(), r.getExpectedValue()),
                display(r.getEntryKey(), r.getActualValue()), r.getId()));
    }

    /**
     * The supervisor row a scalar CFG_SUPERVIS_FMA* result belongs to. {@code SupervisorExtractorService}
     * emits one row per (file, FMA) but the DTO carries no FMA field, so the row is located positionally:
     * it emits FMA1 before FMA2 and the later sort compares {@code dpId} only ({@code List.sort} is
     * stable), so a file's rows line up in order with the FMA blocks the file actually has. If those two
     * disagree the result stays results-only rather than risk annotating the wrong FMA.
     */
    private SupervisorDetail supervisorRowFor(ValidationSummary summary, ParsedConfigFile file, int fileId,
            String block) {

        if (file == null || file.getBlocks() == null) {
            return null;
        }
        List<String> present = new ArrayList<>();
        for (String candidate : SUPERVIS_ORDER) {
            if (file.getBlocks().stream().anyMatch(b -> candidate.equals(b.getName()))) {
                present.add(candidate);
            }
        }
        List<SupervisorDetail> rows = new ArrayList<>();
        for (SupervisorDetail row : nonNull(summary.getSupervisorDetail())) {
            if (eq(fileId, row.getDpId())) {
                rows.add(row);
            }
        }
        int slot = present.indexOf(block);
        return (slot >= 0 && rows.size() == present.size()) ? rows.get(slot) : null;
    }

    /**
     * The detail cell's form of a rule value. A {@code Set.toString()} payload ("[3, 4]", what the
     * {@code Optional*} rules store) is split into its raw tokens and mapped per token, so a tooltip never
     * shows bracket form against a display-form column. The split happens before mapping, so a label
     * containing a comma cannot be corrupted by it.
     */
    private String display(String entryKey, String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
            StringBuilder joined = new StringBuilder();
            for (String token : trimmed.substring(1, trimmed.length() - 1).split(",")) {
                if (joined.length() > 0) {
                    joined.append(", ");
                }
                joined.append(displayOne(entryKey, token.trim()));
            }
            return joined.toString();
        }
        return displayOne(entryKey, trimmed);
    }

    private String displayOne(String entryKey, String raw) {
        return DISPLAY_MAPPED_ENTRIES.contains(entryKey) ? mapped(entryKey, raw) : raw;
    }

    /**
     * Debug rather than warn: until the CFG_SECTION_OUT rules are scoped to the files that carry the block,
     * a CFG_AXCNT-only file fails all seven aux keys with no ACO row to land on, and warning would put one
     * line per key per such file into every run.
     */
    private void noCell(ValidationResult r, String table) {
        log.debug("No {} row for {}.{} on {} - result {} stays results-only",
                table, r.getBlockName(), r.getEntryKey(), r.getFileName(), r.getId());
    }

    private String acoField(IOEXBAcoDetail row, String field) {
        return switch (field) {
            case "clr_occ" -> row.getClrOcc();
            case "type_aux1" -> row.getTypeAux1();
            case "type_aux2" -> row.getTypeAux2();
            case "aux1_out" -> row.getAux1Out();
            case "aux1_no_nc" -> row.getAux1NoNc();
            case "aux2_out" -> row.getAux2Out();
            case "aux2_no_nc" -> row.getAux2NoNc();
            default -> null;
        };
    }

    // ---- helpers ----

    private int supMemberIndex(SupervisorDetail row, String id, String fmaPlus1) {
        List<String> ids = row.getSupByTsDpId();
        List<String> fmas = row.getSupByTsFma();
        if (ids == null) {
            return -1;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (eqTrim(ids.get(i), id) && (fmaPlus1 == null || fmas == null || eqTrim(at(fmas, i), fmaPlus1))) {
                return i;
            }
        }
        return -1;
    }

    private List<ConfigBlock> occurrences(ParsedConfigFile file, String block) {
        return file.getBlocks().stream()
                .filter(b -> block.equals(b.getName()))
                .sorted((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
    }

    private String entryComment(ConfigBlock block, String key) {
        return block.getEntries().stream()
                .filter(e -> key.equals(e.getKey()))
                .map(ConfigEntry::getComment)
                .findFirst()
                .orElse("");
    }

    /** DP/COM display name = the file's ID-block comment; falls back to the id itself. */
    private String dpName(Map<Integer, ParsedConfigFile> filesById, String id) {
        if (id == null) {
            return null;
        }
        try {
            ParsedConfigFile file = filesById.get(Integer.parseInt(id.trim()));
            if (file != null) {
                String name = file.getBlocks().stream()
                        .filter(b -> ID.equals(b.getName()))
                        .flatMap(b -> b.getEntries().stream())
                        .filter(e -> ID.equals(e.getKey()))
                        .map(ConfigEntry::getComment)
                        .findFirst()
                        .orElse(null);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (NumberFormatException ignored) {
            // fall through to the raw id
        }
        return id;
    }

    private String mapped(String fieldKey, String raw) {
        return valueMappingService.mapValue(fieldKey, raw);
    }

    private String timeout(ParsedConfigFile file, String raw) {
        if (file == null || raw == null) {
            return raw;
        }
        return ConfigExtractionUtil.extractTimeoutValue(file, raw.trim());
    }

    private String plus1(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return String.valueOf(Integer.parseInt(raw.trim()) + 1);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private <T extends Annotatable> void add(T row, MismatchAnnotation annotation) {
        if (row.getMismatches() == null) {
            row.setMismatches(new ArrayList<>());
        }
        row.getMismatches().add(annotation);
    }

    /** Appends to a (possibly null/immutable) list field via its setter; returns the new element's index. */
    private int append(List<String> current, java.util.function.Consumer<List<String>> setter, String value) {
        List<String> list = mutable(current);
        list.add(value);
        setter.accept(list);
        return list.size() - 1;
    }

    private List<String> mutable(List<String> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private int idxOf(List<String> list, String value) {
        if (list == null || value == null) {
            return -1;
        }
        String v = value.trim();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) != null && list.get(i).trim().equals(v)) {
                return i;
            }
        }
        return -1;
    }

    private String at(List<String> list, int i) {
        return (list != null && i >= 0 && i < list.size()) ? list.get(i) : null;
    }

    private boolean eq(int fileId, String rowValue) {
        return rowValue != null && String.valueOf(fileId).equals(rowValue.trim());
    }

    private boolean eqTrim(String a, String b) {
        return a != null && b != null && a.trim().equals(b.trim());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private <T> T first(List<T> list, java.util.function.Predicate<T> match) {
        if (list == null) {
            return null;
        }
        for (T t : list) {
            if (match.test(t)) {
                return t;
            }
        }
        return null;
    }
}
