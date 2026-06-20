package kr.lunaf.cloudislands.coreservice.mission;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public final class InMemoryIslandMissionRepository implements IslandMissionRepository {
    private final Map<UUID, Map<String, IslandMissionSnapshot>> missions = new ConcurrentHashMap<>();
    private final Map<String, MissionProviderDefinitionSnapshot> providerDefinitions = new ConcurrentHashMap<>();

    @Override
    public List<IslandMissionSnapshot> list(UUID islandId, String kind) {
        ensureDefaults(islandId);
        String normalized = MissionCatalog.normalizeKind(kind);
        return missions.getOrDefault(islandId, Map.of()).values().stream()
            .filter(snapshot -> snapshot.kind().equals(normalized))
            .sorted(Comparator.comparing(IslandMissionSnapshot::missionKey))
            .toList();
    }

    @Override
    public Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        ensureDefaults(islandId);
        Map<String, IslandMissionSnapshot> islandMissions = missions.getOrDefault(islandId, Map.of());
        IslandMissionSnapshot current = islandMissions.get(missionKey.toLowerCase());
        if (current == null || !current.kind().equals(MissionCatalog.normalizeKind(kind))) {
            return Optional.empty();
        }
        IslandMissionSnapshot completed = new IslandMissionSnapshot(islandId, current.missionKey(), current.kind(), current.title(), current.goal(), current.goal(), true, current.reward(), Instant.now());
        islandMissions.put(current.missionKey(), completed);
        return Optional.of(completed);
    }

    @Override
    public Optional<IslandMissionSnapshot> progress(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        ensureDefaults(islandId);
        Map<String, IslandMissionSnapshot> islandMissions = missions.getOrDefault(islandId, Map.of());
        IslandMissionSnapshot current = islandMissions.get(missionKey.toLowerCase());
        if (current == null || !current.kind().equals(MissionCatalog.normalizeKind(kind))) {
            return Optional.empty();
        }
        long nextProgress = Math.min(current.goal(), current.progress() + Math.max(0L, amount));
        IslandMissionSnapshot updated = new IslandMissionSnapshot(islandId, current.missionKey(), current.kind(), current.title(), nextProgress, current.goal(), nextProgress >= current.goal(), current.reward(), Instant.now());
        islandMissions.put(current.missionKey(), updated);
        return Optional.of(updated);
    }

    @Override
    public IslandMissionSnapshot importCompleted(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        ensureDefaults(islandId);
        Map<String, IslandMissionSnapshot> islandMissions = missions.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>());
        String key = missionKey.toLowerCase();
        IslandMissionSnapshot current = islandMissions.get(key);
        IslandMissionSnapshot completed = current == null
            ? new IslandMissionSnapshot(islandId, key, MissionCatalog.normalizeKind(kind), key, 1L, 1L, true, "", Instant.now())
            : new IslandMissionSnapshot(islandId, current.missionKey(), current.kind(), current.title(), current.goal(), current.goal(), true, current.reward(), Instant.now());
        islandMissions.put(key, completed);
        return completed;
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> listProviderDefinitions(String providerId) {
        String provider = providerId == null || providerId.isBlank() ? "" : providerId.trim();
        return providerDefinitions.values().stream()
            .filter(definition -> provider.isBlank() || definition.providerId().equals(provider))
            .sorted(Comparator.comparing(MissionProviderDefinitionSnapshot::providerId).thenComparing(MissionProviderDefinitionSnapshot::missionKey))
            .toList();
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> registerProviderDefinitions(String providerId, List<MissionProviderDefinitionSnapshot> definitions) {
        String provider = providerId == null || providerId.isBlank() ? "unknown-provider" : providerId.trim();
        for (MissionProviderDefinitionSnapshot definition : definitions == null ? List.<MissionProviderDefinitionSnapshot>of() : definitions) {
            MissionProviderDefinitionSnapshot normalized = new MissionProviderDefinitionSnapshot(provider, definition.missionKey(), definition.kind(), definition.title(), definition.goal(), definition.reward(), definition.enabled(), Instant.now());
            if (!normalized.missionKey().isBlank()) {
                providerDefinitions.put(normalized.missionKey(), normalized);
            }
        }
        return listProviderDefinitions(provider);
    }

    private void ensureDefaults(UUID islandId) {
        Map<String, IslandMissionSnapshot> islandMissions = missions.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>());
        for (MissionDefinition definition : definitions()) {
            if (!definition.enabled()) {
                continue;
            }
            islandMissions.putIfAbsent(definition.missionKey(), new IslandMissionSnapshot(islandId, definition.missionKey(), definition.kind(), definition.title(), 0L, definition.goal(), false, definition.reward(), Instant.EPOCH));
        }
    }

    private List<MissionDefinition> definitions() {
        java.util.ArrayList<MissionDefinition> definitions = new java.util.ArrayList<>(MissionCatalog.all());
        providerDefinitions.values().stream()
            .map(MissionDefinition::new)
            .forEach(definitions::add);
        return List.copyOf(definitions);
    }
}
