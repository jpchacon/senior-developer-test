package com.caseware.templateupdate.persistence;

import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.TEMPLATE;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.V3;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.V5;
import static com.caseware.templateupdate.testsupport.ChangeSetFixtures.faithfulSummary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caseware.templateupdate.cache.ChangeSummaryKey;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.testsupport.PostgresSupport;
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
@DisplayName("JdbcChangeSummaryRepository against real PostgreSQL")
class JdbcChangeSummaryRepositoryIT {

    private static final ChangeSummaryKey KEY = new ChangeSummaryKey(TEMPLATE, V3, V5);

    @Autowired private JdbcChangeSummaryRepository repository;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void clearSummaries() {
        jdbcClient.sql("DELETE FROM template_change.change_summary").update();
    }

    @Test
    @DisplayName("round-trips a summary through the jsonb columns intact")
    void roundTripsSummary() {
        repository.put(KEY, faithfulSummary());

        NarratedSummary found = repository.get(KEY).orElseThrow();

        assertThat(found.headline()).isEqualTo("Three changes across two areas");
        assertThat(found.bullets()).hasSize(2);
        assertThat(found.allCitedChangeIds()).containsExactly("CR-1", "CR-2", "CR-3");
    }

    @Test
    @DisplayName("returns empty for a version pair not yet summarised")
    void returnsEmptyForUnknownPair() {
        assertThat(repository.get(KEY)).isEmpty();
    }

    @Test
    @DisplayName("records that a cached summary came from validated model output")
    void recordsTheSourceOfATrustedSummary() {
        repository.put(KEY, faithfulSummary());

        assertThat(repository.sourceOf(KEY)).contains(SummarySource.LLM_VALIDATED);
    }

    @Test
    @DisplayName("can record a fallback rendering distinctly, so it can be swept later")
    void recordsFallbackSourceDistinctly() {
        repository.store(KEY, faithfulSummary(), SummarySource.DETERMINISTIC_FALLBACK);

        assertThat(repository.sourceOf(KEY)).contains(SummarySource.DETERMINISTIC_FALLBACK);
        assertThat(repository.countFrom(V3)).isEqualTo(1);
    }

    @Test
    @DisplayName("overwrites a stored summary rather than failing on a repeat write")
    void overwritesOnRepeatWrite() {
        repository.put(KEY, faithfulSummary());
        repository.put(KEY, faithfulSummary());

        assertThat(repository.countFrom(V3)).isEqualTo(1);
    }

    @Test
    @DisplayName("the database itself refuses a backwards version range")
    void databaseRejectsBackwardsRange() {
        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                """
                                                INSERT INTO template_change.change_summary
                                                     (template_id, from_version, to_version, headline,
                                                      bullets, change_records, source)
                                                VALUES ('audit-ca', 5, 3, 'backwards', '[]'::jsonb,
                                                        '[]'::jsonb, 'LLM_VALIDATED')
                                                """)
                                        .update())
                .as("the check constraint carries the domain invariant into the schema")
                .hasMessageContaining("change_summary_version_order");
    }
}
