package com.caseware.templateupdate.domain;

import java.util.List;

/**
 * One sentence of a human-readable summary, together with the changes it describes.
 *
 * <p>The citations are the traceability mechanism: every bullet must point at real change
 * records, and every change record must be pointed at by some bullet. Without them a summary
 * could not be checked mechanically, only believed.
 *
 * @param text the sentence shown to the practitioner
 * @param citedChangeIds ids of the change records this sentence describes; never empty
 */
public record SummaryBullet(String text, List<String> citedChangeIds) {

    /**
     * Validates the bullet.
     *
     * @throws IllegalArgumentException if the text is blank or no change is cited
     */
    public SummaryBullet {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("summary bullet text must not be blank");
        }
        citedChangeIds = List.copyOf(citedChangeIds);
        if (citedChangeIds.isEmpty()) {
            throw new IllegalArgumentException("summary bullet must cite at least one change");
        }
    }
}
