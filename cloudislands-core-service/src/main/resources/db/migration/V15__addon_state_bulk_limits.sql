ALTER TABLE addon_state
    DROP CONSTRAINT IF EXISTS addon_state_value_length,
    ADD CONSTRAINT addon_state_value_length CHECK (char_length(state_value) <= 65535);

ALTER TABLE addon_island_state
    DROP CONSTRAINT IF EXISTS addon_island_state_value_length,
    ADD CONSTRAINT addon_island_state_value_length CHECK (char_length(state_value) <= 65535);
