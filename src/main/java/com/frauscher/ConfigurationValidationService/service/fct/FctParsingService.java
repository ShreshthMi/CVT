package com.frauscher.ConfigurationValidationService.service.fct;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;

import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidException;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidReason;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates FCT upload parsing (design §5.1–§5.2): validates the {@code .fct2} as a ZIP, walks its
 * entries under decompression/size caps (rejecting nested archives, oversize/zip-bomb entries, and
 * corrupt/password-protected archives), extracts the mandatory {@code project.xml}, and parses it into
 * a {@link ComAebMap}. Trackplan XMLs are out of scope (read only to enforce caps, then discarded).
 */
@Service
@RequiredArgsConstructor
public class FctParsingService {

    private static final long MAX_TOTAL_UNCOMPRESSED = 50L * 1024 * 1024;
    private static final long MAX_ENTRY_UNCOMPRESSED = 20L * 1024 * 1024;
    private static final long MAX_COMPRESSION_RATIO = 50;
    private static final String PROJECT_XML = "project.xml";
    private static final Set<String> ARCHIVE_EXTENSIONS =
            Set.of(".zip", ".fct2", ".jar", ".7z", ".rar", ".tar", ".gz");

    private final FctProjectXmlParser projectXmlParser;

    public ComAebMap parse(byte[] fctArchive) {
        requireZipMagic(fctArchive);
        byte[] projectXml = extractProjectXml(fctArchive);
        return projectXmlParser.parse(new ByteArrayInputStream(projectXml));
    }

    private void requireZipMagic(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B || bytes[2] != 0x03 || bytes[3] != 0x04) {
            throw new FctInvalidException(FctInvalidReason.NOT_A_VALID_ARCHIVE, "not a ZIP archive (bad magic bytes)");
        }
    }

    private byte[] extractProjectXml(byte[] bytes) {
        byte[] projectXml = null;
        long totalUncompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String baseName = name.substring(name.lastIndexOf('/') + 1).toLowerCase();
                if (isArchive(baseName)) {
                    throw new FctInvalidException(FctInvalidReason.NESTED_ARCHIVE, "nested archive entry: " + name);
                }
                byte[] data = readEntry(zip, entry);
                totalUncompressed += data.length;
                if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED) {
                    throw new FctInvalidException(FctInvalidReason.DECOMPRESSION_LIMIT_EXCEEDED,
                            "total uncompressed size exceeds " + MAX_TOTAL_UNCOMPRESSED + " bytes");
                }
                if (PROJECT_XML.equals(name)) {
                    projectXml = data;
                }
            }
        } catch (FctInvalidException e) {
            throw e;
        } catch (IOException e) {
            // java.util.zip throws here for corrupt or password-protected (encrypted) archives.
            throw new FctInvalidException(FctInvalidReason.NOT_A_VALID_ARCHIVE,
                    "could not read archive (corrupt or password-protected): " + e.getMessage());
        }
        if (projectXml == null) {
            throw new FctInvalidException(FctInvalidReason.PROJECT_XML_MISSING, "archive has no project.xml");
        }
        return projectXml;
    }

    private byte[] readEntry(ZipInputStream zip, ZipEntry entry) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            count += read;
            if (count > MAX_ENTRY_UNCOMPRESSED) {
                throw new FctInvalidException(FctInvalidReason.DECOMPRESSION_LIMIT_EXCEEDED,
                        "entry " + entry.getName() + " exceeds the per-entry cap");
            }
            out.write(buffer, 0, read);
        }
        long compressed = entry.getCompressedSize();
        if (compressed > 0 && count / compressed > MAX_COMPRESSION_RATIO) {
            throw new FctInvalidException(FctInvalidReason.DECOMPRESSION_LIMIT_EXCEEDED,
                    "entry " + entry.getName() + " compression ratio exceeds " + MAX_COMPRESSION_RATIO + ":1");
        }
        return out.toByteArray();
    }

    private boolean isArchive(String baseName) {
        return ARCHIVE_EXTENSIONS.stream().anyMatch(baseName::endsWith);
    }
}