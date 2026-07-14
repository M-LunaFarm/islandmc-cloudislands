package kr.lunaf.cloudislands.paper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.api.model.RoleId;
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
        regionIndex.replaceIsland(new IslandRegion(islandId, worldName, originX - half, originX + half, originZ - half, originZ + half, cellX, cellZ));
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

    public boolean isMigrating(UUID islandId) {
        return islandId != null && migratingIslands.contains(islandId);
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

    public java.util.Optional<IslandRegion> regionAt(String worldName, int blockX, int blockZ) {
        return regionIndex.find(worldName, blockX, blockZ);
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

    public RoleId roleId(UUID islandId, UUID playerUuid) {
        return permissionCache.roleId(islandId, playerUuid);
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
            .map(region -> checkIsland(playerUuid, region.islandId(), permission, adminBypass))
            .orElseGet(() -> PermissionResult.deny("OUTSIDE_ISLAND", RoleId.of(VISITOR_ROLE_KEY)));
    }

    public PermissionResult checkIsland(UUID playerUuid, UUID islandId, IslandPermission permission, boolean adminBypass) {
        if (migratingIslands.contains(islandId)) {
            return PermissionResult.deny("ISLAND_MIGRATING", RoleId.of(VISITOR_ROLE_KEY));
        }
        String roleKey = adminBypass ? OWNER_ROLE_KEY : permissionCache.roleKey(islandId, playerUuid);
        if (permissionCache.allowed(islandId, playerUuid, permission, adminBypass)) {
            return PermissionResult.allow(RoleId.of(roleKey));
        }
        IslandFlag visitorFlag = visitorFlag(permission);
        if (roleKey.equals(VISITOR_ROLE_KEY) && visitorFlag != null && permissionCache.flagAllowed(islandId, visitorFlag)) {
            return PermissionResult.allow(RoleId.of(VISITOR_ROLE_KEY));
        }
        return PermissionResult.deny("DEFAULT_DENY", RoleId.of(roleKey));
    }

    public PermissionResult checkSystem(Block block, IslandPermission permission) {
        return regionIndex.find(block.getWorld().getName(), block.getX(), block.getZ())
            .map(region -> migratingIslands.contains(region.islandId())
                ? PermissionResult.deny("ISLAND_MIGRATING", RoleId.of(VISITOR_ROLE_KEY))
                : PermissionResult.deny("SYSTEM_PROTECTED", RoleId.of(VISITOR_ROLE_KEY)))
            .orElseGet(() -> PermissionResult.allow(RoleId.of(OWNER_ROLE_KEY)));
    }

    public PermissionResult checkSystemFlag(Block block, IslandFlag flag) {
        return checkSystemFlag(block, flag, false);
    }

    public PermissionResult checkSystemFlag(Block block, IslandFlag flag, boolean defaultAllowed) {
        return checkSystemFlag(block.getWorld().getName(), block.getX(), block.getZ(), flag, defaultAllowed);
    }

    public PermissionResult checkSystemFlag(String worldName, int blockX, int blockZ, IslandFlag flag) {
        return checkSystemFlag(worldName, blockX, blockZ, flag, false);
    }

    public PermissionResult checkSystemFlag(String worldName, int blockX, int blockZ, IslandFlag flag, boolean defaultAllowed) {
        return regionIndex.find(worldName, blockX, blockZ)
            .map(region -> {
                if (migratingIslands.contains(region.islandId())) {
                    return PermissionResult.deny("ISLAND_MIGRATING", RoleId.of(VISITOR_ROLE_KEY));
                }
                return permissionCache.flagAllowedOrDefault(region.islandId(), flag, defaultAllowed)
                    ? PermissionResult.allow(RoleId.of(OWNER_ROLE_KEY))
                    : PermissionResult.deny(flag.name() + "_DISABLED", RoleId.of(VISITOR_ROLE_KEY));
            })
            .orElseGet(() -> PermissionResult.allow(RoleId.of(OWNER_ROLE_KEY)));
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
                USE_BREWING_STAND,
                ANIMAL_BREED,
                ANIMAL_SHEAR,
                FISH,
                ENTITY_RIDE,
                VILLAGER_TRADE,
                PICKUP_ENTITY_BUCKET,
                TAKE_LECTERN_BOOK,
                DYE_SHEEP,
                SADDLE_ENTITY,
                BRUSH,
                IGNITE_CREEPER,
                NAME_ENTITY,
                SCULK_SENSOR,
                ITEM_FRAME,
                LEASH,
                TURTLE_EGG_TRAMPLE -> IslandFlag.VISITOR_INTERACT;
            case OPEN_CONTAINER -> IslandFlag.VISITOR_CONTAINER;
            case PICKUP_ITEM -> IslandFlag.VISITOR_PICKUP;
            case DROP_ITEM -> IslandFlag.VISITOR_DROP;
            case ATTACK_PLAYER -> IslandFlag.VISITOR_PVP;
            default -> null;
        };
    }
}
