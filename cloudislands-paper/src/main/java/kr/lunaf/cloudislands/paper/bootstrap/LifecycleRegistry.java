package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Logger;

public final class LifecycleRegistry implements AutoCloseable {
    private final Logger logger;
    private final Deque<NamedComponent> started = new ArrayDeque<>();

    public LifecycleRegistry(Logger logger) {
        this.logger = logger;
    }

    public void started(String name, RuntimeComponent component) {
        started.push(new NamedComponent(name, Objects.requireNonNull(component, "component")));
    }

    @Override
    public void close() {
        while (!started.isEmpty()) {
            NamedComponent component = started.pop();
            try {
                component.component().stop();
            } catch (RuntimeException exception) {
                if (logger != null) {
                    logger.warning("Failed to stop CloudIslands component " + component.name() + ": " + exception.getMessage());
                }
            }
        }
    }

    private record NamedComponent(String name, RuntimeComponent component) {
    }
}
