package kr.lunaf.cloudislands.coreservice.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InMemoryJobCompletionReceiptStore implements JobCompletionReceiptStore {
    private final Map<UUID, String> hashesByJobId = new HashMap<>();
    private final Map<UUID, Long> versionsByJobId = new HashMap<>();
    private final Map<UUID, Long> nextVersionsByIslandId = new HashMap<>();

    @Override
    public synchronized RecordOutcome record(JobCompletionRequest request) {
        String existing = hashesByJobId.get(request.job().jobId());
        if (existing == null) {
            long version = nextVersionsByIslandId.merge(request.job().islandId(), 1L, Long::sum);
            hashesByJobId.put(request.job().jobId(), request.requestHash());
            versionsByJobId.put(request.job().jobId(), version);
            return new RecordOutcome(RecordResult.NEW, version);
        }
        RecordResult result = existing.equals(request.requestHash()) ? RecordResult.REPLAY : RecordResult.CONFLICT;
        return new RecordOutcome(result, versionsByJobId.getOrDefault(request.job().jobId(), 0L));
    }

    @Override
    public synchronized void forget(UUID jobId, String requestHash) {
        if (requestHash.equals(hashesByJobId.get(jobId))) {
            hashesByJobId.remove(jobId);
            versionsByJobId.remove(jobId);
        }
    }
}
