ALTER TABLE addon_state
    ADD CONSTRAINT addon_state_addon_id_length CHECK (char_length(addon_id) BETWEEN 1 AND 128),
    ADD CONSTRAINT addon_state_key_length CHECK (char_length(state_key) BETWEEN 1 AND 128),
    ADD CONSTRAINT addon_state_value_length CHECK (char_length(state_value) <= 4096);
