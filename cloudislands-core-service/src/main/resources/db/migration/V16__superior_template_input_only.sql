UPDATE island_templates
SET enabled = false,
    display_name = 'SuperiorSkyblock2 Migration Input',
    updated_at = now()
WHERE id = 'superiorskyblock2';
