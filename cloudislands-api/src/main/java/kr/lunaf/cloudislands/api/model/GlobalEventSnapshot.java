package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record GlobalEventSnapshot(long sequence, String type, Map<String, String> fields, Instant occurredAt) {
    public GlobalEventSnapshot {
        type = type == null ? "" : type;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        occurredAt = occurredAt == null ? Instant.EPOCH : occurredAt;
    }

    public GlobalEventSnapshot(String type, Map<String, String> fields, Instant occurredAt) {
        this(0L, type, fields, occurredAt);
    }

    public String eventId() {
        String explicitId = fields.getOrDefault("eventId", "");
        if (!explicitId.isBlank()) {
            return explicitId;
        }
        if (sequence > 0L) {
            return Long.toString(sequence);
        }
        return type + ":" + occurredAt + ":" + Integer.toUnsignedString(fields.hashCode());
    }
}
