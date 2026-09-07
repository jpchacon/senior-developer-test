package com.caseware.templateupdate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.ChangeSummaryService;
import com.caseware.templateupdate.cache.InMemorySummaryCache;
import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.EngagementId;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.JsonDiffEntry;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import com.caseware.templateupdate.persistence.EngagementStateRepository;
import com.caseware.templateupdate.resolver.PendingUpdateResolver;
import com.caseware.templateupdate.summary.DeterministicChangeRenderer;
import com.caseware.templateupdate.summary.PathBasedChangeClassifier;
import com.caseware.templateupdate.summary.SummaryValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PendingUpdateQueryService")
class PendingUpdateQueryServiceTest {

    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");

    /** An in-memory stand-in, so the read path can be tested without a database. */
    private static final class InMemoryEngagementStates implements EngagementStateRepository {
        private final List<EngagementTemplateState> states = new ArrayList<>();

        @Override
        public Optional<EngagementTemplateState> findById(EngagementId engagementId) {
            return states.stream().filter(s -> s.engagementId().equals(engagementId)).findFirst();
        }

        @Override
        public void save(EngagementTemplateState state) {
            states.add(state);
        }

        @Override
        public List<TemplateVersion> findDistinctVersionsInUse(TemplateId templateId) {
            return states.stream().map(EngagementTemplateState::currentVersion).distinct().sorted().toList();
        }

        @Override
        public List<EngagementTemplateState> findAllByTemplate(TemplateId templateId) {
            return states.stream().filter(s -> s.templateId().equals(templateId)).toList();
        }
    }

    private final InMemoryEngagementStates engagements = new InMemoryEngagementStates();

    private final PendingUpdateQueryService service =
            new PendingUpdateQueryService(
                    engagements,
                    new PendingUpdateResolver(),
                    new ChangeSummaryService(
                            new DeterministicChangeRenderer()::render,
                            new SummaryValidator(),
                            new DeterministicChangeRenderer(),
                            new InMemorySummaryCache()),
                    new PathBasedChangeClassifier(),
                    (templateId, from, to) ->
                            new JsonDiff(
                                    List.of(
                                            new JsonDiffEntry(
                                                    "/audit_procedures/1/title",
                                                    ChangeKind.MODIFIED,
                                                    "Old",
                                                    "New"))));

    private void given(String id, int version, Integer... declined) {
        engagements.save(
                new EngagementTemplateState(
                        new EngagementId(id),
                        TEMPLATE,
                        new TemplateVersion(version),
                        Set.of(declined).stream()
                                .map(TemplateVersion::new)
                                .collect(java.util.stream.Collectors.toSet())));
    }

    @Test
    @DisplayName("omits engagements that are already on the latest version")
    void omitsCurrentEngagements() {
        given("eng-current", 5);
        given("eng-behind", 3);

        List<PendingUpdateView> pending = service.findPendingUpdates(TEMPLATE, 5);

        assertThat(pending).extracting(PendingUpdateView::engagementId).containsExactly("eng-behind");
    }

    @Test
    @DisplayName("reports how far behind an engagement is, with a readable summary")
    void reportsRangeAndSummary() {
        given("eng-1", 3);

        PendingUpdateView view = service.findPendingUpdates(TEMPLATE, 6).get(0);

        assertThat(view.currentVersion()).isEqualTo(3);
        assertThat(view.latestVersion()).isEqualTo(6);
        assertThat(view.versionsBehind()).isEqualTo(3);
        assertThat(view.summaryHeadline()).isNotBlank();
        assertThat(view.summaryPoints()).isNotEmpty();
    }

    @Test
    @DisplayName("surfaces versions the user declined earlier without skipping their content")
    void surfacesPreviousDeclines() {
        given("eng-1", 3, 4);

        PendingUpdateView view = service.findPendingUpdates(TEMPLATE, 5).get(0);

        assertThat(view.currentVersion())
                .as("declining v4 defers it; the pending range still starts at v3")
                .isEqualTo(3);
        assertThat(view.previouslyDeclined()).containsExactly(4);
    }

    @Test
    @DisplayName("returns nothing when the firm has no engagements on that template")
    void returnsNothingForUnknownTemplate() {
        assertThat(service.findPendingUpdates(new TemplateId("unknown"), 9)).isEmpty();
    }
}
