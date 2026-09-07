-- Tenant plane: one firm's projected engagement state.
--
-- Deployed once per firm schema. This table is the read model that replaces loading engagement
-- files: it is maintained from the three events that can change an engagement's template version
-- (created, applied, declined), so answering "which files have pending updates?" costs a query
-- rather than minutes of loading.

CREATE TABLE engagement_template_state (
    engagement_id     text        NOT NULL,
    template_id       text        NOT NULL,
    current_version   integer     NOT NULL,
    -- Declines are deferrals, not skips: recorded for context, never used to advance the
    -- version the next diff is computed from.
    declined_versions integer[]   NOT NULL DEFAULT '{}',
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT engagement_template_state_pk PRIMARY KEY (engagement_id),
    CONSTRAINT engagement_template_state_version_positive CHECK (current_version > 0)
);

-- Drives publish fan-out: the distinct current_versions in use for a template are exactly the
-- "from" versions whose summaries need precomputing. Bounded by what is actually deployed,
-- rather than by every version pair that could theoretically be asked for.
CREATE INDEX engagement_template_state_fanout
    ON engagement_template_state (template_id, current_version);
