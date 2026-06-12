package kr.lunaf.cloudislands.coreservice.event;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class CompositeGlobalEventPublisher implements GlobalEventPublisher {
    private static final Logger LOGGER = Logger.getLogger(CompositeGlobalEventPublisher.class.getName());
    private final List<GlobalEventPublisher> delegates;
    private volatile long lastFailureLogMillis;

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
                logFailure(delegate, exception);
            }
        }
    }

    private void logFailure(GlobalEventPublisher delegate, RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogMillis < 30_000L) {
            return;
        }
        lastFailureLogMillis = now;
        LOGGER.warning("CloudIslands event publisher failed: " + delegate.getClass().getSimpleName() + " " + exception.getMessage());
    }
}
