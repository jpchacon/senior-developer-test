package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;

/**
 * Produces a summary without consulting any external service.
 *
 * <p>Kept separate from {@link ChangeNarrator} because the two make different promises: a renderer
 * cannot fail and cannot fabricate, while a narrator can do both. That difference is exactly why
 * a renderer is a usable fallback and a second narrator would not be.
 */
public interface SummaryRenderer {

    /**
     * Renders a change set directly from its records.
     *
     * @param changeSet the classified changes to describe
     * @return a summary that always covers every change and states nothing beyond them
     */
    NarratedSummary render(ChangeSet changeSet);
}
