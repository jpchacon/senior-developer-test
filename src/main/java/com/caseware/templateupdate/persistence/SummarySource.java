package com.caseware.templateupdate.persistence;

/** Records how a stored summary was produced, so trust in a row is auditable after the fact. */
public enum SummarySource {
    /** Produced by a language model and passed validation against the change set. */
    LLM_VALIDATED,
    /** Produced by the deterministic renderer because narration was unavailable or untrusted. */
    DETERMINISTIC_FALLBACK
}
