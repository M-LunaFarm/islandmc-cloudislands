package kr.lunaf.cloudislands.velocity.event;

import java.util.Map;

public record CoreEventEnvelope(
    long sequence,
    String type,
    Map<String, String> fields,
    String occurredAt
) {
}
