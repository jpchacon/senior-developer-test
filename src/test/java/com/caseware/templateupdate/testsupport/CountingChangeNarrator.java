package com.caseware.templateupdate.testsupport;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.summary.ChangeNarrator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** A narrator that records how often it was asked, so caching can be asserted rather than assumed. */
public final class CountingChangeNarrator implements ChangeNarrator {

    private final AtomicInteger calls = new AtomicInteger();
    private final Function<ChangeSet, NarratedSummary> response;

    public CountingChangeNarrator(Function<ChangeSet, NarratedSummary> response) {
        this.response = response;
    }

    public static CountingChangeNarrator returningFaithfulSummary() {
        return new CountingChangeNarrator(changeSet -> ChangeSetFixtures.faithfulSummary());
    }

    @Override
    public NarratedSummary narrate(ChangeSet changeSet) {
        calls.incrementAndGet();
        return response.apply(changeSet);
    }

    public int callCount() {
        return calls.get();
    }
}
