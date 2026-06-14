package kr.lunaf.cloudislands.coreservice.template;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIslandTemplateRepository implements IslandTemplateRepository {
    private final Map<String, IslandTemplateSnapshot> templates = new ConcurrentHashMap<>();

    public InMemoryIslandTemplateRepository() {
        templates.put("default", new IslandTemplateSnapshot("default", "Default Island", true, ""));
        templates.put("superiorskyblock2", new IslandTemplateSnapshot("superiorskyblock2", "SuperiorSkyblock2 Migration Input", false, ""));
    }

    @Override
    public Optional<IslandTemplateSnapshot> find(String templateId) {
        String id = normalize(templateId);
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public List<IslandTemplateSnapshot> list() {
        return templates.values().stream()
            .sorted(Comparator.comparing(IslandTemplateSnapshot::id))
            .toList();
    }

    @Override
    public IslandTemplateSnapshot upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        String id = normalize(templateId);
        String name = displayName == null || displayName.isBlank() ? id : displayName;
        IslandTemplateSnapshot snapshot = new IslandTemplateSnapshot(id, name, enabled, minNodeVersion == null ? "" : minNodeVersion);
        templates.put(id, snapshot);
        return snapshot;
    }

    @Override
    public boolean setEnabled(String templateId, boolean enabled) {
        String id = normalize(templateId);
        IslandTemplateSnapshot current = templates.get(id);
        if (current == null) {
            return false;
        }
        templates.put(id, new IslandTemplateSnapshot(current.id(), current.displayName(), enabled, current.minNodeVersion()));
        return true;
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }
}
