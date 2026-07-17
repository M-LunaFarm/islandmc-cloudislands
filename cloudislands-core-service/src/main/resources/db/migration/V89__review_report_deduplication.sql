CREATE TABLE IF NOT EXISTS island_review_reports (
    island_id UUID NOT NULL,
    reviewer_uuid UUID NOT NULL,
    reporter_uuid UUID NOT NULL,
    reason VARCHAR(180) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, reviewer_uuid, reporter_uuid),
    CONSTRAINT fk_island_review_report_review
        FOREIGN KEY (island_id, reviewer_uuid)
        REFERENCES island_reviews(island_id, reviewer_uuid)
        ON DELETE CASCADE
);

CREATE INDEX idx_island_review_reports_reporter_recent
    ON island_review_reports(reporter_uuid, created_at DESC);
