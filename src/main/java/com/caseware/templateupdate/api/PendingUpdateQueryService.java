package com.caseware.templateupdate.api;

import com.caseware.templateupdate.ChangeSummaryService;
import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.domain.PendingUpdate;
import com.caseware.templateupdate.domain.SummaryBullet;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import com.caseware.templateupdate.persistence.EngagementStateRepository;
import com.caseware.templateupdate.resolver.PendingUpdateResolver;
import com.caseware.templateupdate.summary.ChangeClassifier;
import com.caseware.templateupdate.domain.JsonDiff;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles the pending-updates list from the projected engagement state and the shared summaries.
 *
 * <p>The read path never loads an engagement file. It reads the projection, resolves the pending
 * range, and joins to a summary that was computed once for the whole customer base.
 */
@Service
public class PendingUpdateQueryService {

    private final EngagementStateRepository engagementStates;
    private final PendingUpdateResolver resolver;
    private final ChangeSummaryService summaries;
    private final ChangeClassifier classifier;
    private final TemplateDiffSource diffSource;

    /**
     * Creates the query service.
     *
     * @param engagementStates the tenant's projected engagement state
     * @param resolver decides the pending version range
     * @param summaries produces or reuses the change summary
     * @param classifier turns a diff into change records
     * @param diffSource supplies the diff between two template versions
     */
    public PendingUpdateQueryService(
            EngagementStateRepository engagementStates,
            PendingUpdateResolver resolver,
            ChangeSummaryService summaries,
            ChangeClassifier classifier,
            TemplateDiffSource diffSource) {
        this.engagementStates = engagementStates;
        this.resolver = resolver;
        this.summaries = summaries;
        this.classifier = classifier;
        this.diffSource = diffSource;
    }

    /**
     * Finds every engagement of this tenant with a pending update to the given template.
     *
     * @param templateId the template to check
     * @param latestVersionNumber the newest published version
     * @return one view per engagement with something pending
     */
    public List<PendingUpdateView> findPendingUpdates(TemplateId templateId, int latestVersionNumber) {
        TemplateVersion latest = new TemplateVersion(latestVersionNumber);

        return engagementStates.findAllByTemplate(templateId).stream()
                .map(state -> toView(state, latest))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PendingUpdateView> toView(EngagementTemplateState state, TemplateVersion latest) {
        return resolver.resolve(state, latest).map(update -> render(update, summaryFor(update)));
    }

    private NarratedSummary summaryFor(PendingUpdate update) {
        JsonDiff diff =
                diffSource.diff(update.templateId(), update.fromVersion(), update.toVersion());
        ChangeSet changeSet =
                classifier.classify(
                        update.templateId(), update.fromVersion(), update.toVersion(), diff);
        return summaries.summarize(changeSet);
    }

    private PendingUpdateView render(PendingUpdate update, NarratedSummary summary) {
        return PendingUpdateView.builder()
                .engagementId(update.engagementId().value())
                .templateId(update.templateId().value())
                .currentVersion(update.fromVersion().number())
                .latestVersion(update.toVersion().number())
                .versionsBehind(update.versionsBehind())
                .previouslyDeclined(update.declinedInRange().stream().map(TemplateVersion::number).toList())
                .summaryPoints(summary.bullets().stream().map(SummaryBullet::text).toList())
                .summaryHeadline(summary.headline())
                .build();
    }
}
