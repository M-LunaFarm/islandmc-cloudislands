package kr.lunaf.cloudislands.coreservice.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InMemoryJobCompletionReceiptStore implements JobCompletionReceiptStore {
    private final Map<UUID, String> hashesByJobId = new HashMap<>();

    @Override
    public synchronized RecordResult record(JobCompletionRequest request) {
        String existing = hashesByJobId.get(request.job().jobId());
        if (existing == null) {
            hashesByJobId.put(request.job().jobId(), request.requestHash());
            return RecordResult.NEW;
        }
        return existing.equals(request.requestHash()) ? RecordResult.REPLAY : RecordResult.CONFLICT;
    }

    @Override
    public synchronized void forget(UUID jobId, String requestHash) {
        if (requestHash.equals(hashesByJobId.get(jobId))) {
            hashesByJobId.remove(jobId);
        }
    }
}
