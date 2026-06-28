ALTER TABLE island_reviews
    ADD COLUMN moderation_state VARCHAR(32) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN report_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN report_reason VARCHAR(180) NOT NULL DEFAULT '',
    ADD COLUMN moderated_by UUID,
    ADD COLUMN moderated_at TIMESTAMPTZ,
    ADD COLUMN moderation_note VARCHAR(180) NOT NULL DEFAULT '';

CREATE INDEX idx_island_reviews_moderation_queue
    ON island_reviews(moderation_state, report_count DESC, updated_at DESC);
