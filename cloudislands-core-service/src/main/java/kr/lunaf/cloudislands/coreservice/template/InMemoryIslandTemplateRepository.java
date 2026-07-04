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
            .sorted(Comparator.comparingInt(IslandTemplateSnapshot::sortOrder).thenComparing(IslandTemplateSnapshot::id))
            .toList();
    }

    @Override
    public IslandTemplateSnapshot upsert(IslandTemplateSnapshot template) {
        IslandTemplateSnapshot snapshot = template == null ? new IslandTemplateSnapshot("default", "Default Island", true, "") : template;
        templates.put(snapshot.id(), snapshot);
        return snapshot;
    }

    @Override
    public boolean setEnabled(String templateId, boolean enabled) {
        String id = normalize(templateId);
        IslandTemplateSnapshot current = templates.get(id);
        if (current == null) {
            return false;
        }
        templates.put(id, new IslandTemplateSnapshot(
            current.id(),
            current.displayName(),
            current.description(),
            current.category(),
            enabled,
            current.minNodeVersion(),
            current.requiredPermission(),
            current.iconMaterial(),
            current.iconCustomModelData(),
            current.previewImageKey(),
            current.bundleStoragePath(),
            current.bundleChecksum(),
            current.bundleSizeBytes(),
            current.schemaVersion(),
            current.defaultIslandSize(),
            current.spawnWorldOffsetX(),
            current.spawnWorldOffsetY(),
            current.spawnWorldOffsetZ(),
            current.spawnYaw(),
            current.spawnPitch(),
            current.homeName(),
            current.environmentPreset(),
            current.biomeKey(),
            current.borderColor(),
            current.bankInitialBalance(),
            current.creationCost(),
            current.sortOrder(),
            current.tags(),
            current.createdAt(),
            java.time.Instant.now()
        ));
        return true;
    }

    @Override
    public boolean delete(String templateId) {
        String id = normalize(templateId);
        if ("default".equals(id) || "superiorskyblock2".equals(id)) {
            return false;
        }
        return templates.remove(id) != null;
    }

    @Override
    public boolean reorder(String templateId, int sortOrder) {
        String id = normalize(templateId);
        IslandTemplateSnapshot current = templates.get(id);
        if (current == null) {
            return false;
        }
        templates.put(id, new IslandTemplateSnapshot(
            current.id(),
            current.displayName(),
            current.description(),
            current.category(),
            current.enabled(),
            current.minNodeVersion(),
            current.requiredPermission(),
            current.iconMaterial(),
            current.iconCustomModelData(),
            current.previewImageKey(),
            current.bundleStoragePath(),
            current.bundleChecksum(),
            current.bundleSizeBytes(),
            current.schemaVersion(),
            current.defaultIslandSize(),
            current.spawnWorldOffsetX(),
            current.spawnWorldOffsetY(),
            current.spawnWorldOffsetZ(),
            current.spawnYaw(),
            current.spawnPitch(),
            current.homeName(),
            current.environmentPreset(),
            current.biomeKey(),
            current.borderColor(),
            current.bankInitialBalance(),
            current.creationCost(),
            Math.max(0, sortOrder),
            current.tags(),
            current.createdAt(),
            java.time.Instant.now()
        ));
        return true;
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }
}
