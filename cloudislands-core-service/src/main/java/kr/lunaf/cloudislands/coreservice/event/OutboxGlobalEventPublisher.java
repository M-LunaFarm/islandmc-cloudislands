package kr.lunaf.cloudislands.coreservice.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionEvent;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxStore;

public final class OutboxGlobalEventPublisher implements GlobalEventPublisher {
    private static final UUID GLOBAL_AGGREGATE_ID = new UUID(0L, 0L);

    private final JobCompletionOutboxStore outbox;

    public OutboxGlobalEventPublisher(JobCompletionOutboxStore outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        outbox.append(List.of(new JobCompletionEvent(UUID.randomUUID(), aggregateId(fields), 0L, eventType, fields)));
    }

    private UUID aggregateId(Map<String, String> fields) {
        if (fields == null) {
            return GLOBAL_AGGREGATE_ID;
        }
        UUID islandId = uuid(fields.get("islandId"));
        if (islandId != null) {
            return islandId;
        }
        UUID ticketId = uuid(fields.get("ticketId"));
        if (ticketId != null) {
            return ticketId;
        }
        UUID playerUuid = uuid(fields.get("playerUuid"));
        return playerUuid == null ? GLOBAL_AGGREGATE_ID : playerUuid;
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
