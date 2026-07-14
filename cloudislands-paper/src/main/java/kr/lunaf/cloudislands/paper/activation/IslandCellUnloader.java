package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import kr.lunaf.cloudislands.paper.world.cell.CellPlacementPlan;

@FunctionalInterface
public interface IslandCellUnloader {
    void unload(CellPlacementPlan plan) throws IOException;

    static IslandCellUnloader noop() {
        return _plan -> {};
    }

    static IslandCellUnloader unavailable() {
        return plan -> { throw new IslandCellUnloadException("live cell unload is unavailable: " + plan.islandId()); };
    }
}
