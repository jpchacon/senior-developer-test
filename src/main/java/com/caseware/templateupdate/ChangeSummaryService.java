package com.caseware.templateupdate;

import com.caseware.templateupdate.cache.ChangeSummaryKey;
import com.caseware.templateupdate.cache.SummaryCache;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.summary.ChangeNarrator;
import com.caseware.templateupdate.summary.SummaryRenderer;
import com.caseware.templateupdate.summary.SummaryValidator;
import com.caseware.templateupdate.summary.ValidationResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces the summary a practitioner reads when deciding whether to apply a template update.
 *
 * <p>Composes the four collaborators that each answer a different question — what changed, how to
 * phrase it, whether that phrasing can be trusted, and what to show when it cannot — and depends
 * on all of them only as interfaces, so none of them needs to exist to test this orchestration.
 *
 * @implNote Only validated model output is cached. A summary produced by the deterministic
 *     fallback is returned but never stored, so a transient model outage degrades one request
 *     rather than permanently poisoning the cache with a plainer summary that would then be served
 *     to every firm on that version pair. Recorded here because it looks like an omission and is
 *     not one.
 */
public class ChangeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ChangeSummaryService.class);

    private final ChangeNarrator narrator;
    private final SummaryValidator validator;
    private final SummaryRenderer fallbackRenderer;
    private final SummaryCache cache;

    /**
     * Creates the service.
     *
     * @param narrator phrases a change set in readable language
     * @param validator checks that phrasing against the change set
     * @param fallbackRenderer produces a plain summary when narration cannot be trusted
     * @param cache stores validated summaries by template version pair
     */
    public ChangeSummaryService(
            ChangeNarrator narrator,
            SummaryValidator validator,
            SummaryRenderer fallbackRenderer,
            SummaryCache cache) {
        this.narrator = narrator;
        this.validator = validator;
        this.fallbackRenderer = fallbackRenderer;
        this.cache = cache;
    }

    /**
     * Summarises a change set, reusing a cached summary when one exists.
     *
     * <p>Never throws on narration failure: a practitioner waiting on an apply or decline decision
     * is shown the deterministic rendering instead.
     *
     * @param changeSet the classified changes to summarise
     * @return a summary safe to show, whether narrated or deterministic
     */
    public NarratedSummary summarize(ChangeSet changeSet) {
        ChangeSummaryKey key = ChangeSummaryKey.of(changeSet);

        Optional<NarratedSummary> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        return narrateAndValidate(changeSet)
                .map(
                        summary -> {
                            cache.put(key, summary);
                            return summary;
                        })
                .orElseGet(() -> fallbackRenderer.render(changeSet));
    }

    /** Returns the narrated summary only if it survives validation. */
    private Optional<NarratedSummary> narrateAndValidate(ChangeSet changeSet) {
        NarratedSummary narrated;
        try {
            narrated = narrator.narrate(changeSet);
        } catch (RuntimeException failure) {
            log.warn("Narration failed for {}; falling back to deterministic rendering",
                    ChangeSummaryKey.of(changeSet), failure);
            return Optional.empty();
        }

        ValidationResult result = validator.validate(changeSet, narrated);
        if (!result.valid()) {
            log.warn("Narration rejected for {}: {}", ChangeSummaryKey.of(changeSet), result.violations());
            return Optional.empty();
        }
        return Optional.of(narrated);
    }
}
