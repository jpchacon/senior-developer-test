package com.caseware.templateupdate.persistence;

import com.caseware.templateupdate.domain.EngagementId;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.List;
import java.util.Optional;

/** Stores one firm's projected engagement template state. */
public interface EngagementStateRepository {

    /**
     * Looks up the projected state of one engagement.
     *
     * @param engagementId the engagement to find
     * @return its state, or empty if no event has been projected for it yet
     */
    Optional<EngagementTemplateState> findById(EngagementId engagementId);

    /**
     * Stores an engagement's state, ignoring writes that would move it backwards.
     *
     * <p>Idempotent, because events can be redelivered or arrive out of order.
     *
     * @param state the state to store
     */
    void save(EngagementTemplateState state);

    /**
     * Lists the versions of a template that engagements are actually sitting on.
     *
     * <p>This is what bounds precomputation after a publish: summaries are needed only for the
     * versions in use, not for every pair of versions that exists.
     *
     * @param templateId the template to inspect
     * @return the distinct versions in use, ascending
     */
    List<TemplateVersion> findDistinctVersionsInUse(TemplateId templateId);

    /**
     * Lists every engagement built from a given template.
     *
     * @param templateId the template to inspect
     * @return the projected states, ordered by engagement id
     */
    List<EngagementTemplateState> findAllByTemplate(TemplateId templateId);
}
