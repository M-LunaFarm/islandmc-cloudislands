ALTER TABLE island_missions
    DROP CONSTRAINT island_missions_pkey;

ALTER TABLE island_missions
    ADD PRIMARY KEY (island_id, mission_key, kind);

ALTER TABLE island_mission_definitions
    DROP CONSTRAINT island_mission_definitions_pkey;

ALTER TABLE island_mission_definitions
    ADD PRIMARY KEY (mission_key, kind);
