package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.bukkit.block.Block;

public final class ProtectionController {
    private final RegionIndex regionIndex;
    private final LocalIslandPermissionCache permissionCache;

    public ProtectionController(RegionIndex regionIndex, LocalIslandPermissionCache permissionCache) {
        this.regionIndex = regionIndex;
        this.permissionCache = permissionCache;
    }

    public PermissionResult checkBlock(UUID playerUuid, String world, int blockX, int blockY, int blockZ, IslandPermission permission) {
        return regionIndex.find(world, blockX, blockZ)
            .map(region -> permissionCache.allowed(region.islandId(), playerUuid, permission, false)
                ? PermissionResult.allow(IslandRole.MEMBER)
                : PermissionResult.deny("DEFAULT_DENY", IslandRole.VISITOR))
            .orElseGet(() -> PermissionResult.deny("OUTSIDE_ISLAND", IslandRole.VISITOR));
    }

    public PermissionResult checkSystem(Block block, IslandPermission permission) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ())
            .map(region -> PermissionResult.deny("SYSTEM_PROTECTED", IslandRole.VISITOR))
            .orElseGet(() -> PermissionResult.allow(IslandRole.OWNER));
    }
}
