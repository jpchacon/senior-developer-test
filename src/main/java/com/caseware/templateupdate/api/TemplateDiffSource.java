package com.caseware.templateupdate.api;

import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;

/**
 * Supplies the structural diff between two versions of a product template.
 *
 * <p>The spec grants a fast, reliable way to diff two template versions; this port stands in for
 * it so the read path can be built and tested without that system present.
 */
public interface TemplateDiffSource {

    /**
     * Diffs two versions of one template.
     *
     * @param templateId the template to compare
     * @param fromVersion the older version
     * @param toVersion the newer version
     * @return the structural diff between them
     */
    JsonDiff diff(TemplateId templateId, TemplateVersion fromVersion, TemplateVersion toVersion);
}
