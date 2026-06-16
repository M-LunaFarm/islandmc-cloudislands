ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_name_not_blank
    CHECK (trim(name) <> '');

ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_world_not_blank
    CHECK (trim(world_name) <> '');

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_name_not_blank
    CHECK (trim(name) <> '');
