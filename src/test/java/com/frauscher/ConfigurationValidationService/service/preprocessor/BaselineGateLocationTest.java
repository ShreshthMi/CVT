package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;
import com.frauscher.ConfigurationValidationService.exception.TrackReconciliationFailedException;

/**
 * Unit tests for the location-scoped half of the Phase 2 baseline gate: the track multiset is consumed
 * per LOCATION banner against that Station's own FMAs plus the shared pool of FMAs carried by chains
 * with no Station at all, so a track name repeated at two stations is legal while a repeat inside one
 * station is still a rejection. Complements {@link BaselineGateTest}, which pins the flat (untagged)
 * reconciliation and the RSR_TYPE cross-check.
 *
 * <p>Every input here carries a valid CFG_RSR_TYPE so the reconciliation result is what surfaces, and
 * not the §3.2 cross-check firing first.</p>
 */
class BaselineGateLocationTest {

    private final BaselineGate gate = new BaselineGate();

    @Test
    void sameTrackNameAtTwoLocationsIsNoLongerRejected() {
        assertDoesNotThrow(
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "1AXT2"), at("STN2", "1AXT1", "1AXT2")),
                        fct(at("STN1", "1AXT1", "1AXT2"), at("STN2", "1AXT1", "1AXT2")))),
                "a track name repeated at a second location is legal once each side is scoped");
    }

    @Test
    void sameTrackNameRepeatedWithinOneLocationIsStillInconsistent() {
        BaselineInconsistentException ex = assertThrows(BaselineInconsistentException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "1AXT1"), at("STN2", "1AXT1")),
                        fct(at("STN1", "1AXT1"), at("STN2", "1AXT1")))));
        assertTrue(ex.getMessage().contains("STN1 / 1AXT1"),
                "the duplicate must be named with the location that holds it: " + ex.getMessage());
    }

    @Test
    void pdqLocationWithNoMatchingFctStationIsNotFound() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1"), at("STN3", "3CXT3")),
                        fct(at("STN1", "1AXT1"), at("STN2", "2BXT2")))));
        assertEquals(List.of("STN3 / 3CXT3"), ex.getNotFoundTracks());
        assertEquals(List.of("STN2 / 2BXT2"), ex.getExtraTracks());
        assertTrue(ex.getMessage().contains("STN3 / 3CXT3") && ex.getMessage().contains("STN2 / 2BXT2"),
                "message should carry both qualified entries: " + ex.getMessage());
    }

    @Test
    void trackAtOneLocationIsNotSatisfiedByTheSameNameAtAnother() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "5BXT5"), at("STN2", "1AXT1")),
                        fct(at("STN1", "1AXT1"), at("STN2", "1AXT1", "5BXT5")))));
        // The flat multiset would have cancelled these two against each other and passed.
        assertEquals(List.of("STN1 / 5BXT5"), ex.getNotFoundTracks());
        assertEquals(List.of("STN2 / 5BXT5"), ex.getExtraTracks());
    }

    @Test
    void fctStationTheControlTableNeverBannersIsAllExtra() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "1AXT2")),
                        fct(at("STN1", "1AXT1", "1AXT2"), at("STN2", "1AXT1", "1AXT2")))));
        assertTrue(ex.getNotFoundTracks().isEmpty());
        assertEquals(List.of("STN2 / 1AXT1", "STN2 / 1AXT2"), ex.getExtraTracks());
    }

    @Test
    void untaggedControlTableAgainstOneStationPassesAsBefore() {
        assertDoesNotThrow(() -> gate.check(input(
                pdq(at(null, "259BT", "262AT")),
                fct(at("STN1", "259BT", "262AT")))));
    }

    @Test
    void untaggedControlTableFailureKeepsTheUnqualifiedEntryShape() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at(null, "259BT", "999XX")),
                        fct(at("STN1", "259BT")))));
        assertEquals(List.of("999XX"), ex.getNotFoundTracks());
        assertTrue(ex.getExtraTracks().isEmpty());
        assertFalse(ex.getMessage().contains(" / "), "legacy entries stay unqualified: " + ex.getMessage());
    }

    @Test
    void locationMatchesStationTrimmedAndCaseInsensitively() {
        assertDoesNotThrow(() -> gate.check(input(
                pdq(at(" stn1 ", "1AXT1"), at("Stn2", "1AXT1")),
                fct(at("STN1", "1AXT1"), at(" sTn2 ", " 1AXT1 ")))));
    }

    @Test
    void qualifiedEntriesCarryTheUppercasedLocationCode() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("stn1", "1AXT1", "5BXT5")),
                        fct(at("Stn1", "1AXT1")))));
        assertEquals(List.of("STN1 / 5BXT5"), ex.getNotFoundTracks());
        assertTrue(ex.getExtraTracks().isEmpty());
    }

    @Test
    void trackNamesStayCaseSensitiveWithinALocation() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1axt1")),
                        fct(at("STN1", "1AXT1")))));
        assertEquals(List.of("STN1 / 1axt1"), ex.getNotFoundTracks());
        assertEquals(List.of("STN1 / 1AXT1"), ex.getExtraTracks());
    }

    /**
     * One location against a station-less FCT is still reconcilable — there is only one answer the FCT
     * could be giving — so it keeps the original flat comparison and qualifies nothing.
     */
    @Test
    void oneLocationAgainstAStationlessFctKeepsTheFlatPath() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "1AXT2")),
                        fct(at(null, "1AXT1")))));
        assertEquals(List.of("1AXT2"), ex.getNotFoundTracks());
        assertTrue(ex.getExtraTracks().isEmpty());
        assertFalse(ex.getMessage().contains(" / "), "the flat path qualifies nothing: " + ex.getMessage());
    }

    /**
     * Two locations against a station-less FCT is not reconcilable at all: nothing on the FCT side can say
     * which location an FMA belongs to, so the flat path would let one station's topology stand in for
     * both. Rejected outright rather than answered wrongly — the two artefacts arrive independently on the
     * v2 validate request, so this pairing is reachable even when each half is internally sound.
     */
    @Test
    void severalLocationsAgainstAStationlessFctIsRejected() {
        BaselineInconsistentException ex = assertThrows(BaselineInconsistentException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1"), at("STN2", "1AXT1")),
                        fct(at(null, "1AXT1", "1AXT1")))));
        assertTrue(ex.getMessage().contains("no Station"), ex.getMessage());
        assertTrue(ex.getMessage().contains("STN1") && ex.getMessage().contains("STN2"), ex.getMessage());
    }

    @Test
    void untaggedControlTableCannotDescribeATwoStationProject() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at(null, "1AXT1", "1AXT2")),
                        fct(at("STN1", "1AXT1", "1AXT2"), at("STN2", "1AXT1", "1AXT2")))));
        // One sheet-wide row per name can only consume one of the two stations' copies.
        assertTrue(ex.getNotFoundTracks().isEmpty());
        assertEquals(List.of("1AXT1", "1AXT2"), ex.getExtraTracks());
    }

    @Test
    void unscopedFctChainSatisfiesATaggedLocation() {
        assertDoesNotThrow(
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "9ZZ9")),
                        fct(at("STN1", "1AXT1"), at(null, "9ZZ9")))),
                "an FMA on a chain with no Station answers a scoped probe, exactly as it does in BaselineIndex");
    }

    @Test
    void unconsumedUnscopedFmaIsExtraUnqualifiedBesideQualifiedEntries() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "5BXT5")),
                        fct(at("STN1", "1AXT1", "2BXT2"), at(null, "9ZZ9")))));
        assertEquals(List.of("STN1 / 5BXT5"), ex.getNotFoundTracks());
        assertEquals(List.of("STN1 / 2BXT2", "9ZZ9"), ex.getExtraTracks());
        assertFalse(ex.getExtraTracks().get(1).contains(" / "),
                "an FMA with no Station of its own has no location to be qualified with");
    }

    /**
     * The shared pool is a multiset, not a lookup: one physical FMA cannot stand in for a track section
     * at two stations. Which location consumes it and which reports NOT-FOUND is {@code pdqByLocation}
     * iteration order, not contract, so this asserts the count rather than naming the loser.
     */
    @Test
    void sharedUnscopedPoolIsConsumedAtMostOnce() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at("STN1", "1AXT1", "9ZZ9"), at("STN2", "1AXT1", "9ZZ9")),
                        fct(at("STN1", "1AXT1"), at("STN2", "1AXT1"), at(null, "9ZZ9")))));
        assertEquals(1, ex.getNotFoundTracks().size(),
                "one unscoped 9ZZ9 can satisfy one location only: " + ex.getNotFoundTracks());
        assertTrue(List.of("STN1 / 9ZZ9", "STN2 / 9ZZ9").contains(ex.getNotFoundTracks().get(0)),
                "the loser is whichever location is visited second: " + ex.getNotFoundTracks());
        assertTrue(ex.getExtraTracks().isEmpty(), "the pool was consumed, so nothing is left over");
    }

    /**
     * A partially tagged Control Table — rows above the first LOCATION banner carry a null location —
     * puts a NO_SCOPE key on both sides. The unscoped FCT bucket must be the <i>same</i> pool for that
     * key as for every other location, not a second copy of it, or one physical FMA is consumable twice
     * and its leftovers reported twice. The Control table parser rejects such a sheet outright, but
     * {@code pdqData} also arrives straight off the v2 validate request body, where nothing has vetted it.
     */
    @Test
    void unscopedFmaIsReportedOnceAgainstAPartiallyTaggedControlTable() {
        TrackReconciliationFailedException ex = assertThrows(TrackReconciliationFailedException.class,
                () -> gate.check(input(
                        pdq(at(null, "AAA"), at("STN1", "1AXT1")),
                        fct(at("STN1", "1AXT1"), at(null, "9ZZ9")))));
        assertEquals(List.of("AAA"), ex.getNotFoundTracks());
        assertEquals(List.of("9ZZ9"), ex.getExtraTracks());
    }

    // --- builders ---

    private ValidationInputV2 input(PdqUploadResponse pdq, ComAebMap fct) {
        ValidationInputV2 in = new ValidationInputV2();
        in.setPdqData(pdq);
        in.setFctData(fct);
        return in;
    }

    /** A Control Table LOCATION banner or an FCT Station, and the track names sitting under it. */
    private record Scope(String code, List<String> tracks) {
    }

    /** {@code code} null means an untagged Control Table row / a chain with no Station ancestor. */
    private static Scope at(String code, String... tracks) {
        return new Scope(code, List.of(tracks));
    }

    /** One CAN segment per station, its single AEB evaluating that station's tracks. */
    private ComAebMap fct(Scope... stations) {
        List<Chain> chains = java.util.Arrays.stream(stations)
                .map(station -> {
                    List<EvaluatedFma> fmas = station.tracks().stream()
                            .map(n -> new EvaluatedFma(n, "0", "1"))
                            .toList();
                    FctAeb aeb = new FctAeb("1", "DP", fmas, List.of(), 0);
                    return new Chain(new FctCom("1", "COM", station.code()), false, List.of(aeb));
                })
                .toList();
        return new ComAebMap(chains);
    }

    private PdqUploadResponse pdq(Scope... locations) {
        List<TrackSection> sections = java.util.Arrays.stream(locations)
                .flatMap(loc -> loc.tracks().stream()
                        .map(n -> new TrackSection("1", n, List.of(), List.of(), "", "MAIN", null, false, loc.code())))
                .toList();
        return PdqUploadResponse.builder()
                .controlTable(new ControlTable(sections, List.of()))
                .cqIrParameters(Map.of("CFG_RSR_TYPE", Map.of("RSR_TYPE", "1")))
                .build();
    }
}
