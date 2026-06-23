package kr.lunaf.cloudislands.coreservice.event;

import java.util.List;
import java.util.Map;

public final class FailFastGlobalEventPublisher implements GlobalEventPublisher {
    private final List<GlobalEventPublisher> delegates;

    public FailFastGlobalEventPublisher(List<GlobalEventPublisher> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        for (GlobalEventPublisher delegate : delegates) {
            delegate.publish(eventType, fields);
        }
    }
}
