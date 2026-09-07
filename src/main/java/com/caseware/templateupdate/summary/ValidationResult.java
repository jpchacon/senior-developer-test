package com.caseware.templateupdate.summary;

import java.util.List;

/**
 * The outcome of checking a narrated summary against the changes it claims to describe.
 *
 * @param valid whether the summary may be shown to a practitioner
 * @param violations every problem found, so they can be logged and fixed together
 */
public record ValidationResult(boolean valid, List<String> violations) {

    /** Takes a defensive copy of the violations. */
    public ValidationResult {
        violations = List.copyOf(violations);
    }

    /**
     * Creates a passing result.
     *
     * @return a valid result with no violations
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Creates a failing result.
     *
     * @param violations the problems found; must not be empty
     * @return an invalid result carrying every violation
     */
    public static ValidationResult failed(List<String> violations) {
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("a failed validation must report a violation");
        }
        return new ValidationResult(false, violations);
    }
}
