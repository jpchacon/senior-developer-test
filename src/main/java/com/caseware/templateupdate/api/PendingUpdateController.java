package com.caseware.templateupdate.api;

import com.caseware.templateupdate.domain.TemplateId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the question the whole system exists for: which of a firm's engagement files have
 * pending template updates, and what is in them.
 *
 * <p>Deliberately one endpoint. Apply and decline are the engagement management system's to
 * handle, and a detail view would add surface area without adding an architectural argument.
 */
@RestController
public class PendingUpdateController {

    private final PendingUpdateQueryService queryService;

    /**
     * Creates the controller.
     *
     * @param queryService assembles the pending update rows
     */
    public PendingUpdateController(PendingUpdateQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Lists the engagements of one firm that have a pending template update.
     *
     * @param firmId the firm whose engagements to list
     * @param templateId the product template to check
     * @param latestVersion the newest published version of that template
     * @return one row per engagement with something pending; engagements that are current are
     *     omitted rather than returned with an empty update
     */
    @GetMapping("/api/firms/{firmId}/engagements/pending-updates")
    public List<PendingUpdateView> listPendingUpdates(
            @PathVariable String firmId,
            @RequestParam String templateId,
            @RequestParam int latestVersion) {
        return queryService.findPendingUpdates(new TemplateId(templateId), latestVersion);
    }
}
