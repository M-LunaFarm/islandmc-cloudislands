package kr.lunaf.cloudislands.coreclient;

import java.util.Map;

public record AdminEventView(long seq, String type, Map<String, String> fields, String occurredAt) {
    public AdminEventView {
        type = type == null ? "" : type;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        occurredAt = occurredAt == null ? "" : occurredAt;
    }
}
