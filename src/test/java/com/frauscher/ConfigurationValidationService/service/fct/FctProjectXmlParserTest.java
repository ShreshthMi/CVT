package com.frauscher.ConfigurationValidationService.service.fct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidException;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidReason;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;

/**
 * Parses each FCT fixture's {@code Project.xml} into a ComAebMap: the ACO case (segments, AEBs,
 * evaluated FMAs, OutputFma cross-resolution), redundancy collapse for both COM-pair vocabularies
 * (MASTER/SLAVE and PRIMARY/SECONDARY — the latter via in-memory ComMode substitution on the same
 * fixture), mixed-pair rejection, DT-IoExb counting, and the duplicate-AEB-Id rejection.
 */
class FctProjectXmlParserTest {

    private final FctProjectXmlParser parser = new FctProjectXmlParser();

    @Test
    void parsesAcoFixture() throws Exception {
        ComAebMap map = parse(FctFixtures.openAcoProjectXml());

        assertEquals(2, map.chains().size());

        Chain com100 = chainByComName(map, "COM100");
        assertEquals("100", com100.com().comId());
        assertFalse(com100.redundantComPresent());
        assertEquals(5, com100.aebs().size()); // DP2A, DP3A, DP4, DP5, DP1A

        FctAeb dp1a = aebByDpName(com100, "DP1A");
        assertEquals("5", dp1a.dpId());
        assertEquals(2, dp1a.evaluatedFmas().size()); // 1AXT1, SUP1-AXT1
        assertEquals(0, dp1a.dtIoExbCount());
        assertEquals(2, dp1a.acoIoExbs().size());

        // First ACO: single-track, OutputFma1 = 1AXT1 (fmaId 0) on DP1A (dpId 5); no OutputFma2.
        AcoIoExb first = dp1a.acoIoExbs().get(0);
        assertEquals("ACO", first.label());
        assertEquals("1AXT1", first.outputFma1Name());
        assertEquals("0", first.outputFma1Id());
        assertEquals("5", first.outputFma1DpId());
        assertNull(first.outputFma2Name());

        // Second ACO: dual-track, outputs reference FMAs on *other* AEBs (DP3A=2, DP2A=1).
        AcoIoExb second = dp1a.acoIoExbs().get(1);
        assertEquals("201AXT", second.outputFma1Name());
        assertEquals("2", second.outputFma1DpId());
        assertEquals("2AXT1", second.outputFma2Name());
        assertEquals("1", second.outputFma2DpId());

        assertEquals(3, chainByComName(map, "COM200").aebs().size()); // DP2B, DP3B, DP1B
    }

    @Test
    void collapsesMasterSlaveRedundancy() throws Exception {
        ComAebMap map = parse(FctFixtures.openRedundantProjectXml());
        dump(map, "build/fct-redundant.json");
        // The surviving chain COM must be the MASTER ("COM-AdC(M)"), not the SLAVE ("COM-AdC(R)").
        assertEquals("COM-AdC(M)", redundantChain(map, "MASTER/SLAVE").com().comName());
    }

    @Test
    void collapsesPrimarySecondaryRedundancy() throws Exception {
        // Same fixture, alternative vocabulary: baseline FCTs also label redundant pairs PRIMARY/SECONDARY.
        ComAebMap map = parse(withComModes("PRIMARY", "SECONDARY"));
        // The surviving chain COM must be the PRIMARY (the fixture's former MASTER), not the SECONDARY.
        assertEquals("COM-AdC(M)", redundantChain(map, "PRIMARY/SECONDARY").com().comName());
    }

    private static Chain redundantChain(ComAebMap map, String pairLabel) {
        return map.chains().stream().filter(Chain::redundantComPresent).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected at least one chain collapsed from a " + pairLabel + " pair"));
    }

    @Test
    void rejectsMixedRedundancyPair() {
        // MASTER + SECONDARY is not a pair in either vocabulary.
        FctInvalidException ex = assertThrows(FctInvalidException.class,
                () -> parse(withComModes("MASTER", "SECONDARY")));
        assertEquals(FctInvalidReason.MULTI_COM_NO_REDUNDANCY, ex.getReason());
    }

    @Test
    void countsDtIoExbs() throws Exception {
        ComAebMap map = parse(FctFixtures.openDtProjectXml());
        dump(map, "build/fct-dt.json");
        boolean anyDt = map.chains().stream()
                .flatMap(c -> c.aebs().stream())
                .anyMatch(a -> a.dtIoExbCount() > 0);
        assertTrue(anyDt, "expected at least one AEB with DT-mode IoExbs");
    }

    @Test
    void rejectsDuplicateAebId() {
        FctInvalidException ex = assertThrows(FctInvalidException.class,
                () -> parse(FctFixtures.openDuplicateIdProjectXml()));
        assertEquals(FctInvalidReason.DUPLICATE_ENTITY_ID, ex.getReason());
    }

    private ComAebMap parse(InputStream in) throws Exception {
        try (in) {
            return parser.parse(in);
        }
    }

    /** The redundant fixture's project.xml with its MASTER/SLAVE ComModes substituted. */
    private static InputStream withComModes(String masterAs, String slaveAs) throws Exception {
        try (InputStream in = FctFixtures.openRedundantProjectXml()) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("<ComMode>MASTER</ComMode>", "<ComMode>" + masterAs + "</ComMode>")
                    .replace("<ComMode>SLAVE</ComMode>", "<ComMode>" + slaveAs + "</ComMode>");
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void dump(ComAebMap map, String path) throws Exception {
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(new File(path), map);
    }

    private static Chain chainByComName(ComAebMap map, String comName) {
        return map.chains().stream().filter(c -> comName.equals(c.com().comName())).findFirst().orElseThrow();
    }

    private static FctAeb aebByDpName(Chain chain, String dpName) {
        return chain.aebs().stream().filter(a -> dpName.equals(a.dpName())).findFirst().orElseThrow();
    }
}