package com.caseware.templateupdate.domain;

import lombok.Builder;

/**
 * One entry of a structural diff between two template versions.
 *
 * <p>The spec grants a fast, reliable JSON diff of two template versions; this record is that
 * diff's shape at our boundary. Modelling it explicitly keeps the diff tool swappable.
 *
 * @param pointer JSON pointer to the changed location, for example {@code /procedures/3/title}
 * @param kind what kind of change occurred
 * @param before the previous value, or null when the entry was added
 * @param after the new value, or null when the entry was removed
 * @implNote Built through {@code JsonDiffEntry.builder()}. Transposing {@code before} and
 *     {@code after} would invert the meaning of a diff without failing anything, so they are
 *     named at the call site.
 */
@Builder
public record JsonDiffEntry(String pointer, ChangeKind kind, String before, String after) {

    /**
     * Validates the entry.
     *
     * @throws IllegalArgumentException if {@code pointer} is blank or {@code kind} is null
     */
    public JsonDiffEntry {
        if (pointer == null || pointer.isBlank()) {
            throw new IllegalArgumentException("diff entry pointer must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("diff entry kind must not be null");
        }
    }
}
