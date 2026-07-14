package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;

@FunctionalInterface
public interface IslandCellUnloader {
    void unload(IslandCellRange range) throws IOException;

    static IslandCellUnloader noop() {
        return _range -> {};
    }

    static IslandCellUnloader unavailable() {
        return range -> { throw new IslandCellUnloadException("live cell unload is unavailable: " + range.islandId()); };
    }
}
