package com.caseware.templateupdate.domain;

/**
 * Identifies one engagement file within a firm.
 *
 * @param value the engagement identifier; never blank
 */
public record EngagementId(String value) {

    /**
     * Validates the identifier.
     *
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public EngagementId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("engagementId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
