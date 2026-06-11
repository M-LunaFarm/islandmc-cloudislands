package kr.lunaf.cloudislands.coreservice.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InMemoryGlobalEventPublisher implements GlobalEventPublisher {
    private final List<EventRecord> events = new ArrayList<>();

    @Override
    public synchronized void publish(String eventType, Map<String, String> fields) {
        events.add(new EventRecord(eventType, Map.copyOf(fields), Instant.now()));
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
                .append("\"occurredAt\":\"").append(event.occurredAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    public record EventRecord(String type, Map<String, String> fields, Instant occurredAt) {}
}
