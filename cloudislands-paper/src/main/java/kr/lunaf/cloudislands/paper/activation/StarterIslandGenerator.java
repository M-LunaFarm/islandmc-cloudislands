package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.util.UUID;

@FunctionalInterface
public interface StarterIslandGenerator {
    void generate(Plan plan) throws IOException;

    default void prepareShutdown() throws IOException {
    }

    static StarterIslandGenerator noop() {
        return _plan -> {};
    }

    static StarterIslandGenerator unavailable() {
        return plan -> { throw new IOException("built-in starter island generation is unavailable: " + plan.islandId()); };
    }

    record Plan(UUID islandId, String worldName, int blockX, int surfaceY, int blockZ) {}
}
