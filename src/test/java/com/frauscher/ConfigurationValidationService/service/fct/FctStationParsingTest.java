package com.frauscher.ConfigurationValidationService.service.fct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;

/**
 * The FCT half of the location key. Project.xml nests every Com/Aeb/Fma under a {@code <Station>}, but
 * the parser used to read the document flat and drop it — leaving nothing to tell two stations'
 * identically-named tracks apart.
 */
class FctStationParsingTest {

    private final FctProjectXmlParser parser = new FctProjectXmlParser();

    @Test
    void carriesTheStationOnEachChainsCom() throws Exception {
        ComAebMap fct = parseTwoStation();

        assertEquals(4, fct.chains().size(), "2 CAN segments per station");
        assertEquals(List.of("STN1", "STN1", "STN2", "STN2"),
                fct.chains().stream().map(c -> c.com().station()).toList());
    }

    /** The single-station baseline still resolves a station — it just happens to be the only one. */
    @Test
    void carriesTheStationForASingleStationProject() throws Exception {
        ComAebMap fct;
        try (InputStream in = FctFixtures.openAcoProjectXml()) {
            fct = parser.parse(in);
        }

        assertEquals(2, fct.chains().size());
        assertTrue(fct.chains().stream().allMatch(c -> "Station-1".equals(c.com().station())));
    }

    /**
     * The names really do collide — which is the whole reason the station has to be carried. Every DP
     * name and FMA name appears under both stations; only the ids and the station differ.
     */
    @Test
    void bothStationsRepeatTheSameDpAndFmaNames() throws Exception {
        ComAebMap fct = parseTwoStation();

        Set<String> dpNames1 = dpNames(fct, "STN1");
        Set<String> dpNames2 = dpNames(fct, "STN2");
        assertEquals(dpNames1, dpNames2);
        assertTrue(dpNames1.contains("DP1A"));

        assertEquals(fmaNames(fct, "STN1"), fmaNames(fct, "STN2"));
        assertTrue(fmaNames(fct, "STN1").contains("1AXT1"));
    }

    /** Ids stay unique across stations, so the DP id remains a safe key where the name is not. */
    @Test
    void keepsDpIdsUniqueAcrossStations() throws Exception {
        ComAebMap fct = parseTwoStation();

        List<String> allDpIds = fct.chains().stream()
                .flatMap(c -> c.aebs().stream())
                .map(FctAeb::dpId)
                .toList();

        assertEquals(allDpIds.size(), Set.copyOf(allDpIds).size(), "no AEB id repeats across stations");
        assertEquals(16, allDpIds.size());
    }

    private ComAebMap parseTwoStation() throws Exception {
        try (InputStream in = FctFixtures.openTwoStationProjectXml()) {
            return parser.parse(in);
        }
    }

    private Set<String> dpNames(ComAebMap fct, String station) {
        return chainsOf(fct, station).stream()
                .flatMap(c -> c.aebs().stream())
                .map(FctAeb::dpName)
                .collect(Collectors.toSet());
    }

    private Set<String> fmaNames(ComAebMap fct, String station) {
        return chainsOf(fct, station).stream()
                .flatMap(c -> c.aebs().stream())
                .flatMap(a -> a.evaluatedFmas().stream())
                .map(EvaluatedFma::fmaName)
                .collect(Collectors.toSet());
    }

    private List<Chain> chainsOf(ComAebMap fct, String station) {
        return fct.chains().stream().filter(c -> station.equals(c.com().station())).toList();
    }
}
