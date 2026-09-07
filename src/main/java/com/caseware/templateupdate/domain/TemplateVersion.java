package com.caseware.templateupdate.domain;

/**
 * A published version of a product template.
 *
 * <p>Versions are immutable once published, which is what makes a change summary for a given
 * version pair cacheable forever.
 *
 * @param number the version number; always positive
 */
public record TemplateVersion(int number) implements Comparable<TemplateVersion> {

    /**
     * Validates the version number.
     *
     * @throws IllegalArgumentException if {@code number} is not positive
     */
    public TemplateVersion {
        if (number < 1) {
            throw new IllegalArgumentException("template version must be positive, was " + number);
        }
    }

    /**
     * Reports whether this version was published after another.
     *
     * @param other the version to compare against
     * @return {@code true} if this version is strictly newer than {@code other}
     */
    public boolean isAfter(TemplateVersion other) {
        return number > other.number;
    }

    /**
     * Reports whether this version was published before another.
     *
     * @param other the version to compare against
     * @return {@code true} if this version is strictly older than {@code other}
     */
    public boolean isBefore(TemplateVersion other) {
        return number < other.number;
    }

    @Override
    public int compareTo(TemplateVersion other) {
        return Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return "v" + number;
    }
}
