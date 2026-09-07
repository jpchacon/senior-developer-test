package com.caseware.templateupdate.config;

import com.caseware.templateupdate.ChangeSummaryService;
import com.caseware.templateupdate.cache.SummaryCache;
import com.caseware.templateupdate.summary.ChangeClassifier;
import com.caseware.templateupdate.summary.ChangeNarrator;
import com.caseware.templateupdate.summary.DeterministicChangeRenderer;
import com.caseware.templateupdate.summary.PathBasedChangeClassifier;
import com.caseware.templateupdate.summary.SummaryRenderer;
import com.caseware.templateupdate.summary.SummaryValidator;
import com.caseware.templateupdate.resolver.PendingUpdateResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the summarisation pipeline.
 *
 * <p>Every collaborator is bound here explicitly. That keeps the core packages free of framework
 * annotations, and makes the substitution that matters — which narrator is in use — a single
 * visible line rather than a classpath coincidence.
 */
@Configuration
public class SummaryPipelineConfig {

    /** Creates the configuration. */
    public SummaryPipelineConfig() {
        // Nothing to configure.
    }

    /**
     * The deterministic classifier that establishes what changed.
     *
     * @return the classifier
     */
    @Bean
    public ChangeClassifier changeClassifier() {
        return new PathBasedChangeClassifier();
    }

    /**
     * The renderer used whenever narration is unavailable or untrusted.
     *
     * @return the deterministic renderer
     */
    @Bean
    public SummaryRenderer summaryRenderer() {
        return new DeterministicChangeRenderer();
    }

    /**
     * The check applied to every narrated summary before a practitioner sees it.
     *
     * @return the validator
     */
    @Bean
    public SummaryValidator summaryValidator() {
        return new SummaryValidator();
    }

    /**
     * Decides whether an engagement has a pending update and over what range.
     *
     * @return the resolver
     */
    @Bean
    public PendingUpdateResolver pendingUpdateResolver() {
        return new PendingUpdateResolver();
    }

    /**
     * The narrator bound at startup.
     *
     * <p>Defaults to the deterministic renderer adapted to the narrator interface, so the system
     * runs correctly with no model configured at all. A real client is substituted here, and only
     * here, when one is available — which is the deployment order the design argues for: ship the
     * plain summary first, add narration as an enhancement.
     *
     * @param renderer the deterministic renderer to fall back on
     * @param modelEnabled whether a language model is configured
     * @return the narrator to use
     */
    @Bean
    public ChangeNarrator changeNarrator(
            SummaryRenderer renderer,
            @Value("${advisor.narration.model-enabled:false}") boolean modelEnabled) {
        if (modelEnabled) {
            throw new IllegalStateException(
                    "No model client is configured in this slice; see DESIGN.md, Implementation Plan P3.");
        }
        return renderer::render;
    }

    /**
     * The service that produces the summary shown alongside a pending update.
     *
     * @param narrator phrases a change set
     * @param validator checks that phrasing
     * @param renderer the fallback rendering
     * @param cache the durable summary store
     * @return the summarisation service
     */
    @Bean
    public ChangeSummaryService changeSummaryService(
            ChangeNarrator narrator,
            SummaryValidator validator,
            SummaryRenderer renderer,
            SummaryCache cache) {
        return new ChangeSummaryService(narrator, validator, renderer, cache);
    }
}
