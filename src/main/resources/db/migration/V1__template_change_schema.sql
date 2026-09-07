-- Global plane: firm-independent change summaries.
--
-- A diff between two versions of a product template is identical for every customer, so this
-- schema is shared. The primary key is (template_id, from_version, to_version) and nothing else:
-- there is deliberately no column in which a firm or engagement identifier could be recorded,
-- which is how the "no customer data" invariant is enforced rather than merely documented.

CREATE SCHEMA IF NOT EXISTS template_change;

CREATE TABLE template_change.change_summary (
    template_id    text        NOT NULL,
    from_version   integer     NOT NULL,
    to_version     integer     NOT NULL,
    headline       text        NOT NULL,
    bullets        jsonb       NOT NULL,
    change_records jsonb       NOT NULL,
    -- Whether this row came from validated model output or the deterministic fallback, so the
    -- cache-only-validated rule stays auditable and fallback rows can be swept and recomputed.
    source         text        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT change_summary_pk PRIMARY KEY (template_id, from_version, to_version),
    -- Makes a backwards version range unrepresentable, matching the domain invariant.
    CONSTRAINT change_summary_version_order CHECK (to_version > from_version),
    CONSTRAINT change_summary_source_known CHECK (source IN ('LLM_VALIDATED', 'DETERMINISTIC_FALLBACK'))
);
