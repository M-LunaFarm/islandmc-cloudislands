package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;

@FunctionalInterface
public interface IslandWorldFlush {
    void flush(ActiveIslandRegistry.ActiveIsland activeIsland, String reason) throws IOException;

    default void prepareShutdown(Iterable<ActiveIslandRegistry.ActiveIsland> activeIslands) throws IOException {
        if (activeIslands == null) {
            return;
        }
        for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands) {
            flush(activeIsland, "AUTO");
        }
    }

    static IslandWorldFlush noop() {
        return (ignored, reason) -> {};
    }
}
