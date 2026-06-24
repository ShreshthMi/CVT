package com.frauscher.ConfigurationValidationService.validation.instanced;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
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
 */
@Slf4j
@Component
public class InstancedExpectationEvaluator {

    /** {@code CFG_FWRD_ACD} needs socket→COM/IP resolution (§5.6) — handled outside this generic path. */
    static final String FORWARDING_BLOCK = "CFG_FWRD_ACD";

    private static final String RULE_INPUT_MATCH = "InputMatch";
    private static final String RULE_OPTIONAL = "OptionalInputMatchOrBlockNotFound";
    private static final String RULE_IDENTITY = "IdentitySetMatch";
    private static final String RULE_POSITIONAL = "PositionalMatch";

    public List<ValidationResult> evaluate(
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

        List<ValidationResult> results = new ArrayList<>();
        for (Map.Entry<GroupKey, List<InstancedExpectation>> group : groups.entrySet()) {
            GroupKey key = group.getKey();

            if (FORWARDING_BLOCK.equals(key.block())) {
                continue; // §5.6 socket→COM resolution — separate path (reads the COM ADC targets).
            }

            ParsedConfigFile file = filesById.get(key.fileId());
            if (file == null) {
                log.debug("v2 instanced: no uploaded ADC with id {} for block {}", key.fileId(), key.block());
                continue;
            }

            List<InstancedExpectation> rows = group.getValue();
            switch (rows.get(0).matchMode()) {
                case SINGLE -> evaluateSingle(results, file, rows);
                case BY_IDENTITY -> evaluateByIdentity(results, file, key.block(), rows);
                case POSITIONAL -> evaluatePositional(results, file, key.block(), rows);
            }
        }
        return results;
    }

    // ---- SINGLE: mirrors InputMatch (mandatory) / OptionalInputMatchOrBlockNotFound (optional) ----

    private void evaluateSingle(List<ValidationResult> results, ParsedConfigFile file, List<InstancedExpectation> rows) {
        for (InstancedExpectation e : rows) {
            List<String> actuals = values(file, e.block(), e.key());
            boolean optional = e.defaultValue() != null;

            if (optional && !hasBlock(file, e.block())) {
                boolean pass = e.expectedValue().equals(e.defaultValue());
                results.add(result(file, RULE_OPTIONAL, e.block(), e.key(), e.expectedValue(),
                        ValidationConstants.CONFIG_BLOCK_NOT_FOUND, pass));
                continue;
            }

            if (actuals.isEmpty()) {
                results.add(result(file, optional ? RULE_OPTIONAL : RULE_INPUT_MATCH, e.block(), e.key(),
                        e.expectedValue(), ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND, false));
                continue;
            }

            boolean pass = optional
                    ? actuals.stream().anyMatch(a -> e.expectedValue().equals(trim(a)))
                    : actuals.stream().allMatch(a -> e.expectedValue().equals(trim(a)));
            results.add(result(file, optional ? RULE_OPTIONAL : RULE_INPUT_MATCH, e.block(), e.key(),
                    e.expectedValue(), join(actuals), pass));
        }
    }

    // ---- BY_IDENTITY: strict set-equality over occurrences keyed by their identity entries ----

    private void evaluateByIdentity(
            List<ValidationResult> results, ParsedConfigFile file, String block, List<InstancedExpectation> rows) {

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
                results.add(result(file, RULE_IDENTITY, block, idLabel,
                        describeExpected(member.getValue()),
                        ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND, false));
                continue;
            }

            ConfigBlock occurrence = occurrences.get(hits.get(0));
            for (int idx : hits) {
                matched[idx] = true;
            }
            for (InstancedExpectation e : member.getValue()) {
                String actual = entryValue(occurrence, e.key());
                boolean pass = e.expectedValue().equals(trim(actual));
                results.add(result(file, RULE_IDENTITY, block, e.key() + "[" + idLabel + "]",
                        e.expectedValue(),
                        actual == null ? ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND : actual, pass));
            }
        }

        for (int i = 0; i < occurrences.size(); i++) {
            if (!matched[i]) {
                results.add(result(file, RULE_IDENTITY, block,
                        identityLabel(identityOf(occurrences.get(i), idKeys)),
                        ValidationConstants.UNEXPECTED_OCCURRENCE,
                        ValidationConstants.UNEXPECTED_OCCURRENCE, false));
            }
        }
    }

    // ---- POSITIONAL: ordered slot match (ACO / CFG_SECTION_OUT) ----

    private void evaluatePositional(
            List<ValidationResult> results, ParsedConfigFile file, String block, List<InstancedExpectation> rows) {

        Map<Integer, List<InstancedExpectation>> slots = new TreeMap<>();
        for (InstancedExpectation e : rows) {
            slots.computeIfAbsent(e.position(), k -> new ArrayList<>()).add(e);
        }

        List<ConfigBlock> occurrences = occurrences(file, block);

        for (Map.Entry<Integer, List<InstancedExpectation>> slot : slots.entrySet()) {
            int position = slot.getKey();
            if (position >= occurrences.size()) {
                for (InstancedExpectation e : slot.getValue()) {
                    results.add(result(file, RULE_POSITIONAL, block, e.key() + "[pos=" + position + "]",
                            e.expectedValue(), ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND, false));
                }
                continue;
            }
            ConfigBlock occurrence = occurrences.get(position);
            for (InstancedExpectation e : slot.getValue()) {
                String actual = entryValue(occurrence, e.key());
                boolean pass = e.expectedValue().equals(trim(actual));
                results.add(result(file, RULE_POSITIONAL, block, e.key() + "[pos=" + position + "]",
                        e.expectedValue(),
                        actual == null ? ValidationConstants.CONFIG_BLOCK_OR_PARAM_NOT_FOUND : actual, pass));
            }
        }

        for (int i = slots.size(); i < occurrences.size(); i++) {
            results.add(result(file, RULE_POSITIONAL, block, "[pos=" + i + "]",
                    ValidationConstants.UNEXPECTED_OCCURRENCE, ValidationConstants.UNEXPECTED_OCCURRENCE, false));
        }
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

    /** Grouping key — all expectations for one block of one file are evaluated together. */
    private record GroupKey(int fileId, String block) {
    }
}
