package kr.lunaf.cloudislands.coreservice.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;

public final class InMemoryGlobalEventPublisher implements GlobalEventPublisher {
    private final List<EventRecord> events = new ArrayList<>();

    @Override
    public synchronized void publish(String eventType, Map<String, String> fields) {
        events.add(new EventRecord(eventType, enrichedFields(eventType, fields), Instant.now()));
    }

    public synchronized String toJson() {
        StringBuilder builder = new StringBuilder("{\"events\":[");
        boolean first = true;
        for (EventRecord event : events) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"type\":\"").append(event.type()).append("\",")
                .append("\"fields\":").append(fieldsJson(event.fields())).append(',')
                .append("\"occurredAt\":\"").append(event.occurredAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    public synchronized long countByType(String type) {
        return events.stream().filter(event -> event.type().equals(type)).count();
    }

    public synchronized Map<String, Long> countsByField(String type, String fieldName) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EventRecord event : events) {
            if (!event.type().equals(type)) {
                continue;
            }
            String value = event.fields().getOrDefault(fieldName, "");
            counts.put(value, counts.getOrDefault(value, 0L) + 1L);
        }
        return counts;
    }

    private String fieldsJson(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append('}').toString();
    }

    private Map<String, String> enrichedFields(String eventType, Map<String, String> fields) {
        Map<String, String> enriched = new LinkedHashMap<>(fields);
        String cacheTargets = cacheTargets(eventType);
        if (!cacheTargets.isBlank()) {
            enriched.put("cacheTargets", cacheTargets);
        }
        return Map.copyOf(enriched);
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record EventRecord(String type, Map<String, String> fields, Instant occurredAt) {}
}
