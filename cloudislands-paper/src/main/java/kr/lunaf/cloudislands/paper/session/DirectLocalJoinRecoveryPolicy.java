package kr.lunaf.cloudislands.paper.session;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;

public final class DirectLocalJoinRecoveryPolicy {
    private DirectLocalJoinRecoveryPolicy() {
    }

    public static boolean requiresFallback(
        UUID expectedIslandId,
        String worldName,
        double x,
        double z,
        Collection<ActiveIslandRegistry.ActiveIsland> activeIslands
    ) {
        if (expectedIslandId == null) {
            return false;
        }
        return islandAt(worldName, x, z, activeIslands)
            .map(active -> !active.islandId().equals(expectedIslandId))
            .orElse(true);
    }

    public static Optional<ActiveIslandRegistry.ActiveIsland> islandAt(
        String worldName,
        double x,
        double z,
        Collection<ActiveIslandRegistry.ActiveIsland> activeIslands
    ) {
        if (worldName == null || worldName.isBlank() || activeIslands == null) {
            return Optional.empty();
        }
        return activeIslands.stream()
            .filter(active -> contains(active, worldName, x, z))
            .findFirst();
    }

    private static boolean contains(ActiveIslandRegistry.ActiveIsland active, String worldName, double x, double z) {
        if (active == null || !worldName.equals(active.worldName())) {
            return false;
        }
        int halfSize = Math.max(1, active.islandSize() / 2);
        return x >= active.originX() - halfSize
            && x <= active.originX() + halfSize
            && z >= active.originZ() - halfSize
            && z <= active.originZ() + halfSize;
    }
}
