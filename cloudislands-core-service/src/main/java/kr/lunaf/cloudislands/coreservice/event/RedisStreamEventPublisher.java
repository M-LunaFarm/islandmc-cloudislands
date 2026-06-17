package kr.lunaf.cloudislands.coreservice.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.job.RedisStreamJobPublisher.RedisStreamWriter;

public final class RedisStreamEventPublisher implements GlobalEventPublisher {
    private final RedisStreamWriter writer;
    private final AtomicLong failures = new AtomicLong();

    public RedisStreamEventPublisher(RedisStreamWriter writer) {
        this.writer = writer;
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        String safeEventType = eventType == null ? "" : eventType;
        List<String> values = new ArrayList<>();
        values.add("type");
        values.add(safeEventType);
        Map<String, String> safeFields = fields == null ? Map.of() : fields;
        Map<String, String> enriched = new LinkedHashMap<>();
        safeFields.forEach((key, value) -> {
            if (key != null && value != null) {
                enriched.put(key, value);
            }
        });
        String cacheTargets = cacheTargets(safeEventType);
        if (!cacheTargets.isBlank()) {
            enriched.putIfAbsent("cacheTargets", cacheTargets);
        }
        String cacheKeys = cacheKeys(safeEventType, safeFields);
        if (!cacheKeys.isBlank()) {
            enriched.putIfAbsent("cacheKeys", cacheKeys);
        }
        for (Map.Entry<String, String> entry : enriched.entrySet()) {
            values.add(entry.getKey());
            values.add(entry.getValue());
        }
        try {
            writer.xadd(RedisKeys.eventsStream(), values.toArray(String[]::new));
        } catch (RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    private String cacheTargets(String eventType) {
        try {
            Set<CacheInvalidationPlan.CacheTarget> targets = CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(eventType));
            StringBuilder builder = new StringBuilder();
            for (CacheInvalidationPlan.CacheTarget target : targets) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(target.name());
            }
            return builder.toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String cacheKeys(String eventType, Map<String, String> fields) {
        try {
            CloudIslandEventType type = CloudIslandEventType.valueOf(eventType);
            java.util.UUID islandId = uuid(fields.get("islandId"));
            String addonId = fields.getOrDefault("addonId", "");
            return String.join(",", CacheInvalidationPlan.redisKeysFor(type, islandId, addonId));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private java.util.UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
