package com.frauscher.ConfigurationValidationService.validation.instanced;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.frauscher.ConfigurationValidationService.validation.instanced.ForwardingDestinationResolver.ForwardMember;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.MismatchAnnotation;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.service.preprocessor.InstancedExpectation;
import com.frauscher.ConfigurationValidationService.validation.ValidationStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes the {@code instancedExpectations} bucket (v2-expectations-contract.md §5–§6) — the per-entity
 * half of the v2 validation the flattening Phase 1 engine cannot express. For each expectation it selects
 * the {@link ParsedConfigFile} whose {@code id == fileId} and the ADC block occurrence(s) the expectation
 * targets, then compares raw entry values. Occurrence selection is the new BE-06 mechanism (the existing
 * {@code FileContext.values} flattens every occurrence of a block); it reads the raw {@code ID}/{@code
 * SECTION}/… entries rather than the display-mapped, comment-deduped extractor output (Option B).
 *
 * <p>Three strategies, keyed by {@link com.frauscher.ConfigurationValidationService.service.preprocessor.MatchMode}:</p>
 * <ul>
 *   <li><b>SINGLE</b> — validate the file's value(s) for the entry (mirrors {@code InputMatch}; a non-null
 *       {@code defaultValue} makes it optional, mirroring {@code OptionalInputMatchOrBlockNotFound}: an
 *       absent block PASSes iff {@code defaultValue == expectedValue}).</li>
 *   <li><b>BY_IDENTITY</b> — <b>strict set-equality</b> between the expected members (one per distinct
 *       {@code linkedId}) and the file's occurrences of the block keyed by their identity entries: a
 *       missing expected member FAILs, an EXTRA actual occurrence FAILs with the {@code
 *       UNEXPECTED_OCCURRENCE} sentinel, and each matched member's entry values are checked. (The stock
 *       {@code MultipleBlockMultipleInputMatchRule} only does {@code actual ⊆ expected}, so this is new.)</li>
 *   <li><b>POSITIONAL</b> — the i-th occurrence (ordered by {@code blockIndex}) must match the slot-i
 *       expectation, so a complete-but-re-sequenced config FAILs. ACO ({@code CFG_SECTION_OUT}) only.</li>
 * </ul>
 *
 * <p>A {@code fileId} with no uploaded ADC is skipped (the scalar engine likewise validates only the
 * uploaded files). {@code CFG_FWRD_ACD} (BY_IDENTITY with a socket-derived {@code DEST_COM}) is handled
 * separately by the §5.6 resolution path; it is not validated here.</p>
 *
 * <p>{@link #evaluateAnnotated} returns each result wrapped in an {@link InstancedFinding} carrying the raw
 * coordinate (block / identity / position / checked entry) for the BE-07 cell-annotation join; {@link
 * #evaluate} unwraps to the plain result list and is byte-identical to the BE-06 behaviour.</p>
 */
@Slf4j
@Component
public class InstancedExpectationEvaluator {

    /** {@code CFG_FWRD_ACD} is BY_IDENTITY but its dest COM is socket-derived (§5.6) — its own path. */
    static final String FORWARDING_BLOCK = "CFG_FWRD_ACD";

    private static final String RULE_INPUT_MATCH = "InputMatch";
    private static final String RULE_OPTIONAL = "OptionalInputMatchOrBlockNotFound";
    private static final String RULE_IDENTITY = "IdentitySetMatch";
    private static final String RULE_POSITIONAL = "PositionalMatch";
    private static final String DEST_COM = "DEST_COM";
    private static final String CAN_TX_ID = "CAN_TX_ID";

    private final ForwardingDestinationResolver forwardingResolver;

    public InstancedExpectationEvaluator(ForwardingDestinationResolver forwardingResolver) {
        this.forwardingResolver = forwardingResolver;
    }

    /** The plain BE-06 result list (no annotation coordinates). */
    public List<ValidationResult> evaluate(
            List<ParsedConfigFile> parsedFiles, List<InstancedExpectation> expectations) {
        return evaluateAnnotated(parsedFiles, expectations).stream()
                .map(InstancedFinding::result)
                .toList();
    }

    /** As {@link #evaluate} but each result is wrapped with its BE-07 cell-annotation coordinate. */
    public List<InstancedFinding> evaluateAnnotated(
            List<ParsedConfigFile> parsedFiles, List<InstancedExpectation> expectations) {

        if (expectations == null || expectations.isEmpty()) {
            return List.of();
        }

        Map<Integer, ParsedConfigFile> filesById = new HashMap<>();
        if (parsedFiles != null) {
            for (ParsedConfigFile file : parsedFiles) {
                filesById.putIfAbsent(file.getId(), file);
            }
        }

        // Group by (fileId, block) preserving emission order; matchMode is uniform within a block.
        Map<GroupKey, List<InstancedExpectation>> groups = new LinkedHashMap<>();
        for (InstancedExpectation e : expectations) {
            groups.computeIfAbsent(new GroupKey(e.fileId(), e.block()), k -> new ArrayList<>()).add(e);
        }

        List<InstancedFinding> findings = new ArrayList<>();
        for (Map.Entry<GroupKey, List<InstancedExpectation>> group : groups.entrySet()) {
            GroupKey key = group.getKey();

            ParsedConfigFile file = filesById.get(key.fileId());
            if (file == null) {
                log.debug("v2 instanced: no uploaded ADC with id {} for block {}", key.fileId(), key.block());
                continue;
            }

            List<InstancedExpectation> rows = group.getValue();
            switch (rows.get(0).matchMode()) {
                case SINGLE -> evaluateSingle(findings, file, rows);
                case BY_IDENTITY -> {
                    if (FORWARDING_BLOCK.equals(key.block())) {
                        evaluateForwarding(findings, parsedFiles, file, rows);
                    } else {
                        evaluateByIdentity(findings, file, key.block(), rows);
                    }
                }
                case POSITIONAL -> evaluatePositional(findings, file, key.block(), rows);
            }
        }
        return findings;
    }

    // ---- SINGLE: mirrors InputMatch (mandatory) / OptionalInputMatchOrBlockNotFound (optional) ----

    private void evaluateSingle(List<InstancedFinding> out, ParsedConfigFile file, List<InstancedExpectation> rows) {
        for (InstancedExpectation e : rows) {
            List<String> actuals = values(file, e.block(), e.key());
            boolean optional = e.defaultValue() != null;

            if (optional && !hasBlock(file, e.block())) {
                boolean pass = e.expectedValue().equals(e.defaultValue());
                out.add(single(result(file, RULE_OPTIONAL, e.block(), e.key(), e.expectedValue(),
                        ValidationConstants.CONFIG_BLOCK_NOT_FOUND, pass), e.block(), file.getId(),
                        pass ? null : MismatchAnnotation.Kind.VALUE, e.key(), e.expectedValue(), null));
                continue;
            }

            if (actuals.isEmpty()) {
                out.add(single(result(file, optional ? RULE_OPTIONAL : RULE_INPUT_MATCH, e.block(), e.key(),
                        e.expectedValue(), ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND, false),
                        e.block(), file.getId(), MismatchAnnotation.Kind.VALUE, e.key(), e.expectedValue(), null));
                continue;
            }

            boolean pass = optional
                    ? actuals.stream().anyMatch(a -> e.expectedValue().equals(trim(a)))
                    : actuals.stream().allMatch(a -> e.expectedValue().equals(trim(a)));
            out.add(single(result(file, optional ? RULE_OPTIONAL : RULE_INPUT_MATCH, e.block(), e.key(),
                    e.expectedValue(), join(actuals), pass), e.block(), file.getId(),
                    pass ? null : MismatchAnnotation.Kind.VALUE, e.key(), e.expectedValue(), join(actuals)));
        }
    }

    // ---- BY_IDENTITY: strict set-equality over occurrences keyed by their identity entries ----

    private void evaluateByIdentity(
            List<InstancedFinding> out, ParsedConfigFile file, String block, List<InstancedExpectation> rows) {

        // Members: one per distinct linkedId, carrying its (key → expectedValue) checks, in emission order.
        Map<Map<String, String>, List<InstancedExpectation>> members = new LinkedHashMap<>();
        for (InstancedExpectation e : rows) {
            members.computeIfAbsent(e.linkedId(), k -> new ArrayList<>()).add(e);
        }
        Collection<String> idKeys = rows.get(0).linkedId().keySet();

        List<ConfigBlock> occurrences = occurrences(file, block);
        boolean[] matched = new boolean[occurrences.size()];

        for (Map.Entry<Map<String, String>, List<InstancedExpectation>> member : members.entrySet()) {
            Map<String, String> linkedId = member.getKey();
            String idLabel = identityLabel(linkedId);

            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < occurrences.size(); i++) {
                if (identityMatches(linkedId, idKeys, occurrences.get(i))) {
                    hits.add(i);
                }
            }

            if (hits.isEmpty()) {
                out.add(new InstancedFinding(
                        result(file, RULE_IDENTITY, block, idLabel, describeExpected(member.getValue()),
                                ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND, false),
                        block, file.getId(), MismatchAnnotation.Kind.MISSING, linkedId, null, null, null, null,
                        memberExpected(member.getValue())));
                continue;
            }

            ConfigBlock occurrence = occurrences.get(hits.get(0));
            for (int idx : hits) {
                matched[idx] = true;
            }
            for (InstancedExpectation e : member.getValue()) {
                String actual = entryValue(occurrence, e.key());
                boolean pass = e.expectedValue().equals(trim(actual));
                out.add(new InstancedFinding(
                        result(file, RULE_IDENTITY, block, e.key() + "[" + idLabel + "]", e.expectedValue(),
                                actual == null ? ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND : actual, pass),
                        block, file.getId(), pass ? null : MismatchAnnotation.Kind.VALUE, linkedId, null,
                        e.key(), e.expectedValue(), actual, memberExpected(member.getValue())));
            }
        }

        for (int i = 0; i < occurrences.size(); i++) {
            if (!matched[i]) {
                Map<String, String> id = identityOf(occurrences.get(i), idKeys);
                out.add(new InstancedFinding(
                        result(file, RULE_IDENTITY, block, identityLabel(id),
                                ValidationConstants.UNEXPECTED_OCCURRENCE,
                                ValidationConstants.UNEXPECTED_OCCURRENCE, false),
                        block, file.getId(), MismatchAnnotation.Kind.UNEXPECTED, id, null, null, null, null, Map.of()));
            }
        }
    }

    // ---- POSITIONAL: ordered slot match (ACO / CFG_SECTION_OUT) ----

    private void evaluatePositional(
            List<InstancedFinding> out, ParsedConfigFile file, String block, List<InstancedExpectation> rows) {

        Map<Integer, List<InstancedExpectation>> slots = new TreeMap<>();
        for (InstancedExpectation e : rows) {
            slots.computeIfAbsent(e.position(), k -> new ArrayList<>()).add(e);
        }

        List<ConfigBlock> occurrences = occurrences(file, block);

        for (Map.Entry<Integer, List<InstancedExpectation>> slot : slots.entrySet()) {
            int position = slot.getKey();
            if (position >= occurrences.size()) {
                for (InstancedExpectation e : slot.getValue()) {
                    out.add(new InstancedFinding(
                            result(file, RULE_POSITIONAL, block, e.key() + "[pos=" + position + "]",
                                    e.expectedValue(), ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND, false),
                            block, file.getId(), MismatchAnnotation.Kind.MISSING, Map.of(), position,
                            e.key(), e.expectedValue(), null, Map.of()));
                }
                continue;
            }
            ConfigBlock occurrence = occurrences.get(position);
            for (InstancedExpectation e : slot.getValue()) {
                String actual = entryValue(occurrence, e.key());
                boolean pass = e.expectedValue().equals(trim(actual));
                out.add(new InstancedFinding(
                        result(file, RULE_POSITIONAL, block, e.key() + "[pos=" + position + "]", e.expectedValue(),
                                actual == null ? ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND : actual, pass),
                        block, file.getId(), pass ? null : MismatchAnnotation.Kind.VALUE, Map.of(), position,
                        e.key(), e.expectedValue(), actual, Map.of()));
            }
        }

        for (int i = slots.size(); i < occurrences.size(); i++) {
            out.add(new InstancedFinding(
                    result(file, RULE_POSITIONAL, block, "[pos=" + i + "]",
                            ValidationConstants.UNEXPECTED_OCCURRENCE, ValidationConstants.UNEXPECTED_OCCURRENCE, false),
                    block, file.getId(), MismatchAnnotation.Kind.UNEXPECTED, Map.of(), i, null, null, null, Map.of()));
        }
    }

    // ---- CFG_FWRD_ACD (§5.6): set-equality over socket→COM-resolved actual forwards ----

    private void evaluateForwarding(
            List<InstancedFinding> out, List<ParsedConfigFile> allFiles, ParsedConfigFile homeCom,
            List<InstancedExpectation> rows) {

        // Expected members: one per distinct (CAN_TX_ID, DEST_COM), in emission order.
        Map<String, Map<String, String>> expected = new LinkedHashMap<>();
        for (InstancedExpectation e : rows) {
            expected.putIfAbsent(memberKey(e.linkedId()), e.linkedId());
        }

        // Actual members: each CFG_FWRD_ACD entry's socket resolved to a present COM (may raise §3.3 → 400).
        Map<String, ForwardMember> actual = new LinkedHashMap<>();
        for (ForwardMember m : forwardingResolver.resolveActualForwards(homeCom, allFiles)) {
            actual.putIfAbsent(m.canTxId() + "|" + m.destCom(), m);
        }

        for (Map.Entry<String, Map<String, String>> member : expected.entrySet()) {
            Map<String, String> linkedId = member.getValue();
            String label = identityLabel(linkedId);
            if (actual.containsKey(member.getKey())) {
                out.add(new InstancedFinding(
                        result(homeCom, RULE_IDENTITY, FORWARDING_BLOCK, DEST_COM + "[" + label + "]",
                                linkedId.get(DEST_COM), linkedId.get(DEST_COM), true),
                        FORWARDING_BLOCK, homeCom.getId(), null, linkedId, null, null, null, null, Map.of()));
            } else {
                out.add(new InstancedFinding(
                        result(homeCom, RULE_IDENTITY, FORWARDING_BLOCK, label,
                                DEST_COM + "=" + linkedId.get(DEST_COM),
                                ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND, false),
                        FORWARDING_BLOCK, homeCom.getId(), MismatchAnnotation.Kind.MISSING, linkedId, null,
                        null, null, null, Map.of()));
            }
        }

        Set<String> expectedKeys = expected.keySet();
        for (Map.Entry<String, ForwardMember> a : actual.entrySet()) {
            if (!expectedKeys.contains(a.getKey())) {
                ForwardMember m = a.getValue();
                Map<String, String> id = new LinkedHashMap<>();
                id.put(CAN_TX_ID, m.canTxId());
                id.put(DEST_COM, m.destCom());
                out.add(new InstancedFinding(
                        result(homeCom, RULE_IDENTITY, FORWARDING_BLOCK,
                                CAN_TX_ID + "=" + m.canTxId() + "," + DEST_COM + "=" + m.destCom(),
                                ValidationConstants.UNEXPECTED_OCCURRENCE, ValidationConstants.UNEXPECTED_OCCURRENCE, false),
                        FORWARDING_BLOCK, homeCom.getId(), MismatchAnnotation.Kind.UNEXPECTED, id, null,
                        null, null, null, Map.of()));
            }
        }
    }

    private String memberKey(Map<String, String> linkedId) {
        return linkedId.get(CAN_TX_ID) + "|" + linkedId.get(DEST_COM);
    }

    // ---- occurrence / entry access ----

    private List<ConfigBlock> occurrences(ParsedConfigFile file, String block) {
        return file.getBlocks().stream()
                .filter(b -> block.equals(b.getName()))
                .sorted((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
    }

    private List<String> values(ParsedConfigFile file, String block, String entryKey) {
        return file.getBlocks().stream()
                .filter(b -> block.equals(b.getName()))
                .flatMap(b -> b.getEntries().stream())
                .filter(e -> entryKey.equals(e.getKey()))
                .map(ConfigEntry::getValue)
                .toList();
    }

    private boolean hasBlock(ParsedConfigFile file, String block) {
        return file.getBlocks().stream().anyMatch(b -> block.equals(b.getName()));
    }

    private String entryValue(ConfigBlock block, String key) {
        return block.getEntries().stream()
                .filter(e -> key.equals(e.getKey()))
                .map(ConfigEntry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean identityMatches(Map<String, String> linkedId, Collection<String> idKeys, ConfigBlock occurrence) {
        for (String k : idKeys) {
            if (!Objects.equals(trim(linkedId.get(k)), trim(entryValue(occurrence, k)))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> identityOf(ConfigBlock occurrence, Collection<String> idKeys) {
        Map<String, String> id = new LinkedHashMap<>();
        for (String k : idKeys) {
            id.put(k, entryValue(occurrence, k));
        }
        return id;
    }

    private String identityLabel(Map<String, String> linkedId) {
        return linkedId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private String describeExpected(List<InstancedExpectation> member) {
        return member.stream()
                .map(e -> e.key() + "=" + e.expectedValue())
                .collect(Collectors.joining(","));
    }

    private Map<String, String> memberExpected(List<InstancedExpectation> member) {
        Map<String, String> map = new LinkedHashMap<>();
        for (InstancedExpectation e : member) {
            map.put(e.key(), e.expectedValue());
        }
        return map;
    }

    private String join(List<String> values) {
        return String.join(",", values);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private ValidationResult result(ParsedConfigFile file, String ruleType, String block, String entryKey,
            String expected, String actual, boolean pass) {
        return new ValidationResult(file.getFileName(), ruleType, block, entryKey, expected, actual,
                (pass ? ValidationStatus.PASS : ValidationStatus.FAIL).name());
    }

    /** Wraps a SINGLE result with its coordinate (no linkedId / position). */
    private InstancedFinding single(ValidationResult result, String block, int fileId,
            MismatchAnnotation.Kind kind, String entryKey, String rawExpected, String rawActual) {
        return new InstancedFinding(result, block, fileId, kind, Map.of(), null, entryKey,
                rawExpected, rawActual, Map.of());
    }

    /** Grouping key — all expectations for one block of one file are evaluated together. */
    private record GroupKey(int fileId, String block) {
    }
}
