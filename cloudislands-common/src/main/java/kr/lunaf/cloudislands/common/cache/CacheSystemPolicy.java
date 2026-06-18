package kr.lunaf.cloudislands.common.cache;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;

public final class CacheSystemPolicy {
    public static final String THREE_LEVEL_CACHE_POLICY =
            "L1-paper-velocity-local-memory>L2-redis>L3-postgresql";
    public static final String WRITE_EVENT_POLICY =
            "core-api-publishes-global-event-after-successful-write";
    public static final String LOCAL_FANOUT_POLICY =
            "global-event-invalidates-island-node-lobby-and-velocity-route-local-caches";

    private static final List<String> CACHE_TARGETS = List.of(
            "player_uuid->island_id",
            "island_id->summary",
            "island_id->runtime",
            "island_id->members",
            "island_id->permissions",
            "island_id->flags",
            "island_id->warps",
            "node_id->heartbeat"
    );

    private static final List<CloudIslandEventType> GOAL_INVALIDATION_EVENTS = List.of(
            CloudIslandEventType.ISLAND_MEMBER_CHANGED,
            CloudIslandEventType.ISLAND_FLAG_CHANGED,
            CloudIslandEventType.ISLAND_PERMISSION_CHANGED,
            CloudIslandEventType.ISLAND_WARP_CHANGED,
            CloudIslandEventType.ISLAND_RUNTIME_CHANGED,
            CloudIslandEventType.ISLAND_DELETED
    );

    private static final Map<String, List<String>> FLAG_CHANGE_FANOUT_EXAMPLE = Map.of(
            "Island-1", List.of("local-cache-delete:flags:A"),
            "Island-2", List.of("local-cache-delete:flags:A"),
            "Lobby", List.of("local-cache-delete:flags:A"),
            "Velocity", List.of("route-cache-delete-if-affected")
    );

    private CacheSystemPolicy() {
    }

    public static List<String> cacheTargets() {
        return CACHE_TARGETS;
    }

    public static List<CloudIslandEventType> goalInvalidationEvents() {
        return GOAL_INVALIDATION_EVENTS;
    }

    public static Map<String, List<String>> flagChangeFanoutExample() {
        return FLAG_CHANGE_FANOUT_EXAMPLE;
    }

    public static boolean goalInvalidationEvent(CloudIslandEventType eventType) {
        return GOAL_INVALIDATION_EVENTS.contains(eventType);
    }
}
