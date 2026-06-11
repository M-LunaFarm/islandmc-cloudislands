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
        records.add(new AuditRecord(UUID.randomUUID(), actorUuid, actorType, action, targetType, targetId, Map.copyOf(payload), Instant.now()));
    }

    public synchronized String toJson() {
        StringBuilder builder = new StringBuilder("{\"audit\":[");
        boolean first = true;
        for (AuditRecord record : records) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"id\":\"").append(record.id()).append("\",")
                .append("\"actorType\":\"").append(record.actorType()).append("\",")
                .append("\"action\":\"").append(record.action()).append("\",")
                .append("\"targetType\":\"").append(record.targetType()).append("\",")
                .append("\"targetId\":\"").append(record.targetId()).append("\",")
                .append("\"createdAt\":\"").append(record.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    public record AuditRecord(UUID id, UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload, Instant createdAt) {}
}
