package kr.lunaf.cloudislands.coreservice.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;

public interface IslandJobQueue extends IslandJobPublisher {
    List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    Optional<IslandJob> findClaimed(UUID jobId);
    default Optional<IslandJob> findClaimed(UUID jobId, JobClaimLease claimLease) {
        return Optional.empty();
    }
    boolean complete(String nodeId, UUID jobId);
    default boolean complete(String nodeId, UUID jobId, JobClaimLease claimLease) {
        return false;
    }
    boolean fail(String nodeId, UUID jobId, String errorMessage);
    default boolean fail(String nodeId, UUID jobId, JobClaimLease claimLease, String errorMessage) {
        return false;
    }
    boolean retry(UUID jobId);
    boolean cancel(UUID jobId);
}
