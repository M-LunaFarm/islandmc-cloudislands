package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

final class JobCompletionCoordinator {
    private final JobCompletionBackend backend;
    private final JobCompletionEventBuffer eventBuffer;
    private final JobCompletionReceiptStore receipts;
    private final JobCompletionOutboxStore outbox;
    private final JobCompletionOutboxDispatcher dispatcher;

    JobCompletionCoordinator(JobCompletionBackend backend, JobCompletionEventBuffer eventBuffer, JobCompletionReceiptStore receipts, JobCompletionOutboxStore outbox, JobCompletionOutboxDispatcher dispatcher) {
        this.backend = backend;
        this.eventBuffer = eventBuffer;
        this.receipts = receipts;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
    }

    JobCompletionResult completed(IslandJob claimedJob, Map<String, String> completionPayload) {
        JobCompletionRequest request = JobCompletionRequest.completed(claimedJob, completionPayload);
        JobCompletionReceiptStore.RecordOutcome record = receipts.record(request);
        if (record.result() == JobCompletionReceiptStore.RecordResult.CONFLICT) {
            throw new JobCompletionConflictException("job completion request hash differs from the committed receipt");
        }
        if (record.result() == JobCompletionReceiptStore.RecordResult.REPLAY) {
            dispatcher.dispatchDue();
            return new JobCompletionResult(true, request.requestHash());
        }
        eventBuffer.begin();
        java.util.List<JobCompletionEventBuffer.BufferedEvent> bufferedEvents;
        try {
            backend.completed(request.job());
            bufferedEvents = eventBuffer.drain();
        } catch (RuntimeException exception) {
            eventBuffer.clear();
            receipts.forget(request.job().jobId(), request.requestHash());
            throw exception;
        }
        outbox.append(toOutboxEvents(request, record.aggregateVersion(), bufferedEvents));
        dispatcher.dispatchDue();
        return new JobCompletionResult(false, request.requestHash());
    }

    private java.util.List<JobCompletionEvent> toOutboxEvents(JobCompletionRequest request, long aggregateVersion, java.util.List<JobCompletionEventBuffer.BufferedEvent> bufferedEvents) {
        UUID aggregateId = request.job().islandId();
        return bufferedEvents.stream()
            .map(event -> new JobCompletionEvent(UUID.randomUUID(), aggregateId, aggregateVersion, event.eventType(), event.fields()))
            .toList();
    }
}
