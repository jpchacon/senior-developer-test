package com.caseware.templateupdate.summary;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;

/**
 * Turns a structural diff into classified, human-locatable change records.
 *
 * <p>Deterministic by contract. This is the step that establishes what actually changed, and
 * nothing downstream is permitted to add to or subtract from its output.
 */
public interface ChangeClassifier {

    /**
     * Classifies a diff between two versions of one template.
     *
     * @param templateId the template being compared
     * @param fromVersion the older version
     * @param toVersion the newer version
     * @param diff the structural diff between them
     * @return the classified changes
     */
    ChangeSet classify(
            TemplateId templateId, TemplateVersion fromVersion, TemplateVersion toVersion, JsonDiff diff);
}
