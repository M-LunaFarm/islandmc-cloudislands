ALTER TABLE server_nodes
    DROP CONSTRAINT IF EXISTS chk_server_nodes_supported_templates_lowercase;

ALTER TABLE server_nodes
    DROP CONSTRAINT IF EXISTS chk_server_nodes_supported_templates_list_shape;

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_lowercase
    CHECK (
        split_part(supported_templates, ';', 1) = '*'
        OR split_part(supported_templates, ';', 1) = lower(split_part(supported_templates, ';', 1))
    );

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_supported_templates_list_shape
    CHECK (
        split_part(supported_templates, ';', 1) = '*'
        OR (
            split_part(supported_templates, ';', 1) NOT LIKE ',%'
            AND split_part(supported_templates, ';', 1) NOT LIKE '%,'
            AND split_part(supported_templates, ';', 1) NOT LIKE '%,,%'
            AND split_part(supported_templates, ';', 1) NOT LIKE '% %'
        )
    );

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_heartbeat_metadata_shape
    CHECK (
        supported_templates !~ E'[\\r\\n\\t]'
        AND supported_templates NOT LIKE '%;'
        AND supported_templates NOT LIKE '%;;%'
    );
