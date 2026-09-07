package com.caseware.templateupdate.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A null is not the same code path as a blank string, and both reach these constructors from real
 * sources — a missing JSON field and an empty one.
 */
@DisplayName("Value types reject nulls")
class NullRejectionTest {

    @Test
    @DisplayName("change records reject null identity and location")
    void changeRecordRejectsNulls() {
        assertThatThrownBy(() -> new ChangeRecord(null, ChangeKind.ADDED, "Area", "Path", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeRecord("CR-1", ChangeKind.ADDED, null, "Path", "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeRecord("CR-1", ChangeKind.ADDED, "Area", null, "d"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("summary text rejects nulls")
    void summaryTextRejectsNulls() {
        assertThatThrownBy(() -> new SummaryBullet(null, List.of("CR-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NarratedSummary(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("diff entries reject a null pointer")
    void diffEntryRejectsNullPointer() {
        assertThatThrownBy(() -> new JsonDiffEntry(null, ChangeKind.ADDED, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
