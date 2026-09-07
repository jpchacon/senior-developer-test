package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.ChangeRecord;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.domain.SummaryBullet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a change set as plain grouped statements, with no model involved.
 *
 * <p>Plainer than a narrated summary, and entirely trustworthy: it can only restate the records it
 * was given. This is what a practitioner sees when the model is unavailable or its output failed
 * validation, and it is also what shipped before any model was added at all — the product is
 * useful without AI, which is the point.
 */
public class DeterministicChangeRenderer implements SummaryRenderer {

    /** Creates a renderer. It holds no state, so a single instance can be shared freely. */
    public DeterministicChangeRenderer() {
        // Stateless by design.
    }

    @Override
    public NarratedSummary render(ChangeSet changeSet) {
        Map<String, List<ChangeRecord>> byArea = changeSet.groupedByArea();

        List<SummaryBullet> bullets = new ArrayList<>();
        byArea.forEach((area, records) -> bullets.add(bulletFor(area, records)));

        return new NarratedSummary(headlineFor(changeSet, byArea.size()), bullets);
    }

    private String headlineFor(ChangeSet changeSet, int areaCount) {
        if (changeSet.isEmpty()) {
            return "No changes between " + changeSet.fromVersion() + " and " + changeSet.toVersion();
        }
        return "%d change%s across %d area%s, %s to %s"
                .formatted(
                        changeSet.records().size(),
                        changeSet.records().size() == 1 ? "" : "s",
                        areaCount,
                        areaCount == 1 ? "" : "s",
                        changeSet.fromVersion(),
                        changeSet.toVersion());
    }

    private SummaryBullet bulletFor(String area, List<ChangeRecord> records) {
        StringBuilder text = new StringBuilder(area).append(": ");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                text.append("; ");
            }
            ChangeRecord record = records.get(i);
            text.append(verbFor(record.kind())).append(' ').append(record.humanPath());
        }
        return new SummaryBullet(text.toString(), records.stream().map(ChangeRecord::id).toList());
    }

    private String verbFor(ChangeKind kind) {
        return switch (kind) {
            case ADDED -> "added";
            case REMOVED -> "removed";
            case MODIFIED -> "changed";
        };
    }
}
