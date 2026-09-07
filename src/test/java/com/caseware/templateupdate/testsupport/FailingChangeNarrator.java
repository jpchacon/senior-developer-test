package com.caseware.templateupdate.testsupport;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.summary.ChangeNarrator;
import com.caseware.templateupdate.summary.NarrationException;

/** A narrator that always fails, standing in for an unavailable model. */
public final class FailingChangeNarrator implements ChangeNarrator {

    @Override
    public NarratedSummary narrate(ChangeSet changeSet) {
        throw new NarrationException("model unavailable");
    }
}
