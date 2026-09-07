package com.caseware.templateupdate.api;

import java.util.List;
import lombok.Builder;
import lombok.Singular;

/**
 * One row of the pending-updates list, as sent to the dashboard.
 *
 * <p>Kept separate from the domain {@code PendingUpdate} so the wire format and the internal model
 * can evolve independently.
 *
 * @param engagementId the engagement with a pending update
 * @param templateId the product template being updated
 * @param currentVersion the version the engagement sits on today
 * @param latestVersion the newest published version
 * @param versionsBehind how many published versions have accumulated
 * @param previouslyDeclined versions in this range the user has already declined once
 * @param summaryHeadline a one-line orientation on what changed
 * @param summaryPoints the individual statements about what changed
 * @implNote Built through {@code PendingUpdateView.builder()}: eight components, five of them
 *     {@code String} or {@code int}, are too easy to transpose positionally.
 */
@Builder
public record PendingUpdateView(
        String engagementId,
        String templateId,
        int currentVersion,
        int latestVersion,
        int versionsBehind,
        @Singular("previouslyDeclined") List<Integer> previouslyDeclined,
        String summaryHeadline,
        @Singular("summaryPoint") List<String> summaryPoints) {

    /** Copies the collections defensively. */
    public PendingUpdateView {
        previouslyDeclined = List.copyOf(previouslyDeclined);
        summaryPoints = List.copyOf(summaryPoints);
    }
}
