package kr.lunaf.cloudislands.coreservice.job;

import java.util.List;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public interface IslandJobQueue extends IslandJobPublisher {
    List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    void complete(String nodeId, java.util.UUID jobId);
    void fail(String nodeId, java.util.UUID jobId, String errorMessage);
}
