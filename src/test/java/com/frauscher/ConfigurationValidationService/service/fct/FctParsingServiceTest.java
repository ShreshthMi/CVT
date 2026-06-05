package com.frauscher.ConfigurationValidationService.service.fct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidException;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidReason;
import com.frauscher.ConfigurationValidationService.testsupport.FctFixtures;

/**
 * End-to-end: opens the real {@code .fct2} archives (ZIP security + extraction + Project.xml parse).
 */
class FctParsingServiceTest {

    private final FctParsingService service = new FctParsingService(new FctProjectXmlParser());

    @Test
    void parsesAcoArchive() {
        ComAebMap map = service.parse(FctFixtures.acoBytes());
        assertEquals(2, map.chains().size());
    }

    @Test
    void parsesRedundantArchive() {
        ComAebMap map = service.parse(FctFixtures.redundantBytes());
        assertTrue(map.chains().stream().anyMatch(Chain::redundantComPresent));
    }

    @Test
    void rejectsDuplicateAebIdArchive() {
        FctInvalidException ex = assertThrows(FctInvalidException.class,
                () -> service.parse(FctFixtures.duplicateIdBytes()));
        assertEquals(FctInvalidReason.DUPLICATE_ENTITY_ID, ex.getReason());
    }

    @Test
    void rejectsNonZip() {
        FctInvalidException ex = assertThrows(FctInvalidException.class,
                () -> service.parse("this is not a zip".getBytes()));
        assertEquals(FctInvalidReason.NOT_A_VALID_ARCHIVE, ex.getReason());
    }
}