package kr.lunaf.cloudislands.coreservice.mission;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandMissionRepository implements IslandMissionRepository {
    private final IslandMissionRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandMissionRepository(IslandMissionRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public List<IslandMissionSnapshot> list(UUID islandId, String kind) {
        String normalized = MissionCatalog.normalizeKind(kind);
        Optional<List<IslandMissionSnapshot>> cached = cached(islandId, normalized);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(islandId, normalized, delegate.list(islandId, normalized));
    }

    @Override
    public Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        String normalized = MissionCatalog.normalizeKind(kind);
        Optional<IslandMissionSnapshot> completed = delegate.complete(islandId, actorUuid, missionKey, normalized);
        cache(islandId, normalized, delegate.list(islandId, normalized));
        return completed;
    }

    @Override
    public IslandMissionSnapshot importCompleted(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        String normalized = MissionCatalog.normalizeKind(kind);
        IslandMissionSnapshot imported = delegate.importCompleted(islandId, actorUuid, missionKey, normalized);
        cache(islandId, imported.kind(), delegate.list(islandId, imported.kind()));
        return imported;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandMissionSnapshot> cache(UUID islandId, String kind, List<IslandMissionSnapshot> missions) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandMissions(islandId, kind), encode(missions));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return missions;
    }

    private Optional<List<IslandMissionSnapshot>> cached(UUID islandId, String kind) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandMissions(islandId, kind));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandMissionSnapshot> missions) {
        StringBuilder out = new StringBuilder();
        for (IslandMissionSnapshot mission : missions) {
            out.append(mission.islandId()).append('|')
                .append(encodeText(mission.missionKey())).append('|')
                .append(encodeText(mission.kind())).append('|')
                .append(encodeText(mission.title())).append('|')
                .append(mission.progress()).append('|')
                .append(mission.goal()).append('|')
                .append(mission.completed()).append('|')
                .append(encodeText(mission.reward())).append('|')
                .append(mission.updatedAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandMissionSnapshot> parse(String value) {
        List<IslandMissionSnapshot> missions = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 9) {
                continue;
            }
            missions.add(new IslandMissionSnapshot(
                UUID.fromString(parts[0]),
                decodeText(parts[1]),
                decodeText(parts[2]),
                decodeText(parts[3]),
                Long.parseLong(parts[4]),
                Long.parseLong(parts[5]),
                Boolean.parseBoolean(parts[6]),
                decodeText(parts[7]),
                instant(parts[8])
            ));
        }
        return List.copyOf(missions);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encodedBase64) {
        return new String(Base64.getUrlDecoder().decode(encodedBase64), StandardCharsets.UTF_8);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
