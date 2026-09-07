package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;

/**
 * Phrases an already-classified set of changes in language a practitioner can read.
 *
 * <p>This is the only seam in the system where a language model belongs, and its remit is
 * deliberately narrow: it chooses wording, nothing else. It does not decide whether an update
 * exists, what changed, or whether the update should be accepted.
 *
 * <p>Implementations must either return a summary or throw {@link NarrationException}. Honouring
 * that contract is what allows a stub, a deliberately failing stub and a real model client to be
 * swapped for one another without the caller noticing.
 */
public interface ChangeNarrator {

    /**
     * Narrates a change set.
     *
     * @param changeSet the classified changes to describe
     * @return a human-readable summary, which the caller is expected to validate before use
     * @throws NarrationException if no summary could be produced
     */
    NarratedSummary narrate(ChangeSet changeSet);
}
