package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandRuntimeRepository implements IslandRuntimeRepository {
    private static final long COUNTS_CACHE_TTL_MILLIS = 5_000L;
    private static final long NODE_LIST_CACHE_TTL_MILLIS = 5_000L;
    private static final int MAX_CACHED_NODE_LIST_LIMIT = 500;
    private final IslandRuntimeRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandRuntimeRepository(IslandRuntimeRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public Optional<IslandRuntimeSnapshot> find(UUID islandId) {
        Optional<IslandRuntimeSnapshot> cached = cachedRuntime(islandId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<IslandRuntimeSnapshot> runtime = delegate.find(islandId);
        runtime.ifPresent(this::cache);
        return runtime;
    }

    @Override
    public List<IslandRuntimeSnapshot> listByNode(String nodeId, int limit) {
        if (limit > MAX_CACHED_NODE_LIST_LIMIT) {
            return delegate.listByNode(nodeId, limit);
        }
        Optional<List<IslandRuntimeSnapshot>> cached = cachedNodeRuntimes(nodeId, limit);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandRuntimeSnapshot> runtimes = delegate.listByNode(nodeId, limit);
        cacheNodeRuntimes(nodeId, limit, runtimes);
        return runtimes;
    }

    @Override
    public boolean placementOccupied(String worldName, int cellX, int cellZ, UUID exceptIslandId) {
        return delegate.placementOccupied(worldName, cellX, cellZ, exceptIslandId);
    }

    @Override
    public IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ) {
        return cacheChanged(delegate.markActivating(islandId, targetNode, targetWorld, cellX, cellZ));
    }

    @Override
    public IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken) {
        return cacheChanged(delegate.markActive(islandId, nodeId, worldName, cellX, cellZ, fencingToken));
    }

    @Override
    public IslandRuntimeSnapshot markSaving(UUID islandId) {
        return cacheChanged(delegate.markSaving(islandId));
    }

    @Override
    public IslandRuntimeSnapshot markSaving(UUID islandId, long fencingToken) {
        return cacheChanged(delegate.markSaving(islandId, fencingToken));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId) {
        return cacheChanged(delegate.markInactive(islandId));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId, long fencingToken) {
        return cacheChanged(delegate.markInactive(islandId, fencingToken));
    }

    @Override
    public IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode) {
        return cacheChanged(delegate.markMigrating(islandId, targetNode));
    }

    @Override
    public IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason) {
        return cacheChanged(delegate.markQuarantined(islandId, reason));
    }

    @Override
    public IslandRuntimeSnapshot setState(UUID islandId, IslandState state) {
        return cacheChanged(delegate.setState(islandId, state));
    }

    @Override
    public Map<String, Long> countsByState() {
        Optional<Map<String, Long>> cached = cachedCountsByState();
        if (cached.isPresent()) {
            return cached.get();
        }
        Map<String, Long> counts = delegate.countsByState();
        cacheCountsByState(counts);
        return counts;
    }

    @Override
    public int markRecoveryRequiredForNode(String nodeId) {
        List<IslandRuntimeSnapshot> affected = delegate.listByNode(nodeId, Integer.MAX_VALUE);
        int changed = delegate.markRecoveryRequiredForNode(nodeId);
        if (changed > 0) {
            for (IslandRuntimeSnapshot runtime : affected) {
                cache(new IslandRuntimeSnapshot(
                    runtime.islandId(),
                    IslandState.RECOVERY_REQUIRED,
                    runtime.activeNode(),
                    runtime.activeWorld(),
                    runtime.cellX(),
                    runtime.cellZ(),
                    runtime.leaseOwner(),
                    runtime.fencingToken(),
                    runtime.activatedAt(),
                    runtime.lastHeartbeat()
                ));
            }
            invalidateRuntimeCounts();
            invalidateNodeRuntimes();
        }
        return changed;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private IslandRuntimeSnapshot cache(IslandRuntimeSnapshot runtime) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandRuntime(runtime.islandId()), runtimeJson(runtime));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return runtime;
    }

    private IslandRuntimeSnapshot cacheChanged(IslandRuntimeSnapshot runtime) {
        cache(runtime);
        invalidateRuntimeCounts();
        invalidateNodeRuntimes();
        return runtime;
    }

    private Optional<List<IslandRuntimeSnapshot>> cachedNodeRuntimes(String nodeId, int limit) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.nodeIslandRuntimes(nodeId, limit));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(runtimesFromJson(json));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheNodeRuntimes(String nodeId, int limit, List<IslandRuntimeSnapshot> runtimes) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.nodeIslandRuntimes(nodeId, limit), runtimesJson(runtimes), "PX", Long.toString(NODE_LIST_CACHE_TTL_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<Map<String, Long>> cachedCountsByState() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandRuntimeCounts());
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(countsFromJson(json));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheCountsByState(Map<String, Long> counts) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandRuntimeCounts(), countsJson(counts), "PX", Long.toString(COUNTS_CACHE_TTL_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void invalidateRuntimeCounts() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.islandRuntimeCounts());
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void invalidateNodeRuntimes() {
        deletePattern("ci:node:*:island-runtimes:*");
    }

    private void deletePattern(String pattern) {
        for (String key : keys(pattern)) {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                redis.command("DEL", key);
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
            }
        }
    }

    private List<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        do {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                String response = redis.command("SCAN", cursor, "MATCH", pattern, "COUNT", "100");
                String[] lines = response.split("\\n");
                cursor = lines.length == 0 || lines[0].isBlank() ? "0" : lines[0];
                for (int i = 1; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        keys.add(lines[i]);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
                return keys;
            }
        } while (!"0".equals(cursor));
        return keys;
    }

    private Optional<IslandRuntimeSnapshot> cachedRuntime(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandRuntime(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new IslandRuntimeSnapshot(
                JsonFields.uuid(json, "islandId", islandId),
                JsonFields.enumValue(IslandState.class, json, "state", IslandState.INACTIVE_READY),
                nullableText(JsonFields.text(json, "activeNode", "")),
                nullableText(JsonFields.text(json, "activeWorld", "")),
                nullableInteger(json, "cellX"),
                nullableInteger(json, "cellZ"),
                nullableText(JsonFields.text(json, "leaseOwner", "")),
                JsonFields.longValue(json, "fencingToken", 0L),
                nullableInstant(JsonFields.text(json, "activatedAt", "")),
                nullableInstant(JsonFields.text(json, "lastHeartbeat", ""))
            ));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String runtimeJson(IslandRuntimeSnapshot runtime) {
        return new StringBuilder("{")
            .append("\"islandId\":\"").append(runtime.islandId()).append("\",")
            .append("\"state\":\"").append(runtime.state().name()).append("\",")
            .append("\"activeNode\":\"").append(escape(runtime.activeNode())).append("\",")
            .append("\"activeWorld\":\"").append(escape(runtime.activeWorld())).append("\",")
            .append("\"cellX\":").append(nullable(runtime.cellX())).append(',')
            .append("\"cellZ\":").append(nullable(runtime.cellZ())).append(',')
            .append("\"leaseOwner\":\"").append(escape(runtime.leaseOwner())).append("\",")
            .append("\"fencingToken\":").append(runtime.fencingToken()).append(',')
            .append("\"activatedAt\":\"").append(runtime.activatedAt() == null ? "" : runtime.activatedAt()).append("\",")
            .append("\"lastHeartbeat\":\"").append(runtime.lastHeartbeat() == null ? "" : runtime.lastHeartbeat()).append("\"")
            .append('}')
            .toString();
    }

    private static String runtimesJson(List<IslandRuntimeSnapshot> runtimes) {
        StringBuilder builder = new StringBuilder("{\"runtimes\":[");
        boolean first = true;
        for (IslandRuntimeSnapshot runtime : runtimes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(runtimeJson(runtime));
        }
        return builder.append("]}").toString();
    }

    private static String nullable(Integer value) {
        return value == null ? "null" : Integer.toString(value);
    }

    private static Integer nullableInteger(String json, String field) {
        if (json.contains("\"" + field + "\":null")) {
            return null;
        }
        return JsonFields.integer(json, field, 0);
    }

    private static Instant nullableInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<IslandRuntimeSnapshot> runtimesFromJson(String json) {
        List<IslandRuntimeSnapshot> runtimes = new ArrayList<>();
        int arrayField = json.indexOf("\"runtimes\":[");
        int index = arrayField < 0 ? 0 : json.indexOf('[', arrayField) + 1;
        while (true) {
            int objectStart = json.indexOf('{', index);
            if (objectStart < 0) {
                return List.copyOf(runtimes);
            }
            int objectEnd = matchingObjectEnd(json, objectStart);
            if (objectEnd < 0) {
                return List.copyOf(runtimes);
            }
            String object = json.substring(objectStart, objectEnd + 1);
            if (object.contains("\"islandId\"")) {
                runtimes.add(runtimeFromJson(object));
            }
            index = objectEnd + 1;
        }
    }

    private static IslandRuntimeSnapshot runtimeFromJson(String json) {
        return new IslandRuntimeSnapshot(
            JsonFields.uuid(json, "islandId", new UUID(0L, 0L)),
            JsonFields.enumValue(IslandState.class, json, "state", IslandState.INACTIVE_READY),
            nullableText(JsonFields.text(json, "activeNode", "")),
            nullableText(JsonFields.text(json, "activeWorld", "")),
            nullableInteger(json, "cellX"),
            nullableInteger(json, "cellZ"),
            nullableText(JsonFields.text(json, "leaseOwner", "")),
            JsonFields.longValue(json, "fencingToken", 0L),
            nullableInstant(JsonFields.text(json, "activatedAt", "")),
            nullableInstant(JsonFields.text(json, "lastHeartbeat", ""))
        );
    }

    private static int matchingObjectEnd(String json, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Map<String, Long> countsFromJson(String json) {
        LinkedHashMap<String, Long> counts = zeroCounts();
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isBlank()) {
            return Map.copyOf(counts);
        }
        for (String pair : trimmed.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon));
            try {
                counts.put(key, Long.parseLong(pair.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
                counts.put(key, 0L);
            }
        }
        return Map.copyOf(counts);
    }

    private static String countsJson(Map<String, Long> counts) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (IslandState state : IslandState.values()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(state.name()).append("\":").append(counts.getOrDefault(state.name(), 0L));
        }
        return builder.append('}').toString();
    }

    private static LinkedHashMap<String, Long> zeroCounts() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (IslandState state : IslandState.values()) {
            counts.put(state.name(), 0L);
        }
        return counts;
    }

    private static String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String nullableText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
