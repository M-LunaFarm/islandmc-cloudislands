CREATE TABLE island_reviews (
    island_id UUID NOT NULL REFERENCES islands(id),
    reviewer_uuid UUID NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(280) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, reviewer_uuid),
    CONSTRAINT chk_island_reviews_rating_range CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_island_reviews_comment_trimmed CHECK (comment = trim(comment))
);

CREATE INDEX idx_island_reviews_recent
    ON island_reviews(island_id, updated_at DESC);

CREATE INDEX idx_island_reviews_rating_recent
    ON island_reviews(island_id, rating DESC, updated_at DESC);
