package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record GlobalEventSnapshot(long sequence, String type, Map<String, String> fields, Instant occurredAt) {
    public GlobalEventSnapshot(String type, Map<String, String> fields, Instant occurredAt) {
        this(0L, type, fields, occurredAt);
    }
}
