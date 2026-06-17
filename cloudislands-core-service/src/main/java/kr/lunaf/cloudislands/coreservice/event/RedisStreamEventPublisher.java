package kr.lunaf.cloudislands.coreservice.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.job.RedisStreamJobPublisher.RedisStreamWriter;

public final class RedisStreamEventPublisher implements GlobalEventPublisher {
    private final RedisStreamWriter writer;

    public RedisStreamEventPublisher(RedisStreamWriter writer) {
        this.writer = writer;
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        List<String> values = new ArrayList<>();
        values.add("type");
        values.add(eventType);
        String cacheTargets = cacheTargets(eventType);
        if (!cacheTargets.isBlank()) {
            values.add("cacheTargets");
            values.add(cacheTargets);
        }
        String cacheKeys = cacheKeys(eventType, fields);
        if (!cacheKeys.isBlank()) {
            values.add("cacheKeys");
            values.add(cacheKeys);
        }
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            values.add(entry.getKey());
            values.add(entry.getValue());
        }
        writer.xadd(RedisKeys.eventsStream(), values.toArray(String[]::new));
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
