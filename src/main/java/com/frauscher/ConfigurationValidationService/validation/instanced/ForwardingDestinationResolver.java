package com.frauscher.ConfigurationValidationService.validation.instanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.exception.BaselineInconsistentException;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

/**
 * Resolves the destination COM of each actual {@code CFG_FWRD_ACD} forwarding entry of a home COM ADC
 * (v2-expectations-contract.md §5.6 — the BE-06-specific half of Check-B virtual forwarding). The FCT
 * only knows the expected {@code (source DP, dest COM)} set; the socket→COM mapping lives in the COM ADC
 * targets, so it is resolved here at validate time.
 *
 * <p>Each {@code CFG_FWRD_ACD} block carries {@code INT_ID_DEST} (socket {@code 0..15}) and
 * {@code CAN_TX_ID} (source DP). A block's "header" is the value of the entry whose key equals the block
 * name (the parser names a block from its first entry). To resolve a socket:</p>
 * <ul>
 *   <li><b>NW1:</b> the home COM's {@code CFG_INT_ID_DEST_NW1} block with header {@code socket + 32} →
 *       {@code DEST_IP_INT_ID_NW1_B1..B4} → the present COM whose {@code CFG_MY_IP_NW1} equals it.</li>
 *   <li><b>NW2 (mandatory mirror):</b> header {@code socket + 48} → {@code DEST_IP_INT_ID_NW2_B1..B4} →
 *       the present COM whose {@code CFG_MY_IP_NW2} equals it.</li>
 * </ul>
 * Both networks must resolve to the <b>same</b> present COM. A dest IP matching no present COM, a missing
 * NW2 dest/own-IP, or NW1/NW2 disagreeing is a malformed baseline → {@code PHASE2_BASELINE_INCONSISTENT}
 * (§3.3), surfaced as a hard {@link BaselineInconsistentException} (HTTP 400): Phase 2 supplies no
 * external IP baseline, so every forwarding dest must be wired to a COM actually present in the set.
 */
@Component
public class ForwardingDestinationResolver {

    static final String FORWARDING_BLOCK = "CFG_FWRD_ACD";
    static final String INT_ID_DEST = "INT_ID_DEST";
    static final String CAN_TX_ID = "CAN_TX_ID";

    private static final String MY_IP_NW1 = "CFG_MY_IP_NW1";
    private static final String MY_IP_NW2 = "CFG_MY_IP_NW2";
    private static final String DEST_NW1 = "CFG_INT_ID_DEST_NW1";
    private static final String DEST_NW2 = "CFG_INT_ID_DEST_NW2";
    private static final String DEST_IP_NW1_PREFIX = "DEST_IP_INT_ID_NW1_B";
    private static final String DEST_IP_NW2_PREFIX = "DEST_IP_INT_ID_NW2_B";
    private static final String MY_IP_NW1_PREFIX = "MY_IP_NW1_B";
    private static final String MY_IP_NW2_PREFIX = "MY_IP_NW2_B";

    private static final int NW1_OFFSET = 32;
    private static final int NW2_OFFSET = 48;

    /** A resolved actual forward: source DP {@code CAN_TX_ID} → destination COM id. */
    public record ForwardMember(String canTxId, String destCom) {
    }

    /**
     * Resolves every {@code CFG_FWRD_ACD} entry of {@code homeCom} to a {@code (CAN_TX_ID, destCom)}
     * member. {@code allFiles} supplies the present COMs whose self-IPs the dest IPs are matched against.
     *
     * @throws BaselineInconsistentException on any §3.3 network inconsistency.
     */
    public List<ForwardMember> resolveActualForwards(ParsedConfigFile homeCom, List<ParsedConfigFile> allFiles) {
        Map<String, Integer> comByIpNw1 = new HashMap<>();
        Map<String, Integer> comByIpNw2 = new HashMap<>();
        for (ParsedConfigFile file : allFiles) {
            if (!file.isComDetails()) {
                continue;
            }
            String ipNw1 = ownIp(file, MY_IP_NW1, MY_IP_NW1_PREFIX);
            if (ipNw1 != null) {
                comByIpNw1.put(ipNw1, file.getId());
            }
            String ipNw2 = ownIp(file, MY_IP_NW2, MY_IP_NW2_PREFIX);
            if (ipNw2 != null) {
                comByIpNw2.put(ipNw2, file.getId());
            }
        }

        List<ForwardMember> members = new ArrayList<>();
        for (ConfigBlock block : occurrences(homeCom, FORWARDING_BLOCK)) {
            String canTxId = entryValue(block, CAN_TX_ID);
            int socket = parseSocket(entryValue(block, INT_ID_DEST), homeCom);
            int destCom = resolveDestCom(homeCom, socket, comByIpNw1, comByIpNw2);
            members.add(new ForwardMember(canTxId, String.valueOf(destCom)));
        }
        return members;
    }

