package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeRecord;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.domain.SummaryBullet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks that a narrated summary faithfully describes the changes it was given.
 *
 * <p>This is the guard that lets a language model near an audit workflow at all. The model is
 * free to choose wording, but it cannot introduce a change that is not in the diff, quietly drop
 * one that is, or state a figure that has no basis in the underlying content. Each of those is a
 * separate rule below, and any of them failing sends the caller to the deterministic fallback.
 *
 * <p>Every check is mechanical. Nothing here asks a model to judge another model's output.
 */
public class SummaryValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    /** Creates a validator. It holds no state, so a single instance can be shared freely. */
    public SummaryValidator() {
        // Stateless by design.
    }

    /**
     * Validates a summary against its change set.
     *
     * @param changeSet the deterministic ground truth
     * @param summary the narrated summary to check
     * @return a passing result, or a failing one listing every violation found
     */
    public ValidationResult validate(ChangeSet changeSet, NarratedSummary summary) {
        List<String> violations = new ArrayList<>();
        violations.addAll(findDanglingCitations(changeSet, summary));
        violations.addAll(findUncoveredChanges(changeSet, summary));
        violations.addAll(findUnsupportedNumbers(changeSet, summary));

        return violations.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(violations);
    }

    /** Finds citations pointing at changes that do not exist: the signature of a fabrication. */
    private List<String> findDanglingCitations(ChangeSet changeSet, NarratedSummary summary) {
        Set<String> known = changeSet.recordIds();
        return summary.allCitedChangeIds().stream()
                .filter(cited -> !known.contains(cited))
                .distinct()
                .map(cited -> "summary cites unknown change " + cited)
                .toList();
    }

    /** Finds changes no bullet mentions, so an update cannot be silently under-reported. */
    private List<String> findUncoveredChanges(ChangeSet changeSet, NarratedSummary summary) {
        Set<String> cited = new LinkedHashSet<>(summary.allCitedChangeIds());
        return changeSet.records().stream()
                .map(ChangeRecord::id)
                .filter(id -> !cited.contains(id))
                .map(id -> "summary omits change " + id)
                .toList();
    }

    /**
     * Finds figures in the prose with no basis in the change set.
     *
     * <p>Numbers are singled out because they are both the most consequential thing a model can
     * invent in this domain and the easiest to check: a figure is acceptable only if it appears
     * somewhere in the underlying change records, or is a count the summary is entitled to make.
     */
    private List<String> findUnsupportedNumbers(ChangeSet changeSet, NarratedSummary summary) {
        String groundTruth = groundTruthText(changeSet);
        // A set literal will not do here: these values legitimately collide, for instance when a
        // change set happens to hold as many changes as its from-version number.
        Set<String> allowedCounts =
                Stream.of(
                                changeSet.records().size(),
                                changeSet.groupedByArea().size(),
                                changeSet.fromVersion().number(),
                                changeSet.toVersion().number())
                        .map(String::valueOf)
                        .collect(Collectors.toSet());

        List<String> violations = new ArrayList<>();
        for (SummaryBullet bullet : summary.bullets()) {
            Matcher matcher = NUMBER.matcher(bullet.text());
            while (matcher.find()) {
                String number = matcher.group();
                if (!allowedCounts.contains(number) && !groundTruth.contains(number)) {
                    violations.add("summary states unsupported figure " + number);
                }
            }
        }
        return violations;
    }

    /** Everything the summary is allowed to draw on, flattened for a containment check. */
    private String groundTruthText(ChangeSet changeSet) {
        StringBuilder builder = new StringBuilder();
        for (ChangeRecord record : changeSet.records()) {
            builder.append(record.id())
                    .append(' ')
                    .append(record.area())
                    .append(' ')
                    .append(record.humanPath())
                    .append(' ')
                    .append(record.detail())
                    .append('\n');
        }
        return builder.toString();
    }
}
