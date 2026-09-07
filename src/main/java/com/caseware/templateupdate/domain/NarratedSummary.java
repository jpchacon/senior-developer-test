package com.caseware.templateupdate.domain;

import java.util.List;

/**
 * A human-readable account of what changed between two template versions.
 *
 * <p>Shown to a non-technical practitioner to support an apply or decline decision. It never
 * carries a recommendation: whether to accept an update is professional judgement, not something
 * the system decides.
 *
 * @param headline a one-line orientation, such as "12 changes across 3 areas"
 * @param bullets the individual statements, each traceable to change records
 */
public record NarratedSummary(String headline, List<SummaryBullet> bullets) {

    /**
     * Validates the summary.
     *
     * @throws IllegalArgumentException if the headline is blank
     */
    public NarratedSummary {
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("summary headline must not be blank");
        }
        bullets = List.copyOf(bullets);
    }

    /**
     * Lists every change id cited anywhere in this summary.
     *
     * @return the cited change record ids
     */
    public List<String> allCitedChangeIds() {
        return bullets.stream().flatMap(bullet -> bullet.citedChangeIds().stream()).toList();
    }
}
