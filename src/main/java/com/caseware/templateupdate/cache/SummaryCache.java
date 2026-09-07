package com.caseware.templateupdate.cache;

import com.caseware.templateupdate.domain.NarratedSummary;
import java.util.Optional;

/** Stores computed change summaries so each template version pair is summarised only once. */
public interface SummaryCache {

    /**
     * Looks up a previously computed summary.
     *
     * @param key the version pair to look up
     * @return the stored summary, or empty if this pair has not been summarised
     */
    Optional<NarratedSummary> get(ChangeSummaryKey key);

    /**
     * Stores a summary.
     *
     * @param key the version pair the summary describes
     * @param summary the summary to store
     */
    void put(ChangeSummaryKey key, NarratedSummary summary);
}
