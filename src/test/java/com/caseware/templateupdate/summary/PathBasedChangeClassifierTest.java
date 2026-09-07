package com.caseware.templateupdate.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.ChangeRecord;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.JsonDiffEntry;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PathBasedChangeClassifier")
class PathBasedChangeClassifierTest {

    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");
    private static final TemplateVersion V3 = new TemplateVersion(3);
    private static final TemplateVersion V5 = new TemplateVersion(5);

    private final PathBasedChangeClassifier classifier = new PathBasedChangeClassifier();

    private static JsonDiff diffOf(JsonDiffEntry... entries) {
        return new JsonDiff(List.of(entries));
    }

    private ChangeSet classify(JsonDiff diff) {
        return classifier.classify(TEMPLATE, V3, V5, diff);
    }

    @Test
    @DisplayName("names the area from the first path segment, in the user's language")
    void derivesHumanReadableArea() {
        ChangeSet changeSet =
                classify(
                        diffOf(
                                new JsonDiffEntry(
                                        "/audit_procedures/3/title", ChangeKind.MODIFIED, "Old", "New")));

        ChangeRecord record = changeSet.records().get(0);
        assertThat(record.area()).isEqualTo("Audit procedures");
        assertThat(record.humanPath()).isEqualTo("Title");
        assertThat(record.detail()).isEqualTo("Changed from \"Old\" to \"New\"");
    }

    @Test
    @DisplayName("groups changes that share an area")
    void groupsByArea() {
        ChangeSet changeSet =
                classify(
                        diffOf(
                                new JsonDiffEntry("/procedures/1/title", ChangeKind.ADDED, null, "A"),
                                new JsonDiffEntry("/procedures/2/title", ChangeKind.REMOVED, "B", null),
                                new JsonDiffEntry("/disclosures/1/note", ChangeKind.ADDED, null, "C")));

        assertThat(changeSet.groupedByArea()).containsOnlyKeys("Procedures", "Disclosures");
    }

    @Test
    @DisplayName("gives the same diff the same identifiers every time")
    void producesStableIdentifiers() {
        JsonDiff diff =
                diffOf(new JsonDiffEntry("/procedures/1/title", ChangeKind.ADDED, null, "A"));

        assertThat(classify(diff).recordIds()).isEqualTo(classify(diff).recordIds());
    }

    @Test
    @DisplayName("describes additions and removals in their own terms")
    void describesEachKind() {
        ChangeSet changeSet =
                classify(
                        diffOf(
                                new JsonDiffEntry("/procedures/1/title", ChangeKind.ADDED, null, "New one"),
                                new JsonDiffEntry("/procedures/2/title", ChangeKind.REMOVED, "Old one", null)));

        assertThat(changeSet.records().get(0).detail()).isEqualTo("Added: New one");
        assertThat(changeSet.records().get(1).detail()).isEqualTo("Removed: Old one");
    }

    @Test
    @DisplayName("falls back to the area when the path has nothing more specific")
    void handlesShallowAndIndexOnlyPaths() {
        ChangeSet changeSet =
                classify(
                        diffOf(
                                new JsonDiffEntry("/methodology", ChangeKind.MODIFIED, "a", "b"),
                                new JsonDiffEntry("/procedures/7", ChangeKind.ADDED, null, "x")));

        assertThat(changeSet.records().get(0).humanPath()).isEqualTo("Methodology");
        assertThat(changeSet.records().get(1).humanPath()).isEqualTo("Procedures");
    }

    @Test
    @DisplayName("labels a change with no usable path as other content")
    void handlesRootPath() {
        ChangeSet changeSet = classify(diffOf(new JsonDiffEntry("/", ChangeKind.MODIFIED, "a", "b")));

        assertThat(changeSet.records().get(0).area()).isEqualTo("Other content");
    }

    @Test
    @DisplayName("produces an empty change set for identical versions")
    void handlesEmptyDiff() {
        assertThat(classify(diffOf()).isEmpty()).isTrue();
    }
}
