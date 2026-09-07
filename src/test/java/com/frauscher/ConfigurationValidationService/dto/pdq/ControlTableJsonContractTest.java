package com.frauscher.ConfigurationValidationService.dto.pdq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The wire contract for the Control table rows. {@code controlTable} is returned by
 * {@code POST /api/upload/pdq} and handed straight back as the {@code pdqData} block of the v2 validate
 * request, so it is both serialized and deserialized — the {@code location} field has to be additive in
 * one direction and reconstructible in the other.
 */
class ControlTableJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** An untagged workbook's response must be byte-for-byte what it was before locations existed. */
    @Test
    void omitsLocationEntirelyWhenTheRowCarriesNone() throws Exception {
        String json = mapper.writeValueAsString(
                new TrackSection("1", "1AXT1", List.of("DP1A"), List.of("DP2A"), "RESTRICTED RESET WITH LV",
                        "MAIN", null, false, null));

        assertFalse(json.contains("location"), "untagged rows must not gain a location key: " + json);
        assertTrue(json.contains("\"fadcAutoReset\":null"),
                "other null fields keep serializing as before — the include rule is per-field, not per-type");
    }

    @Test
    void omitsLocationOnAnUntaggedDpTableRow() throws Exception {
        String json = mapper.writeValueAsString(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, null));

        assertFalse(json.contains("location"), json);
    }

    @Test
    void carriesLocationWhenTheRowIsTagged() throws Exception {
        String track = mapper.writeValueAsString(
                new TrackSection("1", "1AXT1", List.of(), List.of(), "", "MAIN", null, false, "STN1"));
        String dp = mapper.writeValueAsString(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1"));

        assertTrue(track.contains("\"location\":\"STN1\""), track);
        assertTrue(dp.contains("\"location\":\"STN1\""), dp);
    }

    /**
     * The v2 validate request carries the upload response back, so the records must still round-trip.
     * Adding a component to a record is only safe while Jackson can still resolve its canonical
     * constructor.
     */
    @Test
    void roundTripsATaggedControlTable() throws Exception {
        ControlTable original = new ControlTable(
                List.of(new TrackSection("1", "1AXT1", List.of("DP1A"), List.of("DP2A"), "RESET",
                        "MAIN", new FadcAutoReset("OR", List.of("2AXT1")), false, "STN1")),
                List.of(new DpTableRow("1", "DP1A", "ABOVE THE RAIL", true, "STN1")));

        ControlTable back = mapper.readValue(mapper.writeValueAsString(original), ControlTable.class);

        assertEquals(original, back);
        assertEquals("STN1", back.trackSections().get(0).location());
        assertEquals("STN1", back.dpTable().get(0).location());
    }

    /** A payload written before this change has no location key at all; it must still deserialize. */
    @Test
    void deserialisesALegacyPayloadWithNoLocationKey() throws Exception {
        String legacy = """
                {"trackSections":[{"serialNo":"1","name":"1AXT1","dpIn":["DP1A"],"dpOut":["DP2A"],\
                "resetType":"RESET","trackType":"MAIN","fadcAutoReset":null,"autoResetByTimer":false}],\
                "dpTable":[{"serialNo":"1","name":"DP1A","position":"ABOVE THE RAIL","eChc":true}]}""";

        ControlTable parsed = mapper.readValue(legacy, ControlTable.class);

        assertEquals("1AXT1", parsed.trackSections().get(0).name());
        assertNull(parsed.trackSections().get(0).location());
        assertNull(parsed.dpTable().get(0).location());
    }
}
