package com.caseware.templateupdate.resolver;

import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.PendingUpdate;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Works out whether an engagement has a pending template update, and which version range that
 * update spans.
 *
 * <p>A pure function with no collaborators: the same inputs always give the same answer, which
 * is what lets the same instance serve the read API and the publish fan-out worker.
 */
public class PendingUpdateResolver {

    /** Creates a resolver. It holds no state, so a single instance can be shared freely. */
    public PendingUpdateResolver() {
        // No collaborators by design: this keeps the rule pure and trivially testable.
    }

    /**
     * Resolves the pending update for one engagement.
     *
     * <p>The range always starts at the version the engagement currently sits on. A declined
     * version is a deferral, not a skip, so declining never advances the starting point:
     *
     * <pre>
     *   engagement on v3, user declines v4, then v5 is published
     *     -&gt; the pending range is v3 to v5, with v4 reported as previously declined
     * </pre>
     *
     * @param state the engagement's projected template state
     * @param latestPublished the newest published version of that template
     * @return the pending update, or empty if the engagement is already current
     */
    public Optional<PendingUpdate> resolve(
            EngagementTemplateState state, TemplateVersion latestPublished) {
        if (!latestPublished.isAfter(state.currentVersion())) {
            return Optional.empty();
        }

        List<TemplateVersion> declinedInRange =
                state.declinedVersions().stream()
                        .filter(declined -> declined.isAfter(state.currentVersion()))
                        .filter(declined -> !declined.isAfter(latestPublished))
                        .sorted(Comparator.naturalOrder())
                        .toList();

        return Optional.of(
                PendingUpdate.builder()
                        .engagementId(state.engagementId())
                        .templateId(state.templateId())
                        .fromVersion(state.currentVersion())
                        .toVersion(latestPublished)
                        .declinedInRange(declinedInRange)
                        .build());
    }
}
