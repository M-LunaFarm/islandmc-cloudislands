package kr.lunaf.cloudislands.coreservice.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;

final class JobCompletionEventBuffer implements GlobalEventPublisher {
    private final ThreadLocal<List<BufferedEvent>> events = new ThreadLocal<>();

    void begin() {
        events.set(new ArrayList<>());
    }

    List<BufferedEvent> drain() {
        List<BufferedEvent> buffered = events.get();
        events.remove();
        return buffered == null ? List.of() : List.copyOf(buffered);
    }

    void clear() {
        events.remove();
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        List<BufferedEvent> buffered = events.get();
        if (buffered == null) {
            throw new IllegalStateException("job completion event buffer is not active");
        }
        buffered.add(new BufferedEvent(eventType == null ? "" : eventType, Map.copyOf(fields == null ? Map.of() : fields)));
    }

    record BufferedEvent(String eventType, Map<String, String> fields) {
    }
}
