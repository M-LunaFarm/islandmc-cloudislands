package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import java.util.UUID;

public record JobCompletionOutboxEntry(UUID eventId, UUID aggregateId, long aggregateVersion, String eventType, Map<String, String> fields, int attempts) {
    public JobCompletionOutboxEntry {
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }
}
