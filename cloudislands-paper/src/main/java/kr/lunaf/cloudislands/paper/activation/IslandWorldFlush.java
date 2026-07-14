package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;

@FunctionalInterface
public interface IslandWorldFlush {
    void flush(ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException;

    static IslandWorldFlush noop() {
        return ignored -> {};
    }
}
