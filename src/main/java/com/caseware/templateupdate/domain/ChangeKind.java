package com.caseware.templateupdate.domain;

/** The nature of a single change between two template versions. */
public enum ChangeKind {
    /** Content present in the newer version and absent from the older one. */
    ADDED,
    /** Content present in the older version and absent from the newer one. */
    REMOVED,
    /** Content present in both versions but with a different value. */
    MODIFIED
}
