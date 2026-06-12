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
    private static final int MAX_EVENTS = 4096;
    private final List<EventRecord> events = new ArrayList<>();
    private long nextSequence = 1L;

    @Override
    public synchronized void publish(String eventType, Map<String, String> fields) {
        events.add(new EventRecord(nextSequence++, eventType, enrichedFields(eventType, fields), Instant.now()));
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
    }

    public synchronized String toJson() {
        return toJson(MAX_EVENTS);
    }

    public synchronized String toJson(int limit) {
        return toJson(limit, 0L);
    }

    public synchronized String toJson(int limit, long sinceSequence) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_EVENTS));
        int firstAfterSequence = 0;
        while (firstAfterSequence < events.size() && events.get(firstAfterSequence).sequence() <= sinceSequence) {
            firstAfterSequence++;
        }
        int from = sinceSequence > 0L ? firstAfterSequence : Math.max(0, events.size() - safeLimit);
        int to = sinceSequence > 0L ? Math.min(events.size(), from + safeLimit) : events.size();
        long oldestSequence = events.isEmpty() ? nextSequence : events.get(0).sequence();
        long latestSequence = nextSequence - 1L;
        StringBuilder builder = new StringBuilder("{\"oldestSeq\":")
            .append(oldestSequence)
            .append(",\"latestSeq\":")
            .append(latestSequence)
            .append(",\"events\":[");
        boolean first = true;
        for (int i = from; i < to; i++) {
            EventRecord event = events.get(i);
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"seq\":").append(event.sequence()).append(',')
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
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    public record EventRecord(long sequence, String type, Map<String, String> fields, Instant occurredAt) {}
}
