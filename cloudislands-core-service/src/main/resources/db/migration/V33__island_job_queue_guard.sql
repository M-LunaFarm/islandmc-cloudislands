ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_type_not_blank
    CHECK (trim(job_type) <> '');

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_state_not_blank
    CHECK (trim(state) <> '');

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_request_id_not_blank
    CHECK (trim(request_id) <> '');

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_retry_bounds
    CHECK (retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries);
