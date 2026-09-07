package com.caseware.templateupdate;

import static org.assertj.core.api.Assertions.assertThat;

import com.caseware.templateupdate.api.PendingUpdateQueryService;
import com.caseware.templateupdate.testsupport.PostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Proves the context wires up and Flyway migrates, which unit tests structurally cannot. */
@Tag("integration")
@SpringBootTest
@Import(PostgresSupport.class)
@DisplayName("Application context")
class ApplicationContextIT {

    @Autowired private PendingUpdateQueryService queryService;
    @Autowired private JdbcClient jdbcClient;

    @Test
    @DisplayName("starts with both migrated schemas present")
    void migratesBothSchemas() {
        assertThat(queryService).isNotNull();

        Integer tables =
                jdbcClient
                        .sql(
                                """
                                SELECT count(*) FROM information_schema.tables
                                 WHERE (table_schema = 'template_change' AND table_name = 'change_summary')
                                    OR (table_schema = 'public' AND table_name = 'engagement_template_state')
                                """)
                        .query(Integer.class)
                        .single();

        assertThat(tables).isEqualTo(2);
    }
}
