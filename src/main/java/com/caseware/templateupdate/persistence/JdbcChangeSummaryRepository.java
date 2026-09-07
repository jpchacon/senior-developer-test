package com.caseware.templateupdate.persistence;

import com.caseware.templateupdate.cache.ChangeSummaryKey;
import com.caseware.templateupdate.cache.SummaryCache;
import com.caseware.templateupdate.domain.NarratedSummary;
import com.caseware.templateupdate.domain.SummaryBullet;
import com.caseware.templateupdate.domain.TemplateVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Stores change summaries in the shared, firm-independent schema.
 *
 * <p>Implements {@link SummaryCache} because that is exactly what this table is: a durable cache
 * keyed by template version pair. Versions are immutable, so an entry never needs invalidating.
 */
@Repository
public class JdbcChangeSummaryRepository implements SummaryCache {

    private static final TypeReference<List<SummaryBullet>> BULLET_LIST = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates the repository.
     *
     * @param jdbcClient the client used for queries
     * @param objectMapper used to read and write the jsonb columns
     */
    public JdbcChangeSummaryRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<NarratedSummary> get(ChangeSummaryKey key) {
        return jdbcClient
                .sql(
                        """
                        SELECT headline, bullets
                          FROM template_change.change_summary
                         WHERE template_id  = :templateId
                           AND from_version = :fromVersion
                           AND to_version   = :toVersion
                        """)
                .param("templateId", key.templateId().value())
                .param("fromVersion", key.fromVersion().number())
                .param("toVersion", key.toVersion().number())
                .query(this::mapSummary)
                .optional();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rows written through this method are validated model output; the service never caches a
     * fallback rendering. Anything stored here is therefore marked {@code LLM_VALIDATED}.
     */
    @Override
    public void put(ChangeSummaryKey key, NarratedSummary summary) {
        store(key, summary, SummarySource.LLM_VALIDATED);
    }

    /**
     * Stores a summary, recording how it was produced.
     *
     * @param key the version pair the summary describes
     * @param summary the summary to store
     * @param source how the summary was produced
     */
    public void store(ChangeSummaryKey key, NarratedSummary summary, SummarySource source) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO template_change.change_summary
                                    (template_id, from_version, to_version, headline, bullets,
                                     change_records, source)
                             VALUES (:templateId, :fromVersion, :toVersion, :headline, :bullets,
                                     :changeRecords, :source)
                        ON CONFLICT (template_id, from_version, to_version) DO UPDATE
                                SET headline       = excluded.headline,
                                    bullets        = excluded.bullets,
                                    change_records = excluded.change_records,
                                    source         = excluded.source
                        """)
                .param("templateId", key.templateId().value())
                .param("fromVersion", key.fromVersion().number())
                .param("toVersion", key.toVersion().number())
                .param("headline", summary.headline())
                .param("bullets", jsonb(summary.bullets()))
                .param("changeRecords", jsonb(summary.allCitedChangeIds()))
                .param("source", source.name())
                .update();
    }

    /** PostgreSQL will not implicitly cast text to jsonb, so the type is stated explicitly. */
    private SqlParameterValue jsonb(Object value) {
        try {
            return new SqlParameterValue(Types.OTHER, objectMapper.writeValueAsString(value));
        } catch (JacksonException failure) {
            throw new IllegalArgumentException("could not serialise summary content", failure);
        }
    }

    private NarratedSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new NarratedSummary(
                    rs.getString("headline"), objectMapper.readValue(rs.getString("bullets"), BULLET_LIST));
        } catch (JacksonException failure) {
            throw new IllegalStateException("stored summary could not be read back", failure);
        }
    }

    /**
     * Reports how a stored summary was produced.
     *
     * @param key the version pair to inspect
     * @return the recorded source, or empty if nothing is stored for that pair
     */
    public Optional<SummarySource> sourceOf(ChangeSummaryKey key) {
        return jdbcClient
                .sql(
                        """
                        SELECT source
                          FROM template_change.change_summary
                         WHERE template_id  = :templateId
                           AND from_version = :fromVersion
                           AND to_version   = :toVersion
                        """)
                .param("templateId", key.templateId().value())
                .param("fromVersion", key.fromVersion().number())
                .param("toVersion", key.toVersion().number())
                .query((ResultSet rs, int rowNum) -> SummarySource.valueOf(rs.getString("source")))
                .optional();
    }

    /**
     * Counts stored summaries for a template version.
     *
     * @param fromVersion the version summaries start from
     * @return how many summaries begin at that version
     */
    public int countFrom(TemplateVersion fromVersion) {
        return jdbcClient
                .sql("SELECT count(*) FROM template_change.change_summary WHERE from_version = :v")
                .param("v", fromVersion.number())
                .query(Integer.class)
                .single();
    }
}
