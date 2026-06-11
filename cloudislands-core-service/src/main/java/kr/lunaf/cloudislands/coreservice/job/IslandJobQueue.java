package kr.lunaf.cloudislands.coreservice.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public interface IslandJobQueue extends IslandJobPublisher {
    List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    Optional<IslandJob> findClaimed(UUID jobId);
    void complete(String nodeId, UUID jobId);
    void fail(String nodeId, UUID jobId, String errorMessage);
}
