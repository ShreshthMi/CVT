package com.frauscher.ConfigurationValidationService.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.frauscher.ConfigurationValidationService.exception.FileParsingException;
import com.frauscher.ConfigurationValidationService.model.ConfigBlock;
import com.frauscher.ConfigurationValidationService.model.ConfigEntry;
import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

public final class CfgParserUtil {

    private CfgParserUtil() {
    }


    public static ParsedConfigFile parse(MultipartFile file) {

        List<ConfigBlock> blocks = new ArrayList<>();
        ConfigBlock currentBlock = null;
        int sequence = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]")) {

                    currentBlock = new ConfigBlock();
                    currentBlock.setSequenceNumber(++sequence);
                    blocks.add(currentBlock);
                    continue;
                }

                if (currentBlock != null) {

                    ConfigEntry entry = parseEntry(line);
                    if (entry == null) {
                        continue;
                    }

                    if (currentBlock.getName() == null) {
                        currentBlock.setName(entry.getKey());
                    }

                    currentBlock.getEntries().add(entry);
                }
            }

        } catch (Exception e) {
            throw new FileParsingException(file.getOriginalFilename(), e);
        }

        enrichBlockOccurrences(blocks);

        boolean trackSectionDetails = false;
        boolean acoIoexbDetails = false;
        boolean dtIoexbDetails = false;
        boolean comDetails = false;

        for (ConfigBlock block : blocks) {

            String blockName = block.getName();

            if ("CFG_ZP_FMA1".equalsIgnoreCase(blockName) || "CFG_ZP_FMA2".equalsIgnoreCase(blockName)) {
                trackSectionDetails = true;
            }

            if ("CFG_AXCNT".equalsIgnoreCase(blockName) || "CFG_SECTION_OUT".equalsIgnoreCase(blockName)) {
                acoIoexbDetails = true;
            }

            if ("CFG_DATA_SAFETY_LEVEL".equalsIgnoreCase(blockName) || "CFG_DATA_OUT".equalsIgnoreCase(blockName)) {
                dtIoexbDetails = true;
            }

            if ("CFG_MY_IP_NW1".equalsIgnoreCase(blockName)) {
                comDetails = true;
            }
        }

        int fileId = 0;
        for (ConfigBlock block : blocks) {
            if ("ID".equals(block.getName())) {
                for (ConfigEntry entry : block.getEntries()) {
                    if ("ID".equals(entry.getKey()) && entry.getValue() != null) {
                        try {
                            fileId = Integer.parseInt(entry.getValue().trim());
                        } catch (NumberFormatException e) {
                            fileId = 0;
                        }
                        break;
                    }
                }
                break;
            }
        }

        return new ParsedConfigFile(file.getOriginalFilename(), blocks, trackSectionDetails, acoIoexbDetails, dtIoexbDetails, comDetails, fileId);
    }

    private static ConfigEntry parseEntry(String line) {

        String comment = null;

        if (line.contains("//")) {
            comment = line.substring(line.indexOf("//") + 2).trim();
            line = line.substring(0, line.indexOf("//")).trim();
        }

        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        String key = tokens[0];

        String[] bitValue = tokens[1].split(":");
        if (bitValue.length != 2) {
            return null;
        }

        int bits;
        try {
            bits = Integer.parseInt(bitValue[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        String value = bitValue[1];

        return new ConfigEntry(key, bits, value, comment);
    }

    private static void enrichBlockOccurrences(List<ConfigBlock> blocks) {

        Map<String, List<ConfigBlock>> blocksByName = new LinkedHashMap<>();

        for (ConfigBlock block : blocks) {
            blocksByName.computeIfAbsent(block.getName(), k -> new ArrayList<>()).add(block);
        }

        for (Map.Entry<String, List<ConfigBlock>> entry : blocksByName.entrySet()) {

            List<ConfigBlock> sameBlocks = entry.getValue();
            int total = sameBlocks.size();

            int startSeq = sameBlocks.stream().mapToInt(ConfigBlock::getSequenceNumber).min().orElse(0);

            int endSeq = sameBlocks.stream().mapToInt(ConfigBlock::getSequenceNumber).max().orElse(0);

            for (int i = 0; i < sameBlocks.size(); i++) {

                ConfigBlock block = sameBlocks.get(i);

                block.setTotalOccurrence(total);
                block.setStartSequenceNumber(startSeq);
                block.setEndSequenceNumber(endSeq);
                block.setBlockIndex(total > 1 ? i : 0);
            }
        }
    }
}
