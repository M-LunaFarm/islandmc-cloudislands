package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.common.protection.RegionIndex;

public final class ProtectionController {
    private final RegionIndex regionIndex;

    public ProtectionController(RegionIndex regionIndex) {
        this.regionIndex = regionIndex;
    }

    public PermissionResult checkBlock(UUID playerUuid, String world, int blockX, int blockY, int blockZ, IslandPermission permission) {
        return regionIndex.find(world, blockX, blockZ)
            .map(region -> PermissionResult.deny("CACHE_MISS", IslandRole.VISITOR))
            .orElseGet(() -> PermissionResult.deny("OUTSIDE_ISLAND", IslandRole.VISITOR));
    }
}
