package kr.lunaf.cloudislands.coreservice.job;

import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

public final class RedisStreamJobPublisher implements IslandJobPublisher {
    private final RedisStreamWriter streamWriter;

    public RedisStreamJobPublisher(RedisStreamWriter streamWriter) {
        this.streamWriter = streamWriter;
    }

    @Override
    public void publish(IslandJob job) {
        streamWriter.xadd(RedisKeys.jobsStream(), "type", job.type().name(), "jobId", job.jobId().toString(), "islandId", job.islandId().toString(), "targetNode", job.targetNode());
    }

    public interface RedisStreamWriter {
        void xadd(String stream, String... fieldValues);
    }
}
