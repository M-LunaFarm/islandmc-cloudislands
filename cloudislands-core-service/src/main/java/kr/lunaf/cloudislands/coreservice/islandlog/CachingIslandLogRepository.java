package kr.lunaf.cloudislands.coreservice.islandlog;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandLogRepository implements IslandLogRepository {
    private static final int CACHE_LIMIT = 100;

    private final IslandLogRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandLogRepository(IslandLogRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public void append(UUID islandId, UUID actorUuid, String action, Map<String, String> payload) {
        delegate.append(islandId, actorUuid, action, payload);
        refresh(islandId);
    }

    @Override
    public List<IslandLogRecord> list(UUID islandId, int limit) {
        Optional<List<IslandLogRecord>> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get().stream().limit(Math.max(1, limit)).toList();
        }
        return refresh(islandId).stream().limit(Math.max(1, limit)).toList();
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandLogRecord> refresh(UUID islandId) {
        return cache(islandId, delegate.list(islandId, CACHE_LIMIT));
    }

    private List<IslandLogRecord> cache(UUID islandId, List<IslandLogRecord> logs) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandLogs(islandId), encode(logs));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return logs;
    }

    private Optional<List<IslandLogRecord>> cached(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandLogs(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandLogRecord> logs) {
        StringBuilder out = new StringBuilder();
        for (IslandLogRecord log : logs) {
            out.append(log.logId()).append('|')
                .append(log.islandId()).append('|')
                .append(log.actorUuid()).append('|')
                .append(encodeText(log.action())).append('|')
                .append(encodePayload(log.payload())).append('|')
                .append(log.createdAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandLogRecord> parse(String value) {
        List<IslandLogRecord> logs = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 6) {
                continue;
            }
            try {
                logs.add(new IslandLogRecord(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    UUID.fromString(parts[2]),
                    decodeText(parts[3]),
                    decodePayload(parts[4]),
                    instant(parts[5])
                ));
            } catch (RuntimeException ignored) {
                // Skip a corrupt Redis cache row instead of discarding the whole cached island log list.
            }
        }
        return List.copyOf(logs);
    }

    private static String encodePayload(Map<String, String> payload) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(encodeText(entry.getKey())).append(':').append(encodeText(entry.getValue()));
        }
        return encodeText(out.toString());
    }

    private static Map<String, String> decodePayload(String encoded) {
        String decoded = decodeText(encoded);
        if (decoded.isBlank()) {
            return Map.of();
        }
        Map<String, String> payload = new LinkedHashMap<>();
        for (String pair : decoded.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                payload.put(decodeText(pair.substring(0, colon)), decodeText(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(payload);
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
