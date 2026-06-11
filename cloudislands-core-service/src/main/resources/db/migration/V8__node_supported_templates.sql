ALTER TABLE server_nodes
    ADD COLUMN supported_templates TEXT NOT NULL DEFAULT '*';
