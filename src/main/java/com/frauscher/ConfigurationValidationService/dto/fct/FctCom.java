package com.frauscher.ConfigurationValidationService.dto.fct;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The surviving COM of a CAN segment (post-redundancy collapse). IDs serialised as strings.
 *
 * <p>{@code station} is the name of the {@code <Station>} the segment's backplanes sit under in
 * Project.xml — the FCT half of the location key. A project may hold several stations whose AEB, FMA
 * and COM names all repeat, so it is the station that separates them. Null when the document has no
 * Station ancestor, in which case every name-keyed lookup stays on its legacy project-wide path.</p>
 */
public record FctCom(
        String comId,
        String comName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String station) {
}
