package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.ChangeRecord;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.JsonDiffEntry;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.List;
import java.util.Locale;

/**
 * Classifies diff entries by their location in the template structure.
 *
 * <p>Identifiers are derived from the JSON pointer rather than allocated, so the same diff always
 * yields the same ids. That reproducibility is what makes a cached summary comparable with a
 * freshly computed one, and what lets a citation in stored prose still resolve months later.
 */
public class PathBasedChangeClassifier implements ChangeClassifier {

    private static final String UNKNOWN_AREA = "Other content";

    /** Creates a classifier. It holds no state, so a single instance can be shared freely. */
    public PathBasedChangeClassifier() {
        // Stateless by design.
    }

    @Override
    public ChangeSet classify(
            TemplateId templateId,
            TemplateVersion fromVersion,
            TemplateVersion toVersion,
            JsonDiff diff) {
        return ChangeSet.builder()
                .templateId(templateId)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .records(diff.entries().stream().map(this::toRecord).toList())
                .build();
    }

    private ChangeRecord toRecord(JsonDiffEntry entry) {
        List<String> segments = segmentsOf(entry.pointer());
        return ChangeRecord.builder()
                .id(idFor(entry.pointer()))
                .kind(entry.kind())
                .area(areaFor(segments))
                .humanPath(humanPathFor(segments))
                .detail(detailFor(entry))
                .build();
    }

    /**
     * Derives a stable id from the pointer.
     *
     * <p>A hash keeps ids short and independent of position, so inserting a procedure earlier in
     * the file does not renumber every change after it.
     */
    private String idFor(String pointer) {
        return "CR-%08X".formatted(pointer.hashCode());
    }

    private List<String> segmentsOf(String pointer) {
        return java.util.Arrays.stream(pointer.split("/")).filter(part -> !part.isBlank()).toList();
    }

    /** The first pointer segment names the part of the file a practitioner would recognise. */
    private String areaFor(List<String> segments) {
        if (segments.isEmpty()) {
            return UNKNOWN_AREA;
        }
        return humanise(segments.get(0));
    }

    /** The remaining segments locate the change within that area, skipping array indices. */
    private String humanPathFor(List<String> segments) {
        if (segments.size() <= 1) {
            return areaFor(segments);
        }
        List<String> tail =
                segments.subList(1, segments.size()).stream()
                        .filter(segment -> !segment.chars().allMatch(Character::isDigit))
                        .map(this::humanise)
                        .toList();
        return tail.isEmpty() ? areaFor(segments) : String.join(" › ", tail);
    }

    private String detailFor(JsonDiffEntry entry) {
        return switch (entry.kind()) {
            case ADDED -> "Added: " + nullSafe(entry.after());
            case REMOVED -> "Removed: " + nullSafe(entry.before());
            case MODIFIED -> "Changed from \"%s\" to \"%s\""
                    .formatted(nullSafe(entry.before()), nullSafe(entry.after()));
        };
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String humanise(String segment) {
        String spaced = segment.replace('_', ' ').replace('-', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
