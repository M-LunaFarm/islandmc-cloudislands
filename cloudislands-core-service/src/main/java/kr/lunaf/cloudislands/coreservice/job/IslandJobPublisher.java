package kr.lunaf.cloudislands.coreservice.job;

import kr.lunaf.cloudislands.protocol.job.IslandJob;

public interface IslandJobPublisher {
    void publish(IslandJob job);
}
