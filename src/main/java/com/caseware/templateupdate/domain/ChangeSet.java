package com.caseware.templateupdate.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Singular;

/**
 * Every classified change between two versions of one product template.
 *
 * <p>A change set is firm-independent: it describes template content only and contains no
 * engagement or customer data. That is precisely what allows one summary to be computed once and
 * reused for every firm on the same version pair.
 *
 * @param templateId the template the changes belong to
 * @param fromVersion the older version
 * @param toVersion the newer version
 * @param records the classified changes, in a stable order
 * @implNote Built through {@code ChangeSet.builder()}; {@code record(..)} accumulates changes
 *     one at a time and defaults to empty when never called.
 */
@Builder
public record ChangeSet(
        TemplateId templateId,
        TemplateVersion fromVersion,
        TemplateVersion toVersion,
        @Singular List<ChangeRecord> records) {

    /**
     * Validates the version range and copies the records defensively.
     *
     * @throws IllegalArgumentException if {@code toVersion} is not strictly after
     *     {@code fromVersion}
     */
    public ChangeSet {
        if (!toVersion.isAfter(fromVersion)) {
            throw new IllegalArgumentException(
                    "a change set must move forwards, got " + fromVersion + " to " + toVersion);
        }
        records = List.copyOf(records);
    }

    /**
     * Reports whether the two versions differ at all.
     *
     * @return {@code true} if there are no classified changes
     */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * Lists the identifiers of every change in this set.
     *
     * @return the change record ids
     */
    public Set<String> recordIds() {
        return records.stream().map(ChangeRecord::id).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Looks up a single change by its identifier.
     *
     * @param id the change record id to find
     * @return the matching record, or empty if this set does not contain it
     */
    public Optional<ChangeRecord> findById(String id) {
        return records.stream().filter(record -> record.id().equals(id)).findFirst();
    }

    /**
     * Groups the changes by the practitioner-facing area they affect.
     *
     * @return changes by area, in first-seen order
     */
    public Map<String, List<ChangeRecord>> groupedByArea() {
        return records.stream()
                .collect(
                        Collectors.groupingBy(
                                ChangeRecord::area, LinkedHashMap::new, Collectors.toList()));
    }
}
