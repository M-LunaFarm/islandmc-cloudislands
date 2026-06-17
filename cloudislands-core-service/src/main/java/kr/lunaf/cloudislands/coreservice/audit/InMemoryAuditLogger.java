package kr.lunaf.cloudislands.coreservice.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryAuditLogger implements AuditLogger {
    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public synchronized void log(UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload) {
        records.add(new AuditRecord(UUID.randomUUID(), actorUuid, actorType, action, targetType, targetId, payload == null ? Map.of() : Map.copyOf(payload), Instant.now()));
    }

    public synchronized String toJson() {
        return toJson(100);
    }

    @Override
    public synchronized String toJson(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        StringBuilder builder = new StringBuilder("{\"audit\":[");
        boolean first = true;
        int emitted = 0;
        for (int index = records.size() - 1; index >= 0 && emitted < safeLimit; index--) {
            AuditRecord record = records.get(index);
            if (!first) {
                builder.append(',');
            }
            first = false;
            emitted++;
            builder.append('{')
                .append("\"id\":\"").append(record.id()).append("\",")
                .append("\"actorUuid\":").append(record.actorUuid() == null || record.actorUuid().equals(new UUID(0L, 0L)) ? "null" : "\"" + record.actorUuid() + "\"").append(',')
                .append("\"actorType\":\"").append(escape(record.actorType())).append("\",")
                .append("\"action\":\"").append(escape(record.action())).append("\",")
                .append("\"targetType\":\"").append(escape(record.targetType())).append("\",")
                .append("\"targetId\":\"").append(escape(record.targetId())).append("\",")
                .append("\"payload\":").append(payloadJson(record.payload())).append(',')
                .append("\"createdAt\":\"").append(record.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String payloadJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
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

    public record AuditRecord(UUID id, UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload, Instant createdAt) {}
}
