package kr.lunaf.cloudislands.coreservice.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.job.RedisStreamJobPublisher.RedisStreamWriter;

public final class RedisStreamEventPublisher implements GlobalEventPublisher {
    private final RedisStreamWriter writer;

    public RedisStreamEventPublisher(RedisStreamWriter writer) {
        this.writer = writer;
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        List<String> values = new ArrayList<>();
        values.add("type");
        values.add(eventType);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            values.add(entry.getKey());
            values.add(entry.getValue());
        }
        writer.xadd(RedisKeys.eventsStream(), values.toArray(String[]::new));
    }
}
