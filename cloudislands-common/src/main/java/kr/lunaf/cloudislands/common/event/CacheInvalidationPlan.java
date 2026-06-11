package kr.lunaf.cloudislands.common.event;

import java.util.EnumSet;
import java.util.Set;

public final class CacheInvalidationPlan {
    private CacheInvalidationPlan() {}

    public static Set<CacheTarget> targetsFor(CloudIslandEventType eventType) {
        return switch (eventType) {
            case ISLAND_MEMBER_CHANGED -> EnumSet.of(CacheTarget.MEMBERS, CacheTarget.PERMISSIONS, CacheTarget.SUMMARY);
            case ISLAND_FLAG_CHANGED -> EnumSet.of(CacheTarget.FLAGS, CacheTarget.PERMISSIONS);
            case ISLAND_PERMISSION_CHANGED -> EnumSet.of(CacheTarget.PERMISSIONS);
            case ISLAND_WARP_CHANGED -> EnumSet.of(CacheTarget.WARPS);
            case ISLAND_RUNTIME_CHANGED, ISLAND_ACTIVATED, ISLAND_DEACTIVATED, ISLAND_MIGRATED -> EnumSet.of(CacheTarget.RUNTIME, CacheTarget.ROUTE);
            case ISLAND_LEVEL_UPDATED -> EnumSet.of(CacheTarget.LEVEL, CacheTarget.SUMMARY);
            case ISLAND_UPGRADE -> EnumSet.of(CacheTarget.SUMMARY, CacheTarget.LEVEL, CacheTarget.GENERATOR);
            case ISLAND_DELETED -> EnumSet.allOf(CacheTarget.class);
            default -> EnumSet.of(CacheTarget.SUMMARY);
        };
    }

    public enum CacheTarget {
        SUMMARY,
        RUNTIME,
        MEMBERS,
        PERMISSIONS,
        FLAGS,
        WARPS,
        LEVEL,
        GENERATOR,
        ROUTE
    }
}
