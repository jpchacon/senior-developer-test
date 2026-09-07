package com.caseware.templateupdate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import com.caseware.templateupdate.summary.DeterministicChangeRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SummaryPipelineConfig")
class SummaryPipelineConfigTest {

    private final SummaryPipelineConfig config = new SummaryPipelineConfig();

    @Test
    @DisplayName("runs without a model by narrating through the deterministic renderer")
    void defaultsToDeterministicNarration() {
        assertThat(config.changeNarrator(new DeterministicChangeRenderer(), false)).isNotNull();
    }

    @Test
    @DisplayName("refuses to start pretending a model is wired when none is")
    void refusesToClaimAModelItDoesNotHave() {
        DeterministicChangeRenderer renderer = new DeterministicChangeRenderer();

        assertThatThrownBy(() -> config.changeNarrator(renderer, true))
                .as("failing loudly beats silently serving plain summaries as if they were narrated")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No model client is configured");
    }

    @Test
    @DisplayName("supplies the rest of the pipeline")
    void suppliesPipelineCollaborators() {
        assertThat(config.changeClassifier()).isNotNull();
        assertThat(config.summaryRenderer()).isNotNull();
        assertThat(config.summaryValidator()).isNotNull();
        assertThat(config.pendingUpdateResolver()).isNotNull();
        assertThat(
                        config.changeSummaryService(
                                config.summaryRenderer()::render,
                                config.summaryValidator(),
                                config.summaryRenderer(),
                                new com.caseware.templateupdate.cache.InMemorySummaryCache()))
                .isNotNull();
    }
}
