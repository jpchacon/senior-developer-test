package com.caseware.templateupdate;

import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.bullet;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.threeChanges;
import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.cache.ChangeSummaryKey;
import com.caseware.templateupdate.cache.InMemorySummaryCache;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.summary.DeterministicChangeRenderer;
import com.caseware.templateupdate.summary.SummaryValidator;
import com.caseware.templateupdate.testsupport.CountingChangeNarrator;
import com.caseware.templateupdate.testsupport.FailingChangeNarrator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChangeSummaryService")
class ChangeSummaryServiceTest {

    private final SummaryValidator validator = new SummaryValidator();
    private final DeterministicChangeRenderer renderer = new DeterministicChangeRenderer();
    private final InMemorySummaryCache cache = new InMemorySummaryCache();

    @Test
    @DisplayName("returns the narrated summary when it passes validation")
    void returnsValidatedNarration() {
        CountingChangeNarrator narrator = CountingChangeNarrator.returningFaithfulSummary();
        ChangeSummaryService service =
                new ChangeSummaryService(narrator, validator, renderer, cache);

        NarratedSummary summary = service.summarize(threeChanges());

        assertThat(summary.headline()).isEqualTo("Three changes across two areas");
    }

    @Test
    @DisplayName("falls back to the deterministic rendering when the model is unavailable")
    void fallsBackWhenNarrationThrows() {
        ChangeSummaryService service =
                new ChangeSummaryService(new FailingChangeNarrator(), validator, renderer, cache);

        NarratedSummary summary = service.summarize(threeChanges());

        assertThat(summary.headline()).contains("3 changes across 2 areas");
        assertThat(summary.bullets()).isNotEmpty();
    }

    @Test
    @DisplayName("falls back when the model invents a change that is not in the diff")
    void fallsBackWhenNarrationFailsValidation() {
        CountingChangeNarrator hallucinating =
                new CountingChangeNarrator(
                        changeSet ->
                                new NarratedSummary(
                                        "Invented",
                                        List.of(bullet("A materiality threshold changed.", "CR-999"))));
        ChangeSummaryService service =
                new ChangeSummaryService(hallucinating, validator, renderer, cache);

        NarratedSummary summary = service.summarize(threeChanges());

        assertThat(summary.headline()).contains("3 changes across 2 areas");
    }

    @Test
    @DisplayName("summarises a version pair once however many engagements ask for it")
    void summarisesEachVersionPairOnlyOnce() {
        CountingChangeNarrator narrator = CountingChangeNarrator.returningFaithfulSummary();
        ChangeSummaryService service =
                new ChangeSummaryService(narrator, validator, renderer, cache);
        ChangeSet shared = threeChanges();

        service.summarize(shared);
        service.summarize(shared);
        service.summarize(threeChanges());

        assertThat(narrator.callCount())
                .as("the diff is firm-independent, so it is narrated once and reused")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("does not cache a fallback summary, so a model outage is not made permanent")
    void doesNotCacheFallbackOutput() {
        ChangeSummaryService failing =
                new ChangeSummaryService(new FailingChangeNarrator(), validator, renderer, cache);
        failing.summarize(threeChanges());

        assertThat(cache.get(ChangeSummaryKey.of(threeChanges()))).isEmpty();

        CountingChangeNarrator recovered = CountingChangeNarrator.returningFaithfulSummary();
        ChangeSummaryService healthy =
                new ChangeSummaryService(recovered, validator, renderer, cache);

        assertThat(healthy.summarize(threeChanges()).headline())
                .isEqualTo("Three changes across two areas");
    }
}
