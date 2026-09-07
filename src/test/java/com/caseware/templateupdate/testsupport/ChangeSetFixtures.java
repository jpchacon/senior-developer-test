package com.caseware.templateupdate.testsupport;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.ChangeRecord;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.domain.SummaryBullet;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.List;

/** Shared test data, so each test reads as one sentence about the domain. */
public final class ChangeSetFixtures {

    public static final TemplateId TEMPLATE = new TemplateId("audit-ca");
    public static final TemplateVersion V3 = new TemplateVersion(3);
    public static final TemplateVersion V5 = new TemplateVersion(5);

    private ChangeSetFixtures() {}

    public static ChangeRecord record(String id, ChangeKind kind, String area, String path) {
        return ChangeRecord.builder()
                .id(id)
                .kind(kind)
                .area(area)
                .humanPath(path)
                .detail("detail for " + id)
                .build();
    }

    /** A change set with two changes in one area and one in another. */
    public static ChangeSet threeChanges() {
        return ChangeSet.builder()
                .templateId(TEMPLATE)
                .fromVersion(V3)
                .toVersion(V5)
                .record(record("CR-1", ChangeKind.ADDED, "Audit Procedures", "Revenue testing"))
                .record(record("CR-2", ChangeKind.MODIFIED, "Audit Procedures", "Cut-off testing"))
                .record(record("CR-3", ChangeKind.REMOVED, "Disclosures", "Going concern note"))
                .build();
    }

    public static SummaryBullet bullet(String text, String... citedIds) {
        return new SummaryBullet(text, List.of(citedIds));
    }

    /** A summary that cites every change and invents nothing. */
    public static NarratedSummary faithfulSummary() {
        return new NarratedSummary(
                "Three changes across two areas",
                List.of(
                        bullet("A revenue testing procedure was added and cut-off testing was reworded.", "CR-1", "CR-2"),
                        bullet("The going concern disclosure note was removed.", "CR-3")));
    }
}
