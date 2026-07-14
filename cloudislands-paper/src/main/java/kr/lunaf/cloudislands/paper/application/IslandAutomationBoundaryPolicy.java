package kr.lunaf.cloudislands.paper.application;

import java.util.Objects;
import java.util.UUID;

public final class IslandAutomationBoundaryPolicy {
    private IslandAutomationBoundaryPolicy() {
    }

    public static boolean crossesBoundary(
        UUID sourceIsland,
        UUID targetIsland,
        boolean sourceMigrating,
        boolean targetMigrating
    ) {
        return sourceMigrating || targetMigrating || !Objects.equals(sourceIsland, targetIsland);
    }
}
