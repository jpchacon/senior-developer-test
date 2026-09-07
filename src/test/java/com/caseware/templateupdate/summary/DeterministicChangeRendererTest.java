package com.caseware.templateupdate.summary;

import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.TEMPLATE;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.V3;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.V5;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.record;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.threeChanges;
import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeterministicChangeRenderer")
class DeterministicChangeRendererTest {

    private final DeterministicChangeRenderer renderer = new DeterministicChangeRenderer();
    private final SummaryValidator validator = new SummaryValidator();

    @Test
    @DisplayName("covers every change, so its output always passes validation")
    void alwaysPassesValidation() {
        ChangeSet changeSet = threeChanges();

        ValidationResult result = validator.validate(changeSet, renderer.render(changeSet));

        assertThat(result.valid())
                .as("the fallback must be safe to show without further checking")
                .isTrue();
    }

    @Test
    @DisplayName("counts changes and areas in the headline")
    void summarisesCounts() {
        NarratedSummary summary = renderer.render(threeChanges());

        assertThat(summary.headline()).isEqualTo("3 changes across 2 areas, v3 to v5");
        assertThat(summary.bullets()).hasSize(2);
    }

    @Test
    @DisplayName("uses singular wording for a single change in a single area")
    void usesSingularWording() {
        ChangeSet changeSet =
                new ChangeSet(
                        TEMPLATE, V3, V5, List.of(record("CR-1", ChangeKind.ADDED, "Procedures", "One")));

        assertThat(renderer.render(changeSet).headline())
                .isEqualTo("1 change across 1 area, v3 to v5");
    }

    @Test
    @DisplayName("says plainly when nothing changed")
    void handlesEmptyChangeSet() {
        ChangeSet empty = new ChangeSet(TEMPLATE, V3, V5, List.of());

        NarratedSummary summary = renderer.render(empty);

        assertThat(summary.headline()).isEqualTo("No changes between v3 and v5");
        assertThat(summary.bullets()).isEmpty();
    }

    @Test
    @DisplayName("uses the right verb for each kind of change")
    void describesEveryChangeKind() {
        ChangeSet changeSet =
                new ChangeSet(
                        TEMPLATE,
                        V3,
                        V5,
                        List.of(
                                record("CR-1", ChangeKind.ADDED, "Procedures", "Added one"),
                                record("CR-2", ChangeKind.REMOVED, "Procedures", "Removed one"),
                                record("CR-3", ChangeKind.MODIFIED, "Procedures", "Changed one")));

        String bullet = renderer.render(changeSet).bullets().get(0).text();

        assertThat(bullet)
                .isEqualTo("Procedures: added Added one; removed Removed one; changed Changed one");
    }
}
