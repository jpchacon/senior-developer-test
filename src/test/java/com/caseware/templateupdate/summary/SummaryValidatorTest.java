package com.caseware.templateupdate.summary;

import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.bullet;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.faithfulSummary;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.threeChanges;
import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SummaryValidator")
class SummaryValidatorTest {

    private final SummaryValidator validator = new SummaryValidator();

    @Test
    @DisplayName("accepts a summary that cites every change and invents nothing")
    void acceptsFaithfulSummary() {
        ValidationResult result = validator.validate(threeChanges(), faithfulSummary());

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("rejects a summary citing a change that does not exist")
    void rejectsDanglingCitation() {
        ChangeSet changeSet = threeChanges();
        NarratedSummary summary =
                new NarratedSummary(
                        "Three changes",
                        List.of(
                                bullet("Something about revenue.", "CR-1", "CR-2"),
                                bullet("A materiality threshold was raised.", "CR-3", "CR-999")));

        ValidationResult result = validator.validate(changeSet, summary);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CR-999"));
    }

    @Test
    @DisplayName("rejects a summary that silently omits a change")
    void rejectsUncoveredChange() {
        ChangeSet changeSet = threeChanges();
        NarratedSummary summary =
                new NarratedSummary(
                        "Some changes",
                        List.of(bullet("Revenue and cut-off testing changed.", "CR-1", "CR-2")));

        ValidationResult result = validator.validate(changeSet, summary);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("CR-3"));
    }

    @Test
    @DisplayName("rejects a summary stating a number that appears nowhere in the changes")
    void rejectsInventedNumber() {
        ChangeSet changeSet = threeChanges();
        NarratedSummary summary =
                new NarratedSummary(
                        "Three changes across two areas",
                        List.of(
                                bullet("Revenue and cut-off testing changed.", "CR-1", "CR-2"),
                                bullet("The going concern note was removed, affecting 47 procedures.", "CR-3")));

        ValidationResult result = validator.validate(changeSet, summary);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("47"));
    }

    @Test
    @DisplayName("reports every violation at once rather than stopping at the first")
    void accumulatesViolations() {
        ChangeSet changeSet = threeChanges();
        NarratedSummary summary =
                new NarratedSummary(
                        "Partial",
                        List.of(bullet("Revenue changed in 12 places.", "CR-1", "CR-404")));

        ValidationResult result = validator.validate(changeSet, summary);

        assertThat(result.violations()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("accepts a number that genuinely appears in the change detail")
    void acceptsGroundedNumber() {
        ChangeSet changeSet = threeChanges();
        NarratedSummary summary =
                new NarratedSummary(
                        "Three changes",
                        List.of(
                                bullet("Revenue and cut-off testing changed.", "CR-1", "CR-2"),
                                bullet("Change detail for CR-3 was removed.", "CR-3")));

        ValidationResult result = validator.validate(changeSet, summary);

        assertThat(result.valid()).isTrue();
    }
}
