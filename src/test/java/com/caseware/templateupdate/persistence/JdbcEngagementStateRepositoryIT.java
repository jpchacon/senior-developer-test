package com.caseware.templateupdate.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.domain.EngagementId;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import com.caseware.templateupdate.testsupport.PostgresSupport;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@SpringBootTest
@Import(PostgresSupport.class)
@DisplayName("JdbcEngagementStateRepository against real PostgreSQL")
class JdbcEngagementStateRepositoryIT {

    private static final TemplateId TEMPLATE = new TemplateId("audit-ca");

    @Autowired private JdbcEngagementStateRepository repository;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void clearProjection() {
        jdbcClient.sql("DELETE FROM engagement_template_state").update();
    }

    private static EngagementTemplateState state(String id, int version, Integer... declined) {
        return new EngagementTemplateState(
                new EngagementId(id),
                TEMPLATE,
                new TemplateVersion(version),
                Set.of(declined).stream().map(TemplateVersion::new).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @DisplayName("round-trips an engagement including its declined versions")
    void roundTripsDeclinedVersions() {
        repository.save(state("eng-1", 3, 4, 5));

        EngagementTemplateState found = repository.findById(new EngagementId("eng-1")).orElseThrow();

        assertThat(found.currentVersion()).isEqualTo(new TemplateVersion(3));
        assertThat(found.declinedVersions())
                .containsExactlyInAnyOrder(new TemplateVersion(4), new TemplateVersion(5));
    }

    @Test
    @DisplayName("returns empty for an engagement no event has been projected for")
    void returnsEmptyForUnknownEngagement() {
        assertThat(repository.findById(new EngagementId("never-seen"))).isEmpty();
    }

    @Test
    @DisplayName("is idempotent when the same event is delivered twice")
    void saveIsIdempotent() {
        repository.save(state("eng-1", 4));
        repository.save(state("eng-1", 4));

        assertThat(repository.findById(new EngagementId("eng-1")).orElseThrow().currentVersion())
                .isEqualTo(new TemplateVersion(4));
    }

    @Test
    @DisplayName("never moves an engagement backwards when events arrive out of order")
    void neverMovesBackwards() {
        repository.save(state("eng-1", 6));
        repository.save(state("eng-1", 4));

        assertThat(repository.findById(new EngagementId("eng-1")).orElseThrow().currentVersion())
                .as("a late v4 event must not undo the v6 already recorded")
                .isEqualTo(new TemplateVersion(6));
    }

    @Test
    @DisplayName("reports exactly the versions engagements are sitting on")
    void reportsDistinctVersionsInUse() {
        repository.save(state("eng-1", 3));
        repository.save(state("eng-2", 3));
        repository.save(state("eng-3", 5));

        assertThat(repository.findDistinctVersionsInUse(TEMPLATE))
                .as("this set bounds how many summaries must be precomputed after a publish")
                .containsExactly(new TemplateVersion(3), new TemplateVersion(5));
    }

    @Test
    @DisplayName("lists every engagement built from a template, in a stable order")
    void listsEngagementsByTemplate() {
        repository.save(state("eng-2", 3));
        repository.save(state("eng-1", 4));

        assertThat(repository.findAllByTemplate(TEMPLATE))
                .extracting(engagement -> engagement.engagementId().value())
                .containsExactly("eng-1", "eng-2");
        assertThat(repository.findAllByTemplate(new TemplateId("other"))).isEmpty();
    }
}
