package kr.lunaf.cloudislands.coreservice.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;

public final class InMemoryJobCompletionReceiptStore implements JobCompletionReceiptStore {
    private final Map<UUID, Receipt> receiptsByJobId = new HashMap<>();
    private final Map<UUID, Long> nextVersionsByIslandId = new HashMap<>();

    @Override
    public synchronized RecordOutcome record(JobCompletionRequest request) {
        Receipt existing = receiptsByJobId.get(request.job().jobId());
        if (existing == null) {
            long version = nextVersionsByIslandId.merge(request.job().islandId(), 1L, Long::sum);
            JobClaimLease lease = request.job().claimLease();
            receiptsByJobId.put(request.job().jobId(), new Receipt(
                request.requestHash(),
                version,
                lease.claimedByNode(),
                lease.claimToken(),
                lease.claimEpoch(),
                request.requestPayloadJson()
            ));
            return new RecordOutcome(RecordResult.NEW, version);
        }
        RecordResult result = existing.requestHash().equals(request.requestHash()) ? RecordResult.REPLAY : RecordResult.CONFLICT;
        return new RecordOutcome(result, existing.aggregateVersion());
    }

    @Override
    public synchronized ReplayResult verifyCommitted(UUID jobId, String nodeId, JobClaimLease claimLease, Map<String, String> completionPayload) {
        Receipt receipt = receiptsByJobId.get(jobId);
        if (receipt == null) {
            return ReplayResult.MISSING;
        }
        boolean identityMatches = claimLease != null
            && claimLease.matches(jobId, nodeId)
            && receipt.claimantNode().equals(claimLease.claimedByNode())
            && receipt.claimToken().equals(claimLease.claimToken())
            && receipt.claimEpoch() == claimLease.claimEpoch();
        boolean payloadMatches = JobCompletionRequest.completionPayloadMatches(receipt.requestPayloadJson(), completionPayload);
        return identityMatches && payloadMatches ? ReplayResult.MATCH : ReplayResult.CONFLICT;
    }

    @Override
    public synchronized void forget(UUID jobId, String requestHash) {
        Receipt receipt = receiptsByJobId.get(jobId);
        if (receipt != null && requestHash.equals(receipt.requestHash())) {
            receiptsByJobId.remove(jobId);
        }
    }

    private record Receipt(String requestHash, long aggregateVersion, String claimantNode, String claimToken, long claimEpoch, String requestPayloadJson) {
    }
}
