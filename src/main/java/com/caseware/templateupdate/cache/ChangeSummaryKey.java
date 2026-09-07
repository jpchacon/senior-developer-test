package com.caseware.templateupdate.cache;

import com.caseware.templateupdate.domain.ChangeSet;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import lombok.Builder;

/**
 * Identifies a cached change summary.
 *
 * <p>There is intentionally no firm or engagement component. Template versions are immutable, so
 * an entry under this key stays correct indefinitely, and it is safe to share across customers
 * because it describes template content only.
 *
 * @param templateId the template compared
 * @param fromVersion the older version
 * @param toVersion the newer version
 * @implNote Built through {@code ChangeSummaryKey.builder()}: {@code fromVersion} and
 *     {@code toVersion} share a type, so positional construction is a swap waiting to happen.
 */
@Builder
public record ChangeSummaryKey(
        TemplateId templateId, TemplateVersion fromVersion, TemplateVersion toVersion) {

    /**
     * Derives the key for a change set.
     *
     * @param changeSet the change set to key
     * @return the cache key identifying it
     */
    public static ChangeSummaryKey of(ChangeSet changeSet) {
        return ChangeSummaryKey.builder()
                .templateId(changeSet.templateId())
                .fromVersion(changeSet.fromVersion())
                .toVersion(changeSet.toVersion())
                .build();
    }
}
