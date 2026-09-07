package com.caseware.templateupdate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EngagementTemplateState")
class EngagementTemplateStateTest {

    private static final EngagementId ENGAGEMENT = new EngagementId("eng-1");
    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");

    private static EngagementTemplateState onVersion(int version) {
        return EngagementTemplateState.createdWith(ENGAGEMENT, TEMPLATE, new TemplateVersion(version));
    }

    @Test
    @DisplayName("advances when a newer version is applied")
    void advancesOnApply() {
        EngagementTemplateState state = onVersion(3).withApplied(new TemplateVersion(5));

        assertThat(state.currentVersion()).isEqualTo(new TemplateVersion(5));
    }

    @Test
    @DisplayName("ignores a redelivered apply of the version it already holds")
    void ignoresDuplicateApply() {
        EngagementTemplateState state = onVersion(5).withApplied(new TemplateVersion(5));

        assertThat(state.currentVersion()).isEqualTo(new TemplateVersion(5));
    }

    @Test
    @DisplayName("never moves backwards when events arrive out of order")
    void ignoresOutOfOrderApply() {
        EngagementTemplateState state =
                onVersion(3).withApplied(new TemplateVersion(6)).withApplied(new TemplateVersion(4));

        assertThat(state.currentVersion())
                .as("a late v4 event must not undo the v6 that already landed")
                .isEqualTo(new TemplateVersion(6));
    }

    @Test
    @DisplayName("records a decline without changing the current version")
    void recordsDecline() {
        EngagementTemplateState state = onVersion(3).withDeclined(new TemplateVersion(4));

        assertThat(state.currentVersion()).isEqualTo(new TemplateVersion(3));
        assertThat(state.hasDeclined(new TemplateVersion(4))).isTrue();
    }

    @Test
    @DisplayName("ignores a decline of a version already applied")
    void ignoresDeclineOfAppliedVersion() {
        EngagementTemplateState state = onVersion(5).withDeclined(new TemplateVersion(3));

        assertThat(state.declinedVersions()).isEmpty();
    }

    @Test
    @DisplayName("clears declines that applying a later version has settled")
    void applyingClearsSupersededDeclines() {
        EngagementTemplateState state =
                onVersion(3)
                        .withDeclined(new TemplateVersion(4))
                        .withDeclined(new TemplateVersion(6))
                        .withApplied(new TemplateVersion(5));

        assertThat(state.declinedVersions()).containsExactly(new TemplateVersion(6));
    }

    @Test
    @DisplayName("copies the declined versions defensively")
    void copiesDeclinedVersionsDefensively() {
        Set<TemplateVersion> mutable = new HashSet<>(Set.of(new TemplateVersion(4)));
        EngagementTemplateState state =
                new EngagementTemplateState(ENGAGEMENT, TEMPLATE, new TemplateVersion(3), mutable);

        mutable.add(new TemplateVersion(9));

        assertThat(state.declinedVersions()).containsExactly(new TemplateVersion(4));
        assertThatThrownBy(() -> state.declinedVersions().add(new TemplateVersion(9)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
