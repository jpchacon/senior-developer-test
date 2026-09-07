package com.caseware.templateupdate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The value types refuse invalid states rather than letting them travel through the system. */
@DisplayName("Domain invariants")
class DomainInvariantsTest {

    @Test
    @DisplayName("identifiers reject blank values")
    void identifiersRejectBlanks() {
        assertThatThrownBy(() -> new TemplateId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngagementId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("template versions must be positive and order naturally")
    void templateVersionsAreOrdered() {
        assertThatThrownBy(() -> new TemplateVersion(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TemplateVersion(5).isAfter(new TemplateVersion(3))).isTrue();
        assertThat(new TemplateVersion(3).isBefore(new TemplateVersion(5))).isTrue();
        assertThat(new TemplateVersion(3).compareTo(new TemplateVersion(5))).isNegative();
        assertThat(new TemplateVersion(4)).hasToString("v4");
        assertThat(new TemplateId("audit-ca")).hasToString("audit-ca");
        assertThat(new EngagementId("eng-1")).hasToString("eng-1");
    }

    @Test
    @DisplayName("a pending update cannot run backwards")
    void pendingUpdateMustMoveForwards() {
        assertThatThrownBy(
                        () ->
                                new PendingUpdate(
                                        new EngagementId("eng-1"),
                                        new TemplateId("audit-ca"),
                                        new TemplateVersion(5),
                                        new TemplateVersion(3),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a change set cannot run backwards")
    void changeSetMustMoveForwards() {
        assertThatThrownBy(
                        () ->
                                new ChangeSet(
                                        new TemplateId("audit-ca"),
                                        new TemplateVersion(5),
                                        new TemplateVersion(5),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("change records reject missing identity or location")
    void changeRecordsRejectBlanks() {
        assertThatThrownBy(() -> new ChangeRecord("", ChangeKind.ADDED, "Area", "Path", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeRecord("CR-1", null, "Area", "Path", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeRecord("CR-1", ChangeKind.ADDED, " ", "Path", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeRecord("CR-1", ChangeKind.ADDED, "Area", " ", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ChangeRecord("CR-1", ChangeKind.ADDED, "Area", "Path", null).detail())
                .isEmpty();
    }

    @Test
    @DisplayName("a summary bullet must say something and cite something")
    void summaryBulletsMustCite() {
        assertThatThrownBy(() -> new SummaryBullet(" ", List.of("CR-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SummaryBullet("text", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NarratedSummary(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("diff entries reject a missing location or kind")
    void diffEntriesRejectBlanks() {
        assertThatThrownBy(() -> new JsonDiffEntry(" ", ChangeKind.ADDED, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonDiffEntry("/a", null, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JsonDiff(List.of()).entries()).isEmpty();
    }

    @Test
    @DisplayName("a change set can be searched and grouped")
    void changeSetLookups() {
        ChangeSet changeSet =
                new ChangeSet(
                        new TemplateId("audit-ca"),
                        new TemplateVersion(3),
                        new TemplateVersion(4),
                        List.of(
                                new ChangeRecord("CR-1", ChangeKind.ADDED, "Procedures", "One", "d"),
                                new ChangeRecord("CR-2", ChangeKind.REMOVED, "Procedures", "Two", "d")));

        assertThat(changeSet.isEmpty()).isFalse();
        assertThat(changeSet.recordIds()).containsExactlyInAnyOrder("CR-1", "CR-2");
        assertThat(changeSet.findById("CR-1")).isPresent();
        assertThat(changeSet.findById("CR-9")).isEmpty();
        assertThat(changeSet.groupedByArea()).containsOnlyKeys("Procedures");
    }

    @Test
    @DisplayName("a validation failure must name at least one violation")
    void failedValidationNeedsAViolation() {
        assertThatThrownBy(() -> com.caseware.templateupdate.summary.ValidationResult.failed(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
