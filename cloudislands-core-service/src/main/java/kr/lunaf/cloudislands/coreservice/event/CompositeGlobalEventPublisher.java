package kr.lunaf.cloudislands.coreservice.event;

import java.util.List;
import java.util.Map;

public final class CompositeGlobalEventPublisher implements GlobalEventPublisher {
    private final List<GlobalEventPublisher> delegates;

    public CompositeGlobalEventPublisher(List<GlobalEventPublisher> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void publish(String eventType, Map<String, String> fields) {
        for (GlobalEventPublisher delegate : delegates) {
            try {
                delegate.publish(eventType, fields);
            } catch (RuntimeException exception) {
                // Event streams are cross-node cache invalidation and observability channels.
                // A Redis outage must not fail the authoritative PostgreSQL/Core write path.
            }
        }
    }
}
