package kr.lunaf.cloudislands.coreservice.islandlog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;

public final class InMemoryIslandLogRepository implements IslandLogRepository {
    private final Map<UUID, List<IslandLogRecord>> logs = new ConcurrentHashMap<>();

    @Override
    public void append(UUID islandId, UUID actorUuid, String action, Map<String, String> payload) {
        logs.computeIfAbsent(islandId, ignored -> new ArrayList<>())
            .add(new IslandLogRecord(UUID.randomUUID(), islandId, actorUuid, action, Map.copyOf(payload), Instant.now()));
    }

    @Override
    public List<IslandLogRecord> list(UUID islandId, int limit) {
        return logs.getOrDefault(islandId, List.of()).stream()
            .sorted(Comparator.comparing(IslandLogRecord::createdAt).reversed())
            .limit(limit)
            .toList();
    }
}
