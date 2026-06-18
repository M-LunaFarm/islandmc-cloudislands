package kr.lunaf.cloudislands.common.event;

import java.util.List;
import java.util.Map;

public final class EventApiSurfacePolicy {
    public static final String LOCAL_EVENT_POLICY = "paper-local-events-are-plugin-api-surface-not-cross-node-transport";
    public static final String GLOBAL_EVENT_POLICY = "global-events-are-redis-stream-append-only-ordered-cache-invalidation-and-addon-lifecycle-contract";
    public static final String REDIS_STREAM_POLICY = "redis-streams-are-append-only-event-log-for-cross-node-ordering-and-replay";
    public static final String ADDON_DELIVERY_POLICY = "core-global-events-to-paper-poller-to-cloudislands-addon-and-bukkit-events";

    private static final List<String> LOCAL_PAPER_EVENTS = List.of(
        "IslandPreCreateEvent",
        "IslandCreateEvent",
        "IslandDeleteEvent",
        "IslandPreActivateEvent",
        "IslandActivateEvent",
        "IslandDeactivateEvent",
        "IslandPreVisitEvent",
        "IslandVisitEvent",
        "IslandMemberJoinEvent",
        "IslandMemberLeaveEvent",
        "IslandRoleChangeEvent",
        "IslandFlagChangeEvent",
        "IslandPermissionCheckEvent",
        "IslandLevelRecalculateEvent",
        "IslandWorthChangeEvent",
        "IslandWarpCreateEvent",
        "IslandWarpDeleteEvent",
        "IslandBiomeChangeEvent",
        "IslandUpgradeEvent"
    );

    private static final Map<String, CloudIslandEventType> LOCAL_TO_GLOBAL_TYPES = Map.ofEntries(
        Map.entry("IslandPreCreateEvent", CloudIslandEventType.ISLAND_PRE_CREATE),
        Map.entry("IslandCreateEvent", CloudIslandEventType.ISLAND_CREATED),
        Map.entry("IslandDeleteEvent", CloudIslandEventType.ISLAND_DELETED),
        Map.entry("IslandPreActivateEvent", CloudIslandEventType.ISLAND_PRE_ACTIVATE),
        Map.entry("IslandActivateEvent", CloudIslandEventType.ISLAND_ACTIVATED),
        Map.entry("IslandDeactivateEvent", CloudIslandEventType.ISLAND_DEACTIVATED),
        Map.entry("IslandPreVisitEvent", CloudIslandEventType.ISLAND_PRE_VISIT),
        Map.entry("IslandVisitEvent", CloudIslandEventType.ISLAND_VISITED),
        Map.entry("IslandMemberJoinEvent", CloudIslandEventType.ISLAND_MEMBER_JOINED),
        Map.entry("IslandMemberLeaveEvent", CloudIslandEventType.ISLAND_MEMBER_LEFT),
        Map.entry("IslandRoleChangeEvent", CloudIslandEventType.ISLAND_ROLE_CHANGED),
        Map.entry("IslandFlagChangeEvent", CloudIslandEventType.ISLAND_FLAG_CHANGED),
        Map.entry("IslandPermissionCheckEvent", CloudIslandEventType.ISLAND_PERMISSION_CHECKED),
        Map.entry("IslandLevelRecalculateEvent", CloudIslandEventType.ISLAND_LEVEL_UPDATED),
        Map.entry("IslandWorthChangeEvent", CloudIslandEventType.ISLAND_WORTH_CHANGED),
        Map.entry("IslandWarpCreateEvent", CloudIslandEventType.ISLAND_WARP_CREATED),
        Map.entry("IslandWarpDeleteEvent", CloudIslandEventType.ISLAND_WARP_DELETED),
        Map.entry("IslandBiomeChangeEvent", CloudIslandEventType.ISLAND_BIOME_CHANGED),
        Map.entry("IslandUpgradeEvent", CloudIslandEventType.ISLAND_UPGRADE)
    );

    private static final List<CloudIslandEventType> REQUIRED_GLOBAL_EVENTS = List.of(
        CloudIslandEventType.ISLAND_CREATED,
        CloudIslandEventType.ISLAND_DELETED,
        CloudIslandEventType.ISLAND_ACTIVATED,
        CloudIslandEventType.ISLAND_DEACTIVATED,
        CloudIslandEventType.ISLAND_MIGRATED,
        CloudIslandEventType.ISLAND_MEMBER_CHANGED,
        CloudIslandEventType.ISLAND_FLAG_CHANGED,
        CloudIslandEventType.ISLAND_LEVEL_UPDATED,
        CloudIslandEventType.ISLAND_SNAPSHOT_CREATED,
        CloudIslandEventType.NODE_STATE_CHANGED,
        CloudIslandEventType.ROUTE_TICKET_CREATED,
        CloudIslandEventType.ROUTE_TICKET_CONSUMED
    );

    private EventApiSurfacePolicy() {
    }

    public static List<String> localPaperEvents() {
        return LOCAL_PAPER_EVENTS;
    }

    public static boolean localPaperEvent(String eventName) {
        return eventName != null && LOCAL_PAPER_EVENTS.contains(eventName.trim());
    }

    public static Map<String, CloudIslandEventType> localToGlobalTypes() {
        return LOCAL_TO_GLOBAL_TYPES;
    }

    public static CloudIslandEventType globalTypeForLocalEvent(String eventName) {
        return eventName == null ? null : LOCAL_TO_GLOBAL_TYPES.get(eventName.trim());
    }

    public static List<CloudIslandEventType> requiredGlobalEvents() {
        return REQUIRED_GLOBAL_EVENTS;
    }

    public static boolean requiredGlobalEvent(CloudIslandEventType eventType) {
        return eventType != null && REQUIRED_GLOBAL_EVENTS.contains(eventType);
    }

    public static String localEventSummary() {
        return String.join(",", LOCAL_PAPER_EVENTS);
    }

    public static String globalEventSummary() {
        return REQUIRED_GLOBAL_EVENTS.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }
}
