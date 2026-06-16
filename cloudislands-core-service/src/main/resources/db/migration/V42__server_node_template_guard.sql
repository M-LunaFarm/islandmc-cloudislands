ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_node_version_trimmed
    CHECK (node_version = trim(node_version));

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_not_blank
    CHECK (trim(supported_templates) <> '');

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_trimmed
    CHECK (supported_templates = trim(supported_templates));

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_lowercase
    CHECK (supported_templates = '*' OR supported_templates = lower(supported_templates));

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_list_shape
    CHECK (
        supported_templates = '*'
        OR (
            supported_templates NOT LIKE ',%'
            AND supported_templates NOT LIKE '%,'
            AND supported_templates NOT LIKE '%,,%'
            AND supported_templates NOT LIKE '% %'
        )
    );
