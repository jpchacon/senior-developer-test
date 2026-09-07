package com.caseware.templateupdate.domain;

/**
 * Identifies a product template, independently of any particular version of it.
 *
 * @param value the template identifier; never blank
 */
public record TemplateId(String value) {

    /**
     * Validates the identifier.
     *
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public TemplateId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
