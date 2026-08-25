package com.frauscher.ConfigurationValidationService.service.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/**
 * Request-scoped accumulator for post-gate baseline inconsistencies (vtf-360-design.md §2). Builders
 * and the forwarding resolver record every unresolvable reference here — skipping the affected
 * derivation at its documented grain and continuing — so a single run discovers <b>all</b> problems.
 * {@link #throwIfAny} then rejects with one {@code PHASE2_BASELINE_INCONSISTENT} whose message
 * enumerates the complete list (the {@code TrackReconciliationFailedException} precedent: the list
 * rides in the flat {@code message} field).
 *
 * <p>Items are deduplicated in insertion order: the shared {@link BaselineIndex} already prevents the
 * same malformed id being re-reported per builder, and root-cause-keyed messages (e.g. a COM-less CAN
 * segment) collapse naturally when several derivations hit the same defect.</p>
 */
public class BaselineInconsistencies {

    private final Set<String> items = new LinkedHashSet<>();

    /** Records one inconsistency (deduplicated by exact message, insertion order kept). */
    public void add(String item) {
        items.add(item);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<String> items() {
        return new ArrayList<>(items);
    }

    /**
     * Rejects the run with the complete numbered list if anything was collected; no-op otherwise.
     *
     * @throws BaselineInconsistentException carrying every accumulated problem in its message
     */
    public void throwIfAny() {
        if (items.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("Baseline inconsistent — ")
                .append(items.size()).append(items.size() == 1 ? " problem: " : " problems: ");
        int i = 0;
        for (String item : items) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(++i).append(") ").append(item);
        }
        throw new BaselineInconsistentException(sb.toString());
    }
}
