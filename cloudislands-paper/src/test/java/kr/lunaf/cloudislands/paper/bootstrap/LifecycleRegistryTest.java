package kr.lunaf.cloudislands.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleRegistryTest {
    @Test
    void stopsStartedComponentsInReverseOrder() {
        LifecycleRegistry lifecycle = new LifecycleRegistry(null);
        List<String> stopped = new ArrayList<>();

        lifecycle.started("first", () -> stopped.add("first"));
        lifecycle.started("second", () -> stopped.add("second"));

        lifecycle.close();

        assertEquals(List.of("second", "first"), stopped);
    }

    @Test
    void continuesStoppingAfterComponentFailure() {
        LifecycleRegistry lifecycle = new LifecycleRegistry(null);
        List<String> stopped = new ArrayList<>();

        lifecycle.started("first", () -> stopped.add("first"));
        lifecycle.started("broken", () -> {
            stopped.add("broken");
            throw new IllegalStateException("boom");
        });
        lifecycle.started("last", () -> stopped.add("last"));

        lifecycle.close();

        assertEquals(List.of("last", "broken", "first"), stopped);
    }
}
