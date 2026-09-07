package com.caseware.templateupdate.domain;

import lombok.Builder;

/**
 * One classified, human-locatable change between two template versions.
 *
 * <p>This is the deterministic ground truth that a narrated summary must be traceable to. The
 * language model never produces change records; it only describes the ones already derived from
 * the diff, which is what makes a fabricated claim detectable.
 *
 * @param id stable identifier, derived from the change location so it is reproducible
 * @param kind what kind of change occurred
 * @param area the practitioner-facing part of the file affected, such as "Audit Procedures"
 * @param humanPath where in that area the change sits, in the user's language
 * @param detail a short factual description of the change, taken from the diff, not invented
 * @implNote Built through {@code ChangeRecord.builder()}; three adjacent {@code String}
 *     components would otherwise be silently swappable.
 */
@Builder
public record ChangeRecord(String id, ChangeKind kind, String area, String humanPath, String detail) {

    /**
     * Validates the record.
     *
     * @throws IllegalArgumentException if the id, area or human path is blank, or kind is null
     */
    public ChangeRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("change record id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("change record kind must not be null");
        }
        if (area == null || area.isBlank()) {
            throw new IllegalArgumentException("change record area must not be blank");
        }
        if (humanPath == null || humanPath.isBlank()) {
            throw new IllegalArgumentException("change record humanPath must not be blank");
        }
        detail = detail == null ? "" : detail;
    }
}
