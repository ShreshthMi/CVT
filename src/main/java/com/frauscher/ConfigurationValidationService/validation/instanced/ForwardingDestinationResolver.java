package com.frauscher.ConfigurationValidationService.validation.instanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.service.preprocessor.BaselineInconsistencies;

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
 * Both networks must resolve to the <b>same</b> present COM (VTF-360 D4: an NW2-mirror failure leaves
 * the member unresolved — NW1's answer is not accepted alone). A member that cannot be resolved records
 * its problem in the {@link BaselineInconsistencies} and is excluded; the evaluation-phase end-check
 * then rejects with the complete list (vtf-371-design.md §2): Phase 2 supplies no external IP baseline,
 * so every forwarding dest must be wired to a COM actually present in the set.
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
     * An unresolvable member records its problem into {@code problems} and is excluded from the result
     * (VTF-360 — the caller's end-check rejects with the complete list).
     */
    public List<ForwardMember> resolveActualForwards(
            ParsedConfigFile homeCom, List<ParsedConfigFile> allFiles, BaselineInconsistencies problems) {
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
            Integer socket = parseSocket(entryValue(block, INT_ID_DEST), homeCom, problems);
            if (socket == null) {
                continue; // recorded; this occurrence drops out of the actual-member set.
            }
            Integer destCom = resolveDestCom(homeCom, socket, comByIpNw1, comByIpNw2, problems);
            if (destCom == null) {
                continue;
            }
            members.add(new ForwardMember(canTxId, String.valueOf(destCom)));
        }
        return members;
    }

    private Integer resolveDestCom(
            ParsedConfigFile homeCom, int socket, Map<String, Integer> comByIpNw1,
            Map<String, Integer> comByIpNw2, BaselineInconsistencies problems) {

        String home = describe(homeCom);

        String destIpNw1 = destIp(homeCom, DEST_NW1, socket + NW1_OFFSET, DEST_IP_NW1_PREFIX);
        if (destIpNw1 == null) {
            problems.add(home + " forwards on socket " + socket + " but has no CFG_INT_ID_DEST_NW1 entry "
                    + (socket + NW1_OFFSET));
            return null;
        }
        Integer comNw1 = comByIpNw1.get(destIpNw1);
        if (comNw1 == null) {
            problems.add(home + " socket " + socket + " NW1 dest IP " + destIpNw1
                    + " matches no present COM file");
            return null;
        }

        // VTF-360 D4 (strict): NW2 is a mandatory mirror — an NW1-only resolution is NOT accepted.
        String destIpNw2 = destIp(homeCom, DEST_NW2, socket + NW2_OFFSET, DEST_IP_NW2_PREFIX);
        if (destIpNw2 == null) {
            problems.add(home + " forwards on socket " + socket + " but has no CFG_INT_ID_DEST_NW2 entry "
                    + (socket + NW2_OFFSET) + " (NW2 is mandatory)");
            return null;
        }
        Integer comNw2 = comByIpNw2.get(destIpNw2);
        if (comNw2 == null) {
            problems.add(home + " socket " + socket + " NW2 dest IP " + destIpNw2
                    + " matches no present COM file");
            return null;
        }

        if (!comNw1.equals(comNw2)) {
            problems.add(home + " socket " + socket + " resolves to COM " + comNw1 + " on NW1 but COM "
                    + comNw2 + " on NW2");
            return null;
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

    private Integer parseSocket(String value, ParsedConfigFile homeCom, BaselineInconsistencies problems) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException e) {
            problems.add(describe(homeCom) + " has a CFG_FWRD_ACD with a non-numeric INT_ID_DEST: " + value);
            return null;
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