    private int resolveDestCom(
            ParsedConfigFile homeCom, int socket, Map<String, Integer> comByIpNw1, Map<String, Integer> comByIpNw2) {

        String home = describe(homeCom);

        String destIpNw1 = destIp(homeCom, DEST_NW1, socket + NW1_OFFSET, DEST_IP_NW1_PREFIX);
        if (destIpNw1 == null) {
            throw new BaselineInconsistentException(
                    home + " forwards on socket " + socket + " but has no CFG_INT_ID_DEST_NW1 entry "
                            + (socket + NW1_OFFSET));
        }
        Integer comNw1 = comByIpNw1.get(destIpNw1);
        if (comNw1 == null) {
            throw new BaselineInconsistentException(
                    home + " socket " + socket + " NW1 dest IP " + destIpNw1 + " matches no present COM file");
        }

        String destIpNw2 = destIp(homeCom, DEST_NW2, socket + NW2_OFFSET, DEST_IP_NW2_PREFIX);
        if (destIpNw2 == null) {
            throw new BaselineInconsistentException(
                    home + " forwards on socket " + socket + " but has no CFG_INT_ID_DEST_NW2 entry "
                            + (socket + NW2_OFFSET) + " (NW2 is mandatory)");
        }
        Integer comNw2 = comByIpNw2.get(destIpNw2);
        if (comNw2 == null) {
            throw new BaselineInconsistentException(
                    home + " socket " + socket + " NW2 dest IP " + destIpNw2 + " matches no present COM file");
        }

        if (!comNw1.equals(comNw2)) {
            throw new BaselineInconsistentException(
                    home + " socket " + socket + " resolves to COM " + comNw1 + " on NW1 but COM " + comNw2
                            + " on NW2");
        }
        return comNw1;
    }

    /** The dest IP of the {@code blockName} occurrence whose header (its name-keyed entry) equals {@code header}. */
    private String destIp(ParsedConfigFile com, String blockName, int header, String bytePrefix) {
        String headerStr = String.valueOf(header);
        for (ConfigBlock block : occurrences(com, blockName)) {
            if (headerStr.equals(trim(entryValue(block, blockName)))) {
                return ipBytes(block, bytePrefix);
            }
        }
        return null;
    }

    private String ownIp(ParsedConfigFile com, String blockName, String bytePrefix) {
        for (ConfigBlock block : occurrences(com, blockName)) {
            return ipBytes(block, bytePrefix);
        }
        return null;
    }

    private String ipBytes(ConfigBlock block, String bytePrefix) {
        StringBuilder ip = new StringBuilder();
        for (int b = 1; b <= 4; b++) {
            String octet = trim(entryValue(block, bytePrefix + b));
            if (octet == null || octet.isEmpty()) {
                return null;
            }
            if (b > 1) {
                ip.append('.');
            }
            ip.append(octet);
        }
        return ip.toString();
    }

    private int parseSocket(String value, ParsedConfigFile homeCom) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException | NullPointerException e) {
            throw new BaselineInconsistentException(
                    describe(homeCom) + " has a CFG_FWRD_ACD with a non-numeric INT_ID_DEST: " + value);
        }
    }

    private List<ConfigBlock> occurrences(ParsedConfigFile file, String blockName) {
        return file.getBlocks().stream()
                .filter(b -> blockName.equals(b.getName()))
                .sorted((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
    }

    private String entryValue(ConfigBlock block, String key) {
        return block.getEntries().stream()
                .filter(e -> key.equals(e.getKey()))
                .map(ConfigEntry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String describe(ParsedConfigFile com) {
        return "COM file '" + com.getFileName() + "' (id " + com.getId() + ")";
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
