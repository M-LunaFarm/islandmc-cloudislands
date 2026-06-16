ALTER TABLE addon_state
    ADD CONSTRAINT addon_state_addon_id_trimmed
    CHECK (addon_id = trim(addon_id)),
    ADD CONSTRAINT addon_state_key_trimmed
    CHECK (state_key = trim(state_key)),
    ADD CONSTRAINT addon_state_table_key_shape
    CHECK (
        state_key NOT LIKE 'table/%'
        OR (
            state_key LIKE 'table/%/%'
            AND split_part(state_key, '/', 2) <> ''
            AND split_part(state_key, '/', 3) <> ''
        )
    );

ALTER TABLE addon_island_state
    ADD CONSTRAINT addon_island_state_addon_id_trimmed
    CHECK (addon_id = trim(addon_id)),
    ADD CONSTRAINT addon_island_state_key_trimmed
    CHECK (state_key = trim(state_key)),
    ADD CONSTRAINT addon_island_state_table_key_shape
    CHECK (
        state_key NOT LIKE 'table/%'
        OR (
            state_key LIKE 'table/%/%'
            AND split_part(state_key, '/', 2) <> ''
            AND split_part(state_key, '/', 3) <> ''
        )
    );
