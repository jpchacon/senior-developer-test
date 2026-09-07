package com.caseware.templateupdate.cache;

import com.caseware.templateupdate.domain.NarratedSummary;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A process-local cache, used in tests and for local runs without a database.
 *
 * <p>The durable implementation is the PostgreSQL-backed one; this exists so the summarisation
 * logic can be exercised without any infrastructure at all.
 */
public class InMemorySummaryCache implements SummaryCache {

    private final Map<ChangeSummaryKey, NarratedSummary> entries = new ConcurrentHashMap<>();

    /** Creates an empty cache. */
    public InMemorySummaryCache() {
        // Nothing to configure.
    }

    @Override
    public Optional<NarratedSummary> get(ChangeSummaryKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public void put(ChangeSummaryKey key, NarratedSummary summary) {
        entries.put(key, summary);
    }
}
