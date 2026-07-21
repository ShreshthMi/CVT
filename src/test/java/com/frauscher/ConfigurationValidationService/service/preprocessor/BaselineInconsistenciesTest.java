package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;

/** The VTF-360 accumulator: dedup, ordering, and the single numbered-list rejection. */
class BaselineInconsistenciesTest {

    @Test
    void emptyCollectorDoesNotThrow() {
        assertDoesNotThrow(new BaselineInconsistencies()::throwIfAny);
    }

    @Test
    void enumeratesEveryProblemInOneNumberedMessage() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        problems.add("first problem");
        problems.add("second problem");

        BaselineInconsistentException ex =
                assertThrows(BaselineInconsistentException.class, problems::throwIfAny);
        assertEquals("PHASE2_BASELINE_INCONSISTENT", ex.getErrorCode());
        assertEquals("Baseline inconsistent — 2 problems: 1) first problem; 2) second problem",
                ex.getMessage());
    }

    @Test
    void singularMessageForOneProblem() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        problems.add("only problem");

        BaselineInconsistentException ex =
                assertThrows(BaselineInconsistentException.class, problems::throwIfAny);
        assertTrue(ex.getMessage().startsWith("Baseline inconsistent — 1 problem: 1) only problem"));
    }

    @Test
    void deduplicatesIdenticalItemsKeepingInsertionOrder() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        problems.add("shared root cause");
        problems.add("other problem");
        problems.add("shared root cause");

        assertEquals(List.of("shared root cause", "other problem"), problems.items());
    }
}
