package com.caseware.templateupdate.domain;

import java.util.List;
import lombok.Builder;
import lombok.Singular;

/**
 * A template update awaiting a practitioner's apply or decline decision.
 *
 * <p>The range is always expressed as the <em>net</em> change from the version the engagement
 * currently sits on to the latest published version, never as a chain of intermediate steps.
 * See {@code DESIGN.md}, section 5, for why composing per-step summaries would be wrong.
 *
 * @param engagementId the engagement the update applies to
 * @param templateId the product template being updated
 * @param fromVersion the version the engagement currently sits on
 * @param toVersion the latest published version
 * @param declinedInRange versions inside the range the user has previously declined, ascending;
 *     shown for context only, they never alter the range itself
 * @implNote Built through {@code PendingUpdate.builder()}: {@code fromVersion} and
 *     {@code toVersion} share a type and carry the whole meaning of the update.
 */
@Builder
public record PendingUpdate(
        EngagementId engagementId,
        TemplateId templateId,
        TemplateVersion fromVersion,
        TemplateVersion toVersion,
        @Singular("declinedInRange") List<TemplateVersion> declinedInRange) {

    /**
     * Validates the range and takes a defensive copy of the declined versions.
     *
     * @throws IllegalArgumentException if {@code toVersion} is not strictly after
     *     {@code fromVersion}
     */
    public PendingUpdate {
        if (!toVersion.isAfter(fromVersion)) {
            throw new IllegalArgumentException(
                    "a pending update must move forwards, got " + fromVersion + " to " + toVersion);
        }
        declinedInRange = List.copyOf(declinedInRange);
    }

    /**
     * Reports how far behind the engagement has fallen.
     *
     * @return the number of published versions between the current and latest version
     */
    public int versionsBehind() {
        return toVersion.number() - fromVersion.number();
    }
}
