package com.caseware.templateupdate.domain;

import java.util.List;

/**
 * A structural diff between two versions of one product template.
 *
 * @param entries the individual changes; may be empty but never null
 */
public record JsonDiff(List<JsonDiffEntry> entries) {

    /** Takes a defensive copy of the entries. */
    public JsonDiff {
        entries = List.copyOf(entries);
    }
}
