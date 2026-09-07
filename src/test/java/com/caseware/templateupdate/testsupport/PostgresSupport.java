package com.caseware.templateupdate.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Supplies a real PostgreSQL for the repository tests.
 *
 * <p>Deliberately not H2. The schema relies on {@code jsonb}, integer arrays, {@code ON CONFLICT}
 * upserts and check constraints; a substitute that approximates those would let a test pass while
 * production failed. The container is started once and reused across the suite, and
 * {@code @ServiceConnection} points Spring at it without any properties file.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresSupport {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("advisor")
                .withUsername("advisor")
                .withPassword("advisor")
                .withReuse(true);
    }
}
