ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_id_not_blank
    CHECK (trim(id) <> '');

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_id_trimmed
    CHECK (id = trim(id));

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_id_lowercase
    CHECK (id = lower(id));

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_display_name_not_blank
    CHECK (trim(display_name) <> '');

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_display_name_trimmed
    CHECK (display_name = trim(display_name));

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_min_node_version_trimmed
    CHECK (min_node_version IS NULL OR min_node_version = trim(min_node_version));

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_min_node_version_not_blank
    CHECK (min_node_version IS NULL OR trim(min_node_version) <> '');

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_template_id_trimmed
    CHECK (template_id = trim(template_id));

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_template_id_lowercase
    CHECK (template_id = lower(template_id));

ALTER TABLE islands
    ADD CONSTRAINT fk_islands_template_id
    FOREIGN KEY (template_id)
    REFERENCES island_templates(id)
    NOT VALID;
