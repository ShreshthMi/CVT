package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.dto.pdq.ControlTable;
import com.frauscher.ConfigurationValidationService.dto.pdq.DpTableRow;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;

/**
 * The location-scoped half of the shared builder index (see {@link BaselineIndex}'s "Location
 * scoping"): names repeat verbatim across the stations of one project, so every name index is
 * name → scope → value and one rule resolves the probe — a scoped hit wins, an entry carrying no
 * scope answers any probe, a DP name unique across the stations still resolves from a neighbouring
 * location (block sections legitimately reach one) while an ambiguous one is recorded rather than
 * guessed, and track/FMA references are strict because a track belongs to its own location.
 */
class BaselineIndexLocationTest {

    @Test
    void repeatedDpNameResolvesPerLocation() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(twoStationFct(), twoStationControlTable(), problems);

        assertTrue(index.isLocationScoped());
        assertEquals("STN1", index.stationOfChain(0));
        assertEquals("STN2", index.stationOfChain(1));

        assertEquals(5, index.idOfDp("STN1", "DP1A"));
        assertEquals(6, index.idOfDp("STN2", "DP1A"));
        assertEquals(0, index.chainOfDpId(index.idOfDp("STN1", "DP1A")));
        assertEquals(1, index.chainOfDpId(index.idOfDp("STN2", "DP1A")));
        assertEquals("ABOVE THE RAIL", index.positionOfDp("STN1", "DP1A"));
        assertEquals("BELOW THE RAIL", index.positionOfDp("STN2", "DP1A"));
        assertEquals(Boolean.TRUE, index.eChcOfDp("STN1", "DP1A"));
        assertEquals(Boolean.FALSE, index.eChcOfDp("STN2", "DP1A"));
        assertTrue(problems.isEmpty()); // an exact scope hit is never a fallback
    }

    @Test
    void repeatedDpNameProbedWithNoLocationIsRecordedNamingBothStations() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(twoStationFct(), twoStationControlTable(), problems);

        assertNull(index.idOfDp(null, "DP1A"));
        assertEquals(List.of("DP 'DP1A' is ambiguous — it exists at [STN1, STN2]"
                + " and the reference names no location that picks one"), problems.items());

        assertNull(index.idOfDp(null, " DP1A ")); // same root cause, trim-normalized → dedups
        assertEquals(1, problems.items().size());
    }

    @Test
    void dpNameUniqueToOneStationResolvesFromAnother() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(twoStationFct(), twoStationControlTable(), problems);

        // DP8B exists only at STN2; a STN1 block section reaching it is legitimate, not a defect.
        assertEquals(8, index.idOfDp("STN1", "DP8B"));
        assertEquals(1, index.chainOfDpId(index.idOfDp("STN1", "DP8B")));
        assertEquals("BELOW THE RAIL", index.positionOfDp("STN1", "DP8B"));
        assertEquals(Boolean.FALSE, index.eChcOfDp("STN1", "DP8B"));
        assertTrue(problems.isEmpty());
    }

    @Test
    void trackAndFmaLookupsDoNotFallBackAcrossLocations() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(twoStationFct(), twoStationControlTable(), problems);

        // 2BXT1 belongs to STN2 by definition; the STN1 probe is a miss the caller reports itself.
        assertNull(index.trackByName("STN1", "2BXT1"));
        assertNull(index.fmaByName("STN1", "2BXT1"));
        assertTrue(problems.isEmpty());

        assertEquals("3", index.trackByName("STN2", "2BXT1").serialNo());
        assertEquals("81", index.fmaByName("STN2", "2BXT1").fmaId());
        assertEquals("1", index.trackByName("STN1", "1AXT1").serialNo());
        assertEquals("2", index.trackByName("STN2", "1AXT1").serialNo());
        assertEquals("51", index.fmaByName("STN1", "1AXT1").fmaId());
        assertEquals("61", index.fmaByName("STN2", "1AXT1").fmaId());
    }

    @Test
    void locationCodesMatchTrimmedAndCaseInsensitively() {
        // Short free text typed into two artefacts by two teams — padded on the FCT side, differently
        // cased on the PDQ side, and probed in a third spelling.
        ComAebMap fct = new ComAebMap(List.of(
                chain("100", " stn1 ", aeb("5", "DP1A", "1AXT1", "51")),
                chain("200", "STN2", aeb("6", "DP1A", "1AXT1", "61"))));
        ControlTable ct = new ControlTable(
                List.of(track("1", "1AXT1", "Stn1"), track("2", "1AXT1", " stn2 ")),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "Stn1"),
                        new DpTableRow("2", "DP1A", "BELOW THE RAIL", false, " stn2 ")));

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, ct, problems);

        assertEquals(5, index.idOfDp("stn1", "DP1A"));
        assertEquals(5, index.idOfDp(" STN1 ", "DP1A"));
        assertEquals(6, index.idOfDp("stn2", "DP1A"));
        assertEquals("1", index.trackByName("STN1", "1AXT1").serialNo());
        assertEquals("2", index.trackByName(" sTn2 ", "1AXT1").serialNo());
        assertEquals("ABOVE THE RAIL", index.positionOfDp("STN1", "DP1A"));
        assertEquals("BELOW THE RAIL", index.positionOfDp("stn2", "DP1A"));
        assertTrue(problems.isEmpty()); // every probe landed on its own scope, so nothing fell back
    }

    @Test
    void fctWithoutAStationAnswersALocationNamedProbe() {
        // A tagged Control table against a Project.xml that carries no Station at all.
        ComAebMap fct = new ComAebMap(List.of(chain("100", null, aeb("5", "DP1A", "1AXT1", "51"))));
        ControlTable ct = new ControlTable(
                List.of(track("1", "1AXT1", "STN1")),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1")));

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, ct, problems);

        assertTrue(index.isLocationScoped());
        assertNull(index.stationOfChain(0));
        assertEquals(5, index.idOfDp("STN1", "DP1A"));
        assertEquals(5, index.idOfDp("STN2", "DP1A"));
        assertEquals("51", index.fmaByName("STN2", "1AXT1").fmaId()); // unscoped beats FMA strictness
        assertEquals("ABOVE THE RAIL", index.positionOfDp("STN1", "DP1A"));
        assertEquals(Boolean.TRUE, index.eChcOfDp("STN1", "DP1A"));
        assertTrue(problems.isEmpty());
    }

    @Test
    void untaggedSingleStationBaselineResolvesEveryLookupByBareName() {
        ComAebMap fct = new ComAebMap(List.of(chain("100", null, aeb("5", "DP1A", "1AXT1", "51"))));
        ControlTable ct = new ControlTable(
                List.of(track("1", "1AXT1", null)),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, null)));

        BaselineInconsistencies problems = new BaselineInconsistencies();
        BaselineIndex index = BaselineIndex.build(fct, ct, problems);

        assertFalse(index.isLocationScoped());
        assertNull(index.stationOfChain(0));
        assertEquals(5, index.idOfDp(null, "DP1A"));
        assertEquals(0, index.chainOfDpId(5));
        assertEquals("ABOVE THE RAIL", index.positionOfDp(null, "DP1A"));
        assertEquals(Boolean.TRUE, index.eChcOfDp(null, "DP1A"));
        assertEquals("1AXT1", index.trackByName(null, "1AXT1").name());
        assertEquals("1", index.trackByName(null, "1AXT1").serialNo());
        assertEquals("51", index.fmaByName(null, "1AXT1").fmaId());
        assertTrue(problems.isEmpty());
    }

    @Test
    void locationScopedFollowsTheControlTableBannersAlone() {
        ComAebMap fct = new ComAebMap(List.of(chain("100", "STN1", aeb("5", "DP1A", "1AXT1", "51"))));
        BaselineInconsistencies problems = new BaselineInconsistencies();

        ControlTable untagged = new ControlTable(
                List.of(track("1", "1AXT1", null)),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, null)));
        assertFalse(BaselineIndex.build(fct, untagged, problems).isLocationScoped());
        assertFalse(BaselineIndex.build(fct, null, problems).isLocationScoped()); // no banners to read

        ControlTable oneTaggedDpRow = new ControlTable(
                List.of(track("1", "1AXT1", null)),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, null),
                        new DpTableRow("2", "DP1B", "BELOW THE RAIL", false, "STN2")));
        assertTrue(BaselineIndex.build(fct, oneTaggedDpRow, problems).isLocationScoped());

        ControlTable oneTaggedTrack = new ControlTable(List.of(track("1", "1AXT1", "STN1")), List.of());
        assertTrue(BaselineIndex.build(fct, oneTaggedTrack, problems).isLocationScoped());
        assertTrue(problems.isEmpty());
    }

    /**
     * Two stations whose DP and FMA names repeat verbatim ({@code DP1A}, {@code 1AXT1}) plus one name
     * carried by STN2 alone ({@code DP8B} / {@code 2BXT1}) — the shape the scoping exists for.
     */
    private static ComAebMap twoStationFct() {
        return new ComAebMap(List.of(
                chain("100", "STN1", aeb("5", "DP1A", "1AXT1", "51"), aeb("7", "DP7A", "1AXT2", "71")),
                chain("200", "STN2", aeb("6", "DP1A", "1AXT1", "61"), aeb("8", "DP8B", "2BXT1", "81"))));
    }

    /** The PDQ half of {@link #twoStationFct()}, tagged with the matching {@code LOCATION} banners. */
    /**
     * Two DP-table rows for one DP at one location: the second silently replaced the first, taking its
     * POSITION and E-CHC with it — and DIR_INV is derived from POSITION. The gate has always done this for
     * track names; nothing was doing it for the DP table.
     */
    @Test
    void repeatedDpTableRowAtOneLocationIsRecorded() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        ControlTable withRepeat = new ControlTable(
                twoStationControlTable().trackSections(),
                List.of(
                        new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1"),
                        new DpTableRow("2", "DP1A", "BELOW THE RAIL", false, "STN1")));

        BaselineIndex.build(twoStationFct(), withRepeat, problems);

        assertEquals(List.of("DP table has more than one row for DP 'DP1A' at STN1"), problems.items());
    }

    /** The same name at two DIFFERENT locations is the normal case and stays silent. */
    @Test
    void sameDpNameAtTwoLocationsIsNotARepeat() {
        BaselineInconsistencies problems = new BaselineInconsistencies();
        ControlTable acrossLocations = new ControlTable(
                twoStationControlTable().trackSections(),
                List.of(
                        new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1"),
                        new DpTableRow("1", "DP1A", "BELOW THE RAIL", false, "STN2")));

        BaselineIndex.build(twoStationFct(), acrossLocations, problems);

        assertTrue(problems.items().isEmpty(), problems.items().toString());
    }

    private static ControlTable twoStationControlTable() {
        return new ControlTable(
                List.of(track("1", "1AXT1", "STN1"), track("2", "1AXT1", "STN2"), track("3", "2BXT1", "STN2")),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1"),
                        new DpTableRow("2", "DP1A", "BELOW THE RAIL", false, "STN2"),
                        new DpTableRow("3", "DP8B", "BELOW THE RAIL", false, "STN2")));
    }

    private static Chain chain(String comId, String station, FctAeb... aebs) {
        return new Chain(new FctCom(comId, "COM" + comId, station), false, List.of(aebs));
    }

    /** One AEB evaluating a single FMA; {@code fmaId} tells the two stations' copies apart. */
    private static FctAeb aeb(String dpId, String dpName, String fmaName, String fmaId) {
        return new FctAeb(dpId, dpName, List.of(new EvaluatedFma(fmaName, fmaId, dpId)), List.of(), 0);
    }

    private static TrackSection track(String serialNo, String name, String location) {
        return new TrackSection(serialNo, name, List.of(), List.of(), null, "MAIN", null, false, location);
    }
}
