package kr.lunaf.cloudislands.coreservice.redis;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.job.RedisStreamJobPublisher.RedisStreamWriter;

public final class RedisStreamWriterAdapter implements RedisStreamWriter {
    private final URI redisUri;
    private final AtomicLong failuresTotal = new AtomicLong();

    public RedisStreamWriterAdapter(URI redisUri) {
        this.redisUri = redisUri;
    }

    public long failuresTotal() {
        return failuresTotal.get();
    }

    @Override
    public void xadd(String stream, String... fieldValues) {
        if (fieldValues.length % 2 != 0) {
            throw new IllegalArgumentException("fieldValues must be pairs");
        }
        List<String> command = new ArrayList<>();
        command.add("XADD");
        command.add(stream);
        command.add("*");
        command.addAll(List.of(fieldValues));
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command(command.toArray(String[]::new));
        } catch (IOException exception) {
            failuresTotal.incrementAndGet();
            throw new IllegalStateException("failed to publish redis stream event", exception);
        }
    }
}
