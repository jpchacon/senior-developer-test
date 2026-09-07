package com.caseware.templateupdate.persistence;

import com.caseware.templateupdate.domain.EngagementId;
import com.caseware.templateupdate.domain.EngagementTemplateState;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Stores engagement template state in the tenant's PostgreSQL schema.
 *
 * <p>Plain SQL rather than an ORM: there are only a handful of statements here and each one
 * carries a design rule that reads better as SQL than as mapping annotations.
 */
@Repository
public class JdbcEngagementStateRepository implements EngagementStateRepository {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;

    /**
     * Creates the repository.
     *
     * @param jdbcClient the client used for queries
     * @param dataSource used only to construct SQL arrays for the declined versions column
     */
    public JdbcEngagementStateRepository(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    @Override
    public Optional<EngagementTemplateState> findById(EngagementId engagementId) {
        return jdbcClient
                .sql(
                        """
                        SELECT engagement_id, template_id, current_version, declined_versions
                          FROM engagement_template_state
                         WHERE engagement_id = :engagementId
                        """)
                .param("engagementId", engagementId.value())
                .query(this::mapState)
                .optional();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code WHERE} clause on the conflict branch is the monotonic guard: a redelivered or
     * late event updates nothing rather than dragging the engagement back to an older version.
     * This is the same rule as {@code EngagementTemplateState.withApplied}, enforced again at the
     * one place concurrent consumers actually race.
     */
    @Override
    public void save(EngagementTemplateState state) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO engagement_template_state
                                    (engagement_id, template_id, current_version, declined_versions, updated_at)
                             VALUES (:engagementId, :templateId, :currentVersion, :declinedVersions, now())
                        ON CONFLICT (engagement_id) DO UPDATE
                                SET current_version   = excluded.current_version,
                                    declined_versions = excluded.declined_versions,
                                    template_id       = excluded.template_id,
                                    updated_at        = now()
                              WHERE excluded.current_version >= engagement_template_state.current_version
                        """)
                .param("engagementId", state.engagementId().value())
                .param("templateId", state.templateId().value())
                .param("currentVersion", state.currentVersion().number())
                .param("declinedVersions", declinedVersionsArray(state))
                .update();
    }

    @Override
    public List<TemplateVersion> findDistinctVersionsInUse(TemplateId templateId) {
        return jdbcClient
                .sql(
                        """
                        SELECT DISTINCT current_version
                          FROM engagement_template_state
                         WHERE template_id = :templateId
                         ORDER BY current_version
                        """)
                .param("templateId", templateId.value())
                .query((ResultSet rs, int rowNum) -> new TemplateVersion(rs.getInt("current_version")))
                .list();
    }

    @Override
    public List<EngagementTemplateState> findAllByTemplate(TemplateId templateId) {
        return jdbcClient
                .sql(
                        """
                        SELECT engagement_id, template_id, current_version, declined_versions
                          FROM engagement_template_state
                         WHERE template_id = :templateId
                         ORDER BY engagement_id
                        """)
                .param("templateId", templateId.value())
                .query(this::mapState)
                .list();
    }

    private Array declinedVersionsArray(EngagementTemplateState state) {
        Integer[] versions =
                state.declinedVersions().stream().map(TemplateVersion::number).toArray(Integer[]::new);
        try (var connection = dataSource.getConnection()) {
            return connection.createArrayOf("integer", versions);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not build declined_versions array", failure);
        }
    }

    private EngagementTemplateState mapState(ResultSet rs, int rowNum) throws SQLException {
        return EngagementTemplateState.builder()
                .engagementId(new EngagementId(rs.getString("engagement_id")))
                .templateId(new TemplateId(rs.getString("template_id")))
                .currentVersion(new TemplateVersion(rs.getInt("current_version")))
                .declinedVersions(declinedVersionsOf(rs))
                .build();
    }

    /** The column is {@code NOT NULL DEFAULT '{}'}, so there is no null case to defend against. */
    private Set<TemplateVersion> declinedVersionsOf(ResultSet rs) throws SQLException {
        Integer[] numbers = (Integer[]) rs.getArray("declined_versions").getArray();
        return Arrays.stream(numbers)
                .map(TemplateVersion::new)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
