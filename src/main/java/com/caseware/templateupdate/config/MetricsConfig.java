package com.caseware.templateupdate.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the meters the design document names as its operational signals.
 *
 * <p>Declared explicitly rather than incremented ad hoc, so the metrics an operator is told to
 * watch and the metrics the code actually publishes cannot drift apart.
 *
 * <p>See {@code DESIGN.md}, section 4 (Evaluation and Observability).
 */
@Configuration
public class MetricsConfig {

    /** Creates the configuration. */
    public MetricsConfig() {
        // Nothing to configure.
    }

    /**
     * Counts engagements found to be on a different version than the projection believed.
     *
     * <p>The key indicator for this design: it measures directly whether projecting state from
     * events, rather than reading it, is holding up in production.
     *
     * @param registry the meter registry
     * @return the drift counter
     */
    @Bean
    public Counter projectionDriftCounter(MeterRegistry registry) {
        return Counter.builder("projection.drift.detected")
                .description("Engagements whose real template version differed from the projection")
                .register(registry);
    }

    /**
     * Counts summaries served from the shared store rather than recomputed.
     *
     * @param registry the meter registry
     * @return the cache hit counter
     */
    @Bean
    public Counter summaryCacheHitCounter(MeterRegistry registry) {
        return Counter.builder("summary.cache.hit")
                .description("Change summaries reused across firms rather than recomputed")
                .register(registry);
    }

    /**
     * Counts narrated summaries rejected by validation.
     *
     * <p>How often the model was caught saying something the diff does not support. A rise here is
     * a reason to review the prompt or the model, not to relax the check.
     *
     * @param registry the meter registry
     * @return the validation failure counter
     */
    @Bean
    public Counter narrationValidationFailedCounter(MeterRegistry registry) {
        return Counter.builder("narration.validation.failed")
                .description("Narrated summaries rejected for not matching the change set")
                .register(registry);
    }

    /**
     * Counts requests served by the deterministic fallback.
     *
     * @param registry the meter registry
     * @return the fallback counter
     */
    @Bean
    public Counter narrationFallbackUsedCounter(MeterRegistry registry) {
        return Counter.builder("narration.fallback.used")
                .description("Summaries served from the deterministic renderer")
                .register(registry);
    }
}
