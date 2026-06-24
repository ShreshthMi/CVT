package com.frauscher.ConfigurationValidationService.validation.instanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.service.preprocessor.InstancedExpectation;
import com.frauscher.ConfigurationValidationService.validation.instanced.ForwardingDestinationResolver.ForwardMember;

/**
 * CFG_FWRD_ACD validation (v2-expectations-contract.md §5.6): the {@link ForwardingDestinationResolver}
 * socket→COM/IP resolution + §3.3 network-consistency 400s, and the {@link InstancedExpectationEvaluator}
 * set-equality over the resolved actual forwards. COM ADCs are hand-built to the verified C0391 layout
 * (the header is the value of the entry keyed by the block name; socket+32 = NW1, socket+48 = NW2).
 */
class ForwardingValidationTest {

    private final ForwardingDestinationResolver resolver = new ForwardingDestinationResolver();
    private final InstancedExpectationEvaluator evaluator = new InstancedExpectationEvaluator(resolver);

    // ---------- resolver: socket → dest COM ----------

    @Test
    void resolvesSocketToDestComOnBothNetworks() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd(0, "361"));
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        List<ForwardMember> members = resolver.resolveActualForwards(home, List.of(home, dest));

        assertEquals(1, members.size());
        assertEquals("361", members.get(0).canTxId());
        assertEquals("41", members.get(0).destCom());
    }

    @Test
    void destIpMatchingNoPresentComThrows() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd(0, "361"));

        // dest COM 41 not in the uploaded set.
        assertThrows(BaselineInconsistentException.class,
                () -> resolver.resolveActualForwards(home, List.of(home)));
    }

    @Test
    void missingNw2DestThrows() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                fwrd(0, "361")); // no NW2 dest block
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        assertThrows(BaselineInconsistentException.class,
                () -> resolver.resolveActualForwards(home, List.of(home, dest)));
    }

    @Test
    void nw1AndNw2ResolvingToDifferentComsThrows() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.42"),
                fwrd(0, "361"));
        ParsedConfigFile dest41 = com(41, "10.1.106.41", "10.2.106.41");
        ParsedConfigFile dest42 = com(42, "10.1.106.42", "10.2.106.42");

        assertThrows(BaselineInconsistentException.class,
                () -> resolver.resolveActualForwards(home, List.of(home, dest41, dest42)));
    }

    @Test
    void nonNumericSocketThrows() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd("x", "361"));
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        assertThrows(BaselineInconsistentException.class,
                () -> resolver.resolveActualForwards(home, List.of(home, dest)));
    }

    // ---------- evaluator: set-equality over resolved forwards ----------

    @Test
    void forwardingAllExpectedPresentPasses() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd(0, "361"));
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        List<ValidationResult> results = evaluator.evaluate(List.of(home, dest), List.of(
                InstancedExpectation.byIdentity(391, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "361", "DEST_COM", "41"), "DEST_COM", "41")));

        assertEquals(1, results.size());
        assertEquals("PASS", results.get(0).getStatus());
    }

    @Test
    void forwardingMissingExpectedFails() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd(0, "361"));
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        List<ValidationResult> results = evaluator.evaluate(List.of(home, dest), List.of(
                InstancedExpectation.byIdentity(391, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "361", "DEST_COM", "41"), "DEST_COM", "41"),
                InstancedExpectation.byIdentity(391, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "999", "DEST_COM", "41"), "DEST_COM", "41")));

        assertTrue(results.stream().anyMatch(
                r -> "PASS".equals(r.getStatus()) && r.getEntryKey().contains("361")));
        assertTrue(results.stream().anyMatch(r -> "FAIL".equals(r.getStatus())
                && ValidationConstants.EXPECTED_OCCURRENCE_NOT_FOUND.equals(r.getActualValue())
                && r.getEntryKey().contains("999")));
    }

    @Test
    void forwardingExtraActualIsFlagged() {
        ParsedConfigFile home = homeCom(391, "10.1.106.53", "10.2.106.53",
                dest("CFG_INT_ID_DEST_NW1", "DEST_IP_INT_ID_NW1_B", 32, "10.1.106.41"),
                dest("CFG_INT_ID_DEST_NW2", "DEST_IP_INT_ID_NW2_B", 48, "10.2.106.41"),
                fwrd(0, "361"), fwrd(0, "370"));
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        List<ValidationResult> results = evaluator.evaluate(List.of(home, dest), List.of(
                InstancedExpectation.byIdentity(391, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "361", "DEST_COM", "41"), "DEST_COM", "41")));

        assertEquals(1, results.stream()
                .filter(r -> ValidationConstants.UNEXPECTED_OCCURRENCE.equals(r.getActualValue())).count());
        assertTrue(results.stream().anyMatch(r -> r.getEntryKey().contains("370")));
    }

    @Test
    void homeComNotUploadedIsSkipped() {
        ParsedConfigFile dest = com(41, "10.1.106.41", "10.2.106.41");

        List<ValidationResult> results = evaluator.evaluate(List.of(dest), List.of(
                InstancedExpectation.byIdentity(391, "CFG_FWRD_ACD",
                        Map.of("CAN_TX_ID", "361", "DEST_COM", "41"), "DEST_COM", "41")));

        assertTrue(results.isEmpty());
    }

    // ---------- COM ADC builders (verified C0391 layout) ----------

    private ParsedConfigFile com(int id, String ipNw1, String ipNw2, ConfigBlock... extra) {
        List<ConfigBlock> blocks = new ArrayList<>();
        blocks.add(ipBlock("CFG_MY_IP_NW1", "1", "MY_IP_NW1_B", ipNw1));
        blocks.add(ipBlock("CFG_MY_IP_NW2", "2", "MY_IP_NW2_B", ipNw2));
        for (ConfigBlock b : extra) {
            blocks.add(b);
        }
        return new ParsedConfigFile("C" + id, blocks, false, false, false, true, id);
    }

    private ParsedConfigFile homeCom(int id, String ipNw1, String ipNw2, ConfigBlock... extra) {
        return com(id, ipNw1, ipNw2, extra);
    }

    private ConfigBlock dest(String name, String bytePrefix, int header, String ip) {
        return ipBlock(name, String.valueOf(header), bytePrefix, ip);
    }

    private ConfigBlock ipBlock(String name, String headerValue, String bytePrefix, String ip) {
        String[] octets = ip.split("\\.");
        ConfigBlock block = new ConfigBlock();
        block.setName(name);
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(name, 8, headerValue, null)); // header = value of the name-keyed entry
        for (int i = 0; i < 4; i++) {
            entries.add(new ConfigEntry(bytePrefix + (i + 1), 8, octets[i], null));
        }
        block.setEntries(entries);
        return block;
    }

    private ConfigBlock fwrd(int socket, String canTxId) {
        return fwrd(String.valueOf(socket), canTxId);
    }

    private ConfigBlock fwrd(String socket, String canTxId) {
        ConfigBlock block = new ConfigBlock();
        block.setName("CFG_FWRD_ACD");
        block.setEntries(new ArrayList<>(List.of(
                new ConfigEntry("CFG_FWRD_ACD", 8, "9", null),
                new ConfigEntry("INT_ID_DEST", 4, socket, null),
                new ConfigEntry("CAN_TX_ID", 12, canTxId, null))));
        return block;
    }
}
