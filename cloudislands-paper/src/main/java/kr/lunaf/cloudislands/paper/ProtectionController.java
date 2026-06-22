package kr.lunaf.cloudislands.paper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.common.protection.ProtectionDecisionPolicy;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.bukkit.block.Block;

public final class ProtectionController {
    private static final String OWNER_ROLE_KEY = "OWNER";
    private static final String VISITOR_ROLE_KEY = "VISITOR";
    private static final String BANNED_ROLE_KEY = "BANNED";

    private final RegionIndex regionIndex;
    private final LocalIslandPermissionCache permissionCache;
    private final Set<UUID> migratingIslands = ConcurrentHashMap.newKeySet();

    public ProtectionController(RegionIndex regionIndex, LocalIslandPermissionCache permissionCache) {
        this.regionIndex = regionIndex;
        this.permissionCache = permissionCache;
    }

    public void registerIsland(UUID islandId, String worldName, int originX, int originZ, int islandSize, int cellX, int cellZ) {
        int half = Math.max(1, islandSize / 2);
        regionIndex.add(new IslandRegion(islandId, worldName, originX - half, originX + half, originZ - half, originZ + half, cellX, cellZ));
    }

    public void unregisterIsland(UUID islandId) {
        regionIndex.removeIsland(islandId);
        permissionCache.invalidate(islandId);
        migratingIslands.remove(islandId);
    }

    public void markMigrating(UUID islandId) {
        if (islandId != null) {
            migratingIslands.add(islandId);
        }
    }

    public void clearMigrating(UUID islandId) {
        if (islandId != null) {
            migratingIslands.remove(islandId);
        }
    }

    public boolean migrating(Block block) {
        return islandAt(block).map(migratingIslands::contains).orElse(false);
    }

    public java.util.Optional<UUID> islandAt(Block block) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ()).map(IslandRegion::islandId);
    }

    public java.util.Optional<UUID> islandAt(String worldName, int blockX, int blockZ) {
        return regionIndex.find(worldName, blockX, blockZ).map(IslandRegion::islandId);
    }

    public java.util.Optional<IslandRegion> regionAt(Block block) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ());
    }

    public java.util.Optional<IslandRegion> region(UUID islandId) {
        return regionIndex.findIsland(islandId);
    }

    public int indexedChunkCount() {
        return regionIndex.indexedChunkCount();
    }

    public int indexedIslandCount() {
        return regionIndex.indexedIslandCount();
    }

    public int migratingIslandCount() {
        return migratingIslands.size();
    }

    public String synchronousDecisionPolicy() {
        return ProtectionDecisionPolicy.HOT_PATH_POLICY;
    }

    public IslandRole role(UUID islandId, UUID playerUuid) {
        return permissionCache.role(islandId, playerUuid);
    }

    public java.util.List<String> roleCatalog(UUID islandId, boolean includeVisitor) {
        return permissionCache.roleCatalog(islandId, includeVisitor);
    }

    public boolean memberOrTrusted(UUID islandId, UUID playerUuid) {
        String roleKey = permissionCache.roleKey(islandId, playerUuid);
        return !roleKey.equals(VISITOR_ROLE_KEY) && !roleKey.equals(BANNED_ROLE_KEY);
    }

    public PermissionResult checkBlock(UUID playerUuid, String world, int blockX, int blockY, int blockZ, IslandPermission permission) {
        return checkBlock(playerUuid, world, blockX, blockY, blockZ, permission, false);
    }

    public PermissionResult checkBlock(UUID playerUuid, String world, int blockX, int blockY, int blockZ, IslandPermission permission, boolean adminBypass) {
        return regionIndex.find(world, blockX, blockZ)
            .map(region -> {
                if (migratingIslands.contains(region.islandId())) {
                    return PermissionResult.deny("ISLAND_MIGRATING", IslandRole.VISITOR);
                }
                String roleKey = adminBypass ? OWNER_ROLE_KEY : permissionCache.roleKey(region.islandId(), playerUuid);
                IslandRole resultRole = legacyRole(roleKey);
                if (permissionCache.allowed(region.islandId(), playerUuid, permission, adminBypass)) {
                    return PermissionResult.allow(resultRole);
                }
                IslandFlag visitorFlag = visitorFlag(permission);
                if (roleKey.equals(VISITOR_ROLE_KEY) && visitorFlag != null && permissionCache.flagAllowed(region.islandId(), visitorFlag)) {
                    return PermissionResult.allow(IslandRole.VISITOR);
                }
                return PermissionResult.deny("DEFAULT_DENY", resultRole);
            })
            .orElseGet(() -> PermissionResult.deny("OUTSIDE_ISLAND", IslandRole.VISITOR));
    }

    public PermissionResult checkSystem(Block block, IslandPermission permission) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ())
            .map(region -> migratingIslands.contains(region.islandId())
                ? PermissionResult.deny("ISLAND_MIGRATING", IslandRole.VISITOR)
                : PermissionResult.deny("SYSTEM_PROTECTED", IslandRole.VISITOR))
            .orElseGet(() -> PermissionResult.allow(IslandRole.OWNER));
    }

    public PermissionResult checkSystemFlag(Block block, IslandFlag flag) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ())
            .map(region -> {
                if (migratingIslands.contains(region.islandId())) {
                    return PermissionResult.deny("ISLAND_MIGRATING", IslandRole.VISITOR);
                }
                return permissionCache.flagAllowed(region.islandId(), flag)
                    ? PermissionResult.allow(IslandRole.OWNER)
                    : PermissionResult.deny(flag.name() + "_DISABLED", IslandRole.VISITOR);
            })
            .orElseGet(() -> PermissionResult.allow(IslandRole.OWNER));
    }

    private IslandFlag visitorFlag(IslandPermission permission) {
        return switch (permission) {
            case INTERACT,
                USE_DOOR,
                USE_BUTTON,
                USE_PRESSURE_PLATE,
                USE_REDSTONE,
                USE_SPAWNER,
                USE_ANVIL,
                USE_ENCHANT_TABLE,
                USE_BREWING_STAND -> IslandFlag.VISITOR_INTERACT;
            case OPEN_CONTAINER -> IslandFlag.VISITOR_CONTAINER;
            case PICKUP_ITEM -> IslandFlag.VISITOR_PICKUP;
            case DROP_ITEM -> IslandFlag.VISITOR_DROP;
            case ATTACK_PLAYER -> IslandFlag.VISITOR_PVP;
            default -> null;
        };
    }

    private IslandRole legacyRole(String roleKey) {
        try {
            return IslandRole.valueOf(roleKey);
        } catch (IllegalArgumentException exception) {
            return IslandRole.VISITOR;
        }
    }
}
