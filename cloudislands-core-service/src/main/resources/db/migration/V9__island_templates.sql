CREATE TABLE island_templates (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    min_node_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO island_templates(id, display_name, enabled)
VALUES
    ('default', 'Default Island', true),
    ('superiorskyblock2', 'SuperiorSkyblock2 Migration Input', false)
ON CONFLICT (id) DO NOTHING;
