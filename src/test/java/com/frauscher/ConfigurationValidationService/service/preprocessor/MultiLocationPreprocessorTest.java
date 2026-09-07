package com.frauscher.ConfigurationValidationService.service.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.ValidationInputV2;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.pdq.PdqUploadResponse;
import com.frauscher.ConfigurationValidationService.dto.pdq.TrackSection;
import com.frauscher.ConfigurationValidationService.service.fct.FctProjectXmlParser;
import com.frauscher.ConfigurationValidationService.service.pdq.ControlTableParser;
import com.frauscher.ConfigurationValidationService.service.pdq.CqIrSheetParser;
import com.frauscher.ConfigurationValidationService.service.pdq.CqIrValueNormalizer;
import com.frauscher.ConfigurationValidationService.service.pdq.DataTransmissionParser;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqMappingService;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqParsingService;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqSheetHeaderParser;
import com.frauscher.ConfigurationValidationService.service.pdq.PdqWorkbookContract;
import com.frauscher.ConfigurationValidationService.service.pdq.ProjectBlockResolver;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;
import com.frauscher.ConfigurationValidationService.testsupport.PdqFixtures;

/**
 * End-to-end over the real artefacts: a two-location PDQ workbook against a two-station FCT, where both
 * locations carry the same track and DP names throughout. This is the case the tool used to reject
 * outright at the gate, and — had the gate simply been relaxed — would have gone on to validate each
 * location against the other one's topology without raising anything.
 */
class MultiLocationPreprocessorTest {

    private static final Set<String> LINKED_BY_DP = Set.of(
            "CFG_ZP_FMA1", "CFG_ZP_FMA2", "CFG_CONTROL", "CFG_SUPERVIS_FMA1", "CFG_SUPERVIS_FMA2");

    private final DefaultExpectationsPreprocessor preprocessor = new DefaultExpectationsPreprocessor(
            new BaselineGate(),
            new ScalarExpectationsBuilder(),
            new CountingHeadExpectationsBuilder(),
            new SupervisorExpectationsBuilder(),
            new AcoExpectationsBuilder(),
            new ControlExpectationsBuilder(new ResetOutMappingService()),
            new IpSwitchExpectationsBuilder(),
            new ForwardingExpectationsBuilder(),
            new DataTransmissionExpectationsBuilder());

    /** The headline: identical names at two locations reconcile instead of being rejected as duplicates. */
    @Test
    void preprocessesAProjectWhoseTwoLocationsShareEveryTrackName() throws Exception {
        ValidationInputV2 input = twoLocationInput();

        List<TrackSection> tracks = input.getPdqData().getControlTable().trackSections();
        assertEquals(14, tracks.size());
        assertEquals(2, tracks.stream().filter(t -> "1AXT1".equals(t.name())).count(),
                "the same track name really does appear at both locations");

        assertDoesNotThrow(() -> preprocessor.preprocess(input));
    }

    /**
     * The defect a relaxed gate alone would have left behind. Every DP-linked expectation must join a
     * file to a linked DP <b>within one station</b>; a single cross-station pair would mean a location
     * was validated against the other one's counting heads, silently and with no error raised.
     */
    @Test
    void neverLinksAFileToADpAtTheOtherStation() throws Exception {
        ValidationInputV2 input = twoLocationInput();
        Map<Integer, String> stationByDpId = stationByDpId(input.getFctData());

        Expectations expectations = preprocessor.preprocess(input);

        List<InstancedExpectation> linked = expectations.instancedExpectations().stream()
                .filter(e -> LINKED_BY_DP.contains(e.block()))
                .filter(e -> e.linkedId() != null && e.linkedId().containsKey("ID"))
                .toList();
        assertFalse(linked.isEmpty(), "the fixture should produce DP-linked expectations");

        for (InstancedExpectation e : linked) {
            String fileStation = stationByDpId.get(e.fileId());
            String linkedStation = stationByDpId.get(Integer.valueOf(e.linkedId().get("ID")));
            assertEquals(fileStation, linkedStation,
                    "expectation " + e.block() + " on DP " + e.fileId()
                            + " links DP " + e.linkedId().get("ID") + " at another station");
        }
    }

    /** Both locations must actually be derived — not one of them silently dropped by the row walk. */
    @Test
    void derivesExpectationsForBothLocations() throws Exception {
        ValidationInputV2 input = twoLocationInput();
        Map<Integer, String> stationByDpId = stationByDpId(input.getFctData());

        Expectations expectations = preprocessor.preprocess(input);

        Set<String> stationsCovered = new LinkedHashSet<>();
        for (InstancedExpectation e : expectations.instancedExpectations()) {
            String station = stationByDpId.get(e.fileId());
            if (station != null) {
                stationsCovered.add(station);
            }
        }
        assertEquals(Set.of("STN1", "STN2"), stationsCovered);
    }

    /**
     * A track named at both locations is evaluated by a different AEB at each, so its counting-head
     * expectations land on two disjoint sets of files rather than being collapsed onto one.
     */
    @Test
    void emitsCountingHeadsSeparatelyForEachLocationsCopyOfATrack() throws Exception {
        ValidationInputV2 input = twoLocationInput();
        Map<Integer, String> stationByDpId = stationByDpId(input.getFctData());

        Expectations expectations = preprocessor.preprocess(input);

        Set<Integer> stn1Files = new LinkedHashSet<>();
        Set<Integer> stn2Files = new LinkedHashSet<>();
        for (InstancedExpectation e : expectations.instancedExpectations()) {
            if (!e.block().startsWith("CFG_ZP_FMA")) {
                continue;
            }
            ("STN1".equals(stationByDpId.get(e.fileId())) ? stn1Files : stn2Files).add(e.fileId());
        }

        assertFalse(stn1Files.isEmpty());
        assertFalse(stn2Files.isEmpty());
        assertTrue(java.util.Collections.disjoint(stn1Files, stn2Files));
        assertEquals(stn1Files.size(), stn2Files.size(),
                "the two locations are structural copies, so they derive the same shape");
    }

    // --- fixtures ---

    private ValidationInputV2 twoLocationInput() throws Exception {
        ValidationInputV2 input = new ValidationInputV2();
        input.setPdqData(PdqFixtures.withConsistentResetTypes(parsePdq()));
        input.setFctData(parseFct());
        return input;
    }

    private PdqUploadResponse parsePdq() throws Exception {
        PdqMappingService mapping = new PdqMappingService();
        PdqWorkbookContract contract = new PdqWorkbookContract();
        PdqParsingService service = new PdqParsingService(
                contract,
                new PdqSheetHeaderParser(contract),
                new CqIrSheetParser(new CqIrValueNormalizer(mapping), mapping, contract),
                new ControlTableParser(contract),
                new DataTransmissionParser(contract),
                new ProjectBlockResolver(contract));
        try (InputStream in = PdqFixtures.class.getResourceAsStream(PdqFixtures.TWO_LOCATION_WORKBOOK)) {
            return service.parse(in);
        }
    }

    private ComAebMap parseFct() throws Exception {
        try (InputStream in = FctFixtures.openTwoStationProjectXml()) {
            return new FctProjectXmlParser().parse(in);
        }
    }

    /** DP id → the station whose CAN segment carries it. Ids stay unique across stations; names do not. */
    private Map<Integer, String> stationByDpId(ComAebMap fct) {
        Map<Integer, String> byId = new HashMap<>();
        fct.chains().forEach(chain -> chain.aebs().forEach(
                aeb -> byId.put(Integer.valueOf(aeb.dpId()), chain.com().station())));
        return byId;
    }
}
