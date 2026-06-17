package kr.lunaf.cloudislands.common.event;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.common.cache.RedisKeys;

public final class CacheInvalidationPlan {
    private CacheInvalidationPlan() {}

    public static Set<CacheTarget> targetsFor(CloudIslandEventType eventType) {
        return switch (eventType) {
            case ISLAND_CREATED -> EnumSet.of(CacheTarget.RUNTIME, CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_MEMBER_JOINED, ISLAND_MEMBER_LEFT, ISLAND_MEMBER_CHANGED, ISLAND_MEMBER_ROLE_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_OWNERSHIP_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_RENAMED -> EnumSet.of(CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_INVITE_CHANGED -> EnumSet.of(CacheTarget.INVITES, CacheTarget.SUMMARY);
            case ISLAND_ACCESS_CHANGED -> EnumSet.of(CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_VISITOR_BAN_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_FLAG_CHANGED -> EnumSet.of(CacheTarget.FLAGS, CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_PERMISSION_CHECKED, ISLAND_PERMISSION_CHANGED -> EnumSet.of(CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_ROLE_CHANGED -> EnumSet.of(CacheTarget.ROLES, CacheTarget.SUMMARY);
            case ISLAND_BIOME_CHANGED -> EnumSet.of(CacheTarget.BIOME, CacheTarget.SUMMARY);
            case ISLAND_HOME_CHANGED -> EnumSet.of(CacheTarget.HOMES, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_WARP_CREATED, ISLAND_WARP_DELETED, ISLAND_WARP_CHANGED -> EnumSet.of(CacheTarget.WARPS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_BANK_CHANGED -> EnumSet.of(CacheTarget.BANK, CacheTarget.SUMMARY);
            case ISLAND_CHAT_SENT -> EnumSet.of(CacheTarget.CHAT);
            case ISLAND_MISSION_PROGRESS, ISLAND_MISSION_COMPLETED -> EnumSet.of(CacheTarget.MISSIONS, CacheTarget.SUMMARY, CacheTarget.LEVEL);
            case ISLAND_LIMIT_CHANGED -> EnumSet.of(CacheTarget.LIMITS, CacheTarget.SUMMARY);
            case ISLAND_BLOCKS_CHANGED -> EnumSet.of(CacheTarget.BLOCKS, CacheTarget.LEVEL, CacheTarget.SUMMARY);
            case ISLAND_BLOCK_VALUE_CHANGED -> EnumSet.of(CacheTarget.BLOCKS, CacheTarget.LEVEL, CacheTarget.SUMMARY);
            case ISLAND_DELETE_BACKUP_FAILED -> EnumSet.of(CacheTarget.RUNTIME, CacheTarget.SNAPSHOTS, CacheTarget.SUMMARY);
            case ISLAND_RUNTIME_CHANGED, ISLAND_RECOVERY_REQUIRED, ISLAND_REPAIRED, ISLAND_PRE_CREATE, ISLAND_PRE_ACTIVATE, ISLAND_ACTIVATE_REQUESTED, ISLAND_ACTIVATED, ISLAND_DEACTIVATE_REQUESTED, ISLAND_DEACTIVATED, ISLAND_MIGRATE_REQUESTED, ISLAND_MIGRATED, ISLAND_RESTORE_REQUESTED, ISLAND_RESTORED, ISLAND_RESET_REQUESTED, ISLAND_RESET, ISLAND_DELETE_REQUESTED, NODE_STATE_CHANGED -> EnumSet.of(CacheTarget.RUNTIME, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_LEVEL_UPDATED, ISLAND_WORTH_CHANGED -> EnumSet.of(CacheTarget.LEVEL, CacheTarget.SUMMARY);
            case ISLAND_UPGRADE -> EnumSet.of(CacheTarget.SUMMARY, CacheTarget.LEVEL, CacheTarget.LIMITS, CacheTarget.BANK, CacheTarget.FLAGS, CacheTarget.GENERATOR, CacheTarget.CROP);
            case ISLAND_SNAPSHOT_REQUESTED, ISLAND_SNAPSHOT_CREATED -> EnumSet.of(CacheTarget.SUMMARY, CacheTarget.SNAPSHOTS);
            case ISLAND_TEMPLATE_CHANGED -> EnumSet.of(CacheTarget.TEMPLATES, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ADDON_STATE_CHANGED -> EnumSet.of(CacheTarget.ADDON_STATE, CacheTarget.SUMMARY);
            case CORE_CACHE_CLEARED, CORE_RELOADED -> EnumSet.allOf(CacheTarget.class);
            case ISLAND_PRE_VISIT, ISLAND_VISITED, ISLAND_VISITOR_KICKED, ROUTE_TICKET_CREATED, ROUTE_SESSION_PUBLISHED, ROUTE_TICKET_CONSUMED, ROUTE_TICKET_FAILED, ROUTE_TICKET_CLEARED -> EnumSet.of(CacheTarget.ROUTE, CacheTarget.ROUTE_TICKETS, CacheTarget.SUMMARY);
            case ISLAND_DELETED -> EnumSet.allOf(CacheTarget.class);
            default -> EnumSet.of(CacheTarget.SUMMARY);
        };
    }

    public static Set<String> redisKeysFor(CloudIslandEventType eventType, UUID islandId) {
        if (islandId == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (CacheTarget target : targetsFor(eventType)) {
            String key = redisKey(target, islandId);
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    public static String redisKey(CacheTarget target, UUID islandId) {
        if (target == null || islandId == null) {
            return "";
        }
        return switch (target) {
            case SUMMARY -> RedisKeys.islandSummary(islandId);
            case RUNTIME -> RedisKeys.islandRuntime(islandId);
            case MEMBERS -> RedisKeys.islandMembers(islandId);
            case PERMISSIONS -> RedisKeys.islandPermissions(islandId);
            case ROLES -> RedisKeys.islandRoles(islandId);
            case FLAGS -> RedisKeys.islandFlags(islandId);
            case BIOME -> RedisKeys.islandSummary(islandId);
            case HOMES -> RedisKeys.islandHomes(islandId);
            case WARPS -> RedisKeys.islandWarps(islandId);
            case BANK -> RedisKeys.islandBank(islandId);
            case MISSIONS -> RedisKeys.islandMissions(islandId, "ALL");
            case LIMITS -> RedisKeys.islandLimits(islandId);
            case BLOCKS -> RedisKeys.islandBlockCounts(islandId);
            case SNAPSHOTS -> RedisKeys.islandSnapshots(islandId);
            case ROUTE, ROUTE_TICKETS -> RedisKeys.islandRouteTickets(islandId);
            case TEMPLATES -> RedisKeys.templates();
            case LEVEL -> RedisKeys.rankingVersion();
            case ADDON_STATE -> RedisKeys.islandAddonState(islandId);
            default -> "";
        };
    }

    public enum CacheTarget {
        SUMMARY,
        RUNTIME,
        MEMBERS,
        INVITES,
        PERMISSIONS,
        ROLES,
        FLAGS,
        BIOME,
        HOMES,
        WARPS,
        BANK,
        CHAT,
        MISSIONS,
        LIMITS,
        BLOCKS,
        SNAPSHOTS,
        LEVEL,
        GENERATOR,
        CROP,
        TEMPLATES,
        ADDON_STATE,
        ROUTE,
        ROUTE_TICKETS
    }
}
