package kr.lunaf.cloudislands.velocity.event;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class CoreEventPoller {
    private final CoreApiClient coreApiClient;
    private final Consumer<CoreEventEnvelope> eventHandler;
    private final int batchSize;
    private final Set<String> seenEvents = ConcurrentHashMap.newKeySet();
    private long lastEventSequence;

    public CoreEventPoller(CoreApiClient coreApiClient, CoreEventCodec eventCodec, Consumer<CoreEventEnvelope> eventHandler, int batchSize) {
        this.coreApiClient = coreApiClient;
        this.eventHandler = eventHandler;
        this.batchSize = Math.max(1, batchSize);
    }

    public void pollOnce() {
        coreApiClient.adminEvents().listSince(lastEventSequence, batchSize)
            .thenAccept(this::handleBatch)
            .exceptionally(error -> null);
    }

    private void handleBatch(AdminEventStreamView batch) {
        long oldestSequence = batch.oldestSeq();
        long latestSequence = batch.latestSeq();
        if (latestSequence > 0L && latestSequence < lastEventSequence) {
            lastEventSequence = 0L;
            seenEvents.clear();
        }
        if (lastEventSequence > 0L && oldestSequence > lastEventSequence + 1L) {
            lastEventSequence = oldestSequence - 1L;
            seenEvents.clear();
        }
        for (AdminEventView view : batch.events()) {
            CoreEventEnvelope event = new CoreEventEnvelope(view.seq(), view.type(), view.fields(), view.occurredAt());
            lastEventSequence = Math.max(lastEventSequence, event.sequence());
            if (seenEvents.add(eventKey(event))) {
                eventHandler.accept(event);
            }
        }
        if (seenEvents.size() > 2048) {
            seenEvents.clear();
        }
    }

    private String eventKey(CoreEventEnvelope event) {
        Map<String, String> fields = event.fields();
        String identity = firstPresent(fields, "nodeId", "islandId", "ticketId", "jobId", "playerUuid");
        if (identity.isBlank()) {
            identity = fields.toString();
        }
        return event.type() + "@" + event.occurredAt() + "@" + identity + "@" + fields.getOrDefault("state", "");
    }

    private String firstPresent(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.getOrDefault(key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
