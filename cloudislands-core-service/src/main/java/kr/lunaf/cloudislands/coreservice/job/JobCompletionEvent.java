package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import java.util.UUID;

public record JobCompletionEvent(UUID eventId, UUID aggregateId, long aggregateVersion, String eventType, Map<String, String> fields) {
    public JobCompletionEvent {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }
}
