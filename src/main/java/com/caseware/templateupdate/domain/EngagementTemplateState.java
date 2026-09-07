package com.caseware.templateupdate.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;

/**
 * The projected template state of a single engagement file.
 *
 * <p>This is the read model that makes the whole design viable. Reading an engagement's real
 * template version requires loading it, which costs about a minute; instead this state is
 * projected from the three events that can change it — creation, apply and decline — and so is
 * exact rather than approximate.
 *
 * <p>The state is <em>monotonic</em>: {@link #withApplied(TemplateVersion)} never moves the
 * current version backwards, which is what makes the projection safe against duplicate and
 * out-of-order event delivery.
 *
 * @param engagementId the engagement this state describes
 * @param templateId the product template the engagement was built from
 * @param currentVersion the template version the engagement currently sits on
 * @param declinedVersions versions the user has declined; a deferral, never a skip
 * @implNote Built through {@code EngagementTemplateState.builder()}; {@code declinedVersion(..)}
 *     accumulates one at a time and defaults to empty when never called.
 */
@Builder
public record EngagementTemplateState(
        EngagementId engagementId,
        TemplateId templateId,
        TemplateVersion currentVersion,
        @Singular("declinedVersion") Set<TemplateVersion> declinedVersions) {

    /** Takes a defensive, unmodifiable copy of the declined versions. */
    public EngagementTemplateState {
        declinedVersions = Collections.unmodifiableSet(new LinkedHashSet<>(declinedVersions));
    }

    /**
     * Reports whether the user has already declined a given version.
     *
     * @param version the version to check
     * @return {@code true} if that version was declined
     */
    public boolean hasDeclined(TemplateVersion version) {
        return declinedVersions.contains(version);
    }

    /**
     * Advances the engagement onto a newly applied template version.
     *
     * <p>Applying an older or equal version is ignored rather than rejected: events can be
     * redelivered or arrive out of order, and silently keeping the newer state is the behaviour
     * that makes the projection converge. Once a version is applied it is no longer pending, so
     * it is dropped from the declined set.
     *
     * @param version the version that was applied
     * @return the advanced state, or this same state if {@code version} is not newer
     */
    public EngagementTemplateState withApplied(TemplateVersion version) {
        if (!version.isAfter(currentVersion)) {
            return this;
        }
        Set<TemplateVersion> remaining = new LinkedHashSet<>(declinedVersions);
        remaining.removeIf(declined -> !declined.isAfter(version));
        return EngagementTemplateState.builder()
                .engagementId(engagementId)
                .templateId(templateId)
                .currentVersion(version)
                .declinedVersions(remaining)
                .build();
    }

    /**
     * Records that the user declined a version, leaving the current version untouched.
     *
     * <p>Declining a version at or below the current one is ignored: there is nothing to defer.
     *
     * @param version the version that was declined
     * @return the state with the decline recorded
     */
    public EngagementTemplateState withDeclined(TemplateVersion version) {
        if (!version.isAfter(currentVersion)) {
            return this;
        }
        Set<TemplateVersion> updated = new LinkedHashSet<>(declinedVersions);
        updated.add(version);
        return EngagementTemplateState.builder()
                .engagementId(engagementId)
                .templateId(templateId)
                .currentVersion(currentVersion)
                .declinedVersions(updated)
                .build();
    }

    /**
     * Creates the initial state for a newly created engagement file.
     *
     * @param engagementId the new engagement
     * @param templateId the template it was created from
     * @param version the template version it was created with
     * @return a state with no declined versions
     */
    public static EngagementTemplateState createdWith(
            EngagementId engagementId, TemplateId templateId, TemplateVersion version) {
        return EngagementTemplateState.builder()
                .engagementId(engagementId)
                .templateId(templateId)
                .currentVersion(version)
                .build();
    }
}
