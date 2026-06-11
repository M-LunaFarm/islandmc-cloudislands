package kr.lunaf.cloudislands.common.event;

import java.util.EnumSet;
import java.util.Set;

public final class CacheInvalidationPlan {
    private CacheInvalidationPlan() {}

    public static Set<CacheTarget> targetsFor(CloudIslandEventType eventType) {
        return switch (eventType) {
            case ISLAND_MEMBER_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_INVITE_CHANGED -> EnumSet.of(CacheTarget.INVITES, CacheTarget.SUMMARY);
            case ISLAND_ACCESS_CHANGED -> EnumSet.of(CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_VISITOR_BAN_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_FLAG_CHANGED -> EnumSet.of(CacheTarget.FLAGS, CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_PERMISSION_CHANGED -> EnumSet.of(CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_BIOME_CHANGED -> EnumSet.of(CacheTarget.BIOME, CacheTarget.SUMMARY);
            case ISLAND_HOME_CHANGED -> EnumSet.of(CacheTarget.HOMES, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_WARP_CHANGED -> EnumSet.of(CacheTarget.WARPS, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_BANK_CHANGED -> EnumSet.of(CacheTarget.BANK, CacheTarget.SUMMARY);
            case ISLAND_RUNTIME_CHANGED, ISLAND_ACTIVATE_REQUESTED, ISLAND_ACTIVATED, ISLAND_DEACTIVATE_REQUESTED, ISLAND_DEACTIVATED, ISLAND_MIGRATE_REQUESTED, ISLAND_MIGRATED, ISLAND_RESTORE_REQUESTED, ISLAND_RESTORED, ISLAND_RESET_REQUESTED, ISLAND_RESET, ISLAND_DELETE_REQUESTED -> EnumSet.of(CacheTarget.RUNTIME, CacheTarget.ROUTE, CacheTarget.SUMMARY);
            case ISLAND_LEVEL_UPDATED -> EnumSet.of(CacheTarget.LEVEL, CacheTarget.SUMMARY);
            case ISLAND_UPGRADE -> EnumSet.of(CacheTarget.SUMMARY, CacheTarget.LEVEL, CacheTarget.GENERATOR);
            case ISLAND_SNAPSHOT_REQUESTED, ISLAND_SNAPSHOT_CREATED -> EnumSet.of(CacheTarget.SUMMARY, CacheTarget.SNAPSHOTS);
            case ISLAND_DELETED -> EnumSet.allOf(CacheTarget.class);
            default -> EnumSet.of(CacheTarget.SUMMARY);
        };
    }

    public enum CacheTarget {
        SUMMARY,
        RUNTIME,
        MEMBERS,
        INVITES,
        PERMISSIONS,
        FLAGS,
        BIOME,
        HOMES,
        WARPS,
        BANK,
        SNAPSHOTS,
        LEVEL,
        GENERATOR,
        ROUTE
    }
}
