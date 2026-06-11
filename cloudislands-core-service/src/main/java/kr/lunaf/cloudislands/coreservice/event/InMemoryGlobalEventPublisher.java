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
                .append("\"fields\":").append(fieldsJson(event.fields())).append(',')
                .append("\"occurredAt\":\"").append(event.occurredAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    public synchronized long countByType(String type) {
        return events.stream().filter(event -> event.type().equals(type)).count();
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record EventRecord(String type, Map<String, String> fields, Instant occurredAt) {}
}
