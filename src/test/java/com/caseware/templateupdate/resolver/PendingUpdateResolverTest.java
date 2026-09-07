package com.caseware.templateupdate.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.EngagementId;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.PendingUpdate;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PendingUpdateResolver")
class PendingUpdateResolverTest {

    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");
    private static final EngagementId ENGAGEMENT = new EngagementId("eng-1");

    private final PendingUpdateResolver resolver = new PendingUpdateResolver();

    private static EngagementTemplateState onVersion(int current, int... declined) {
        Set<TemplateVersion> declinedVersions = new java.util.HashSet<>();
        for (int v : declined) {
            declinedVersions.add(new TemplateVersion(v));
        }
        return new EngagementTemplateState(
                ENGAGEMENT, TEMPLATE, new TemplateVersion(current), declinedVersions);
    }

    @Nested
    @DisplayName("when nothing is pending")
    class NoPendingUpdate {

        @Test
        @DisplayName("returns empty when the engagement is already on the latest version")
        void returnsEmptyWhenAlreadyOnLatest() {
            Optional<PendingUpdate> result = resolver.resolve(onVersion(4), new TemplateVersion(4));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the published version is older than the engagement")
        void returnsEmptyWhenPublishedIsOlder() {
            Optional<PendingUpdate> result = resolver.resolve(onVersion(6), new TemplateVersion(4));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("when a single update is available")
    class SingleStep {

        @Test
        @DisplayName("spans exactly one version and reports one version behind")
        void spansOneVersion() {
            PendingUpdate update =
                    resolver.resolve(onVersion(3), new TemplateVersion(4)).orElseThrow();

            assertThat(update.fromVersion()).isEqualTo(new TemplateVersion(3));
            assertThat(update.toVersion()).isEqualTo(new TemplateVersion(4));
            assertThat(update.versionsBehind()).isEqualTo(1);
            assertThat(update.declinedInRange()).isEmpty();
        }
    }

    @Nested
    @DisplayName("when updates have accumulated")
    class Accumulation {

        @Test
        @DisplayName("spans the whole range v3 to v6 rather than one step at a time")
        void spansTheWholeRange() {
            PendingUpdate update =
                    resolver.resolve(onVersion(3), new TemplateVersion(6)).orElseThrow();

            assertThat(update.fromVersion()).isEqualTo(new TemplateVersion(3));
            assertThat(update.toVersion()).isEqualTo(new TemplateVersion(6));
            assertThat(update.versionsBehind()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("when the user has declined a version")
    class Declines {

        @Test
        @DisplayName("declining v4 does not advance the from version when v5 publishes")
        void declineDoesNotAdvanceTheFromVersion() {
            PendingUpdate update =
                    resolver.resolve(onVersion(3, 4), new TemplateVersion(5)).orElseThrow();

            assertThat(update.fromVersion()).isEqualTo(new TemplateVersion(3));
            assertThat(update.toVersion()).isEqualTo(new TemplateVersion(5));
        }

        @Test
        @DisplayName("reports the declined versions falling inside the pending range")
        void reportsDeclinedVersionsInRange() {
            PendingUpdate update =
                    resolver.resolve(onVersion(3, 4, 5), new TemplateVersion(6)).orElseThrow();

            assertThat(update.declinedInRange())
                    .containsExactly(new TemplateVersion(4), new TemplateVersion(5));
        }

        @Test
        @DisplayName("ignores declined versions that fall outside the pending range")
        void ignoresDeclinesOutsideRange() {
            PendingUpdate update =
                    resolver.resolve(onVersion(3, 2), new TemplateVersion(5)).orElseThrow();

            assertThat(update.declinedInRange()).isEmpty();
        }

        @Test
        @DisplayName("declining the latest version leaves it pending, not resolved")
        void decliningTheLatestLeavesItPending() {
            Optional<PendingUpdate> result =
                    resolver.resolve(onVersion(3, 4), new TemplateVersion(4));

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().declinedInRange())
                    .containsExactly(new TemplateVersion(4));
        }
    }
}
