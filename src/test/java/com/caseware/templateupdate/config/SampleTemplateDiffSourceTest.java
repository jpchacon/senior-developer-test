package com.caseware.templateupdate.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SampleTemplateDiffSource")
class SampleTemplateDiffSourceTest {

    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");

    private final SampleTemplateDiffSource diffSource = new SampleTemplateDiffSource();

    @Test
    @DisplayName("produces a diff that grows with the version gap")
    void diffGrowsWithTheGap() {
        JsonDiff oneStep =
                diffSource.diff(TEMPLATE, new TemplateVersion(3), new TemplateVersion(4));
        JsonDiff threeSteps =
                diffSource.diff(TEMPLATE, new TemplateVersion(3), new TemplateVersion(6));

        assertThat(oneStep.entries()).hasSize(2);
        assertThat(threeSteps.entries())
                .as("an engagement three versions behind has more to review")
                .hasSize(6);
    }

    @Test
    @DisplayName("produces nothing when the versions match")
    void producesNothingForNoGap() {
        assertThat(diffSource.diff(TEMPLATE, new TemplateVersion(4), new TemplateVersion(4)).entries())
                .isEmpty();
    }
}
