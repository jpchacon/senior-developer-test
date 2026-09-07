package com.caseware.templateupdate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caseware.templateupdate.cache.ChangeSummaryKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A builder is a convenience, never a way around an invariant.
 *
 * <p>Generated code is not exempt from the rules these types enforce, and a builder that skipped
 * the compact constructor would quietly turn "an invalid instance cannot exist" into "an invalid
 * instance cannot be constructed one particular way". These tests pin that it does not.
 */
@DisplayName("Builders honour the same invariants as the constructors")
class BuilderContractTest {

    @Test
    @DisplayName("a built change record is still rejected when its identity is missing")
    void builtChangeRecordStillValidated() {
        assertThatThrownBy(
                        () ->
                                ChangeRecord.builder()
                                        .id(" ")
                                        .kind(ChangeKind.ADDED)
                                        .area("Procedures")
                                        .humanPath("Revenue testing")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a built change set is still rejected when its versions run backwards")
    void builtChangeSetStillValidated() {
        assertThatThrownBy(
                        () ->
                                ChangeSet.builder()
                                        .templateId(new TemplateId("audit-ca"))
                                        .fromVersion(new TemplateVersion(5))
                                        .toVersion(new TemplateVersion(3))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a built pending update is still rejected when it does not move forwards")
    void builtPendingUpdateStillValidated() {
        assertThatThrownBy(
                        () ->
                                PendingUpdate.builder()
                                        .engagementId(new EngagementId("eng-1"))
                                        .templateId(new TemplateId("audit-ca"))
                                        .fromVersion(new TemplateVersion(4))
                                        .toVersion(new TemplateVersion(4))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an unset collection builds as empty rather than null")
    void unsetCollectionsAreEmpty() {
        PendingUpdate update =
                PendingUpdate.builder()
                        .engagementId(new EngagementId("eng-1"))
                        .templateId(new TemplateId("audit-ca"))
                        .fromVersion(new TemplateVersion(3))
                        .toVersion(new TemplateVersion(5))
                        .build();

        assertThat(update.declinedInRange())
                .as("a builder cannot force every field to be set, so the collections must default")
                .isEmpty();
        assertThat(ChangeSet.builder()
                        .templateId(new TemplateId("audit-ca"))
                        .fromVersion(new TemplateVersion(3))
                        .toVersion(new TemplateVersion(5))
                        .build()
                        .isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("collections accumulate one element at a time")
    void collectionsAccumulate() {
        EngagementTemplateState state =
                EngagementTemplateState.builder()
                        .engagementId(new EngagementId("eng-1"))
                        .templateId(new TemplateId("audit-ca"))
                        .currentVersion(new TemplateVersion(3))
                        .declinedVersion(new TemplateVersion(4))
                        .declinedVersion(new TemplateVersion(5))
                        .build();

        assertThat(state.declinedVersions())
                .containsExactly(new TemplateVersion(4), new TemplateVersion(5));
    }

    @Test
    @DisplayName("builds a change set from accumulated records")
    void buildsChangeSetFromRecords() {
        ChangeSet changeSet =
                ChangeSet.builder()
                        .templateId(new TemplateId("audit-ca"))
                        .fromVersion(new TemplateVersion(3))
                        .toVersion(new TemplateVersion(5))
                        .record(
                                ChangeRecord.builder()
                                        .id("CR-1")
                                        .kind(ChangeKind.ADDED)
                                        .area("Procedures")
                                        .humanPath("Revenue testing")
                                        .detail("Added a revenue testing step")
                                        .build())
                        .build();

        assertThat(changeSet.records()).hasSize(1);
        assertThat(changeSet.recordIds()).containsExactly("CR-1");
    }

    @Test
    @DisplayName("names the two versions of a summary key so they cannot be transposed")
    void summaryKeyNamesItsVersions() {
        ChangeSummaryKey key =
                ChangeSummaryKey.builder()
                        .templateId(new TemplateId("audit-ca"))
                        .fromVersion(new TemplateVersion(3))
                        .toVersion(new TemplateVersion(5))
                        .build();

        assertThat(key.fromVersion()).isEqualTo(new TemplateVersion(3));
        assertThat(key.toVersion()).isEqualTo(new TemplateVersion(5));
    }

    @Test
    @DisplayName("a built diff entry keeps before and after the right way round")
    void diffEntryKeepsDirection() {
        JsonDiffEntry entry =
                JsonDiffEntry.builder()
                        .pointer("/procedures/1/title")
                        .kind(ChangeKind.MODIFIED)
                        .before("Old wording")
                        .after("New wording")
                        .build();

        assertThat(entry.before()).isEqualTo("Old wording");
        assertThat(entry.after()).isEqualTo("New wording");
    }
}
