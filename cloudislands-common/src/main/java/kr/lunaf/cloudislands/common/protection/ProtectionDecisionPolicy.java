package kr.lunaf.cloudislands.common.protection;

import java.util.List;
import java.util.Locale;

public final class ProtectionDecisionPolicy {
    public static final String HOT_PATH_POLICY = "region-index-and-local-permission-cache-only";
    public static final String NO_SYNC_IO_POLICY = "no-core-api-http-database-or-redis-call-on-bukkit-event-thread";
    public static final String CACHE_REFRESH_POLICY = "async-core-event-poller-refreshes-local-cache-outside-protection-decision";
    public static final String MIGRATION_POLICY = "deny-protected-actions-while-island-region-is-migrating";
    public static final String DECISION_ORDER = "admin-bypass>island-owner>explicit-member-role>trusted-override>visitor-flags>default-deny";
    public static final String REGION_LOOKUP_ORDER = "world-chunk-region-index>bounding-box>island-id>local-permission-cache";
    public static final String PROTECTED_EVENT_SURFACE = "block-place-break-interact-bucket-inventory-combat-explosion-hanging-item-armorstand-entity-vehicle-fire-fluid";
    public static final String SYNC_EVENT_SOURCE_POLICY = "synchronous-paper-events-may-read-region-index-permission-cache-and-runtime-cache-only";
    public static final String ASYNC_REFRESH_SOURCE_POLICY = "core-api-http-database-and-redis-refresh-local-cache-outside-event-thread";
    public static final String BORDER_POLICY = "visitor-returns-to-visitor-spawn-member-returns-to-island-spawn-admin-may-bypass";

    private static final List<String> SYNC_ALLOWED_SOURCES = List.of(
            "region-index",
            "local-permission-cache",
            "local-runtime-cache",
            "local-member-cache",
            "local-flag-cache",
            "local-warp-cache"
    );

    private static final List<String> SYNC_FORBIDDEN_SOURCES = List.of(
            "core-api-http",
            "database",
            "postgresql",
            "mysql",
            "redis",
            "object-storage",
            "web-request",
            "grpc"
    );

    private static final List<String> PROTECTED_EVENTS = List.of(
            "BlockBreakEvent",
            "BlockPlaceEvent",
            "BlockMultiPlaceEvent",
            "PlayerInteractEvent",
            "PlayerBucketEmptyEvent",
            "PlayerBucketFillEvent",
            "InventoryOpenEvent",
            "InventoryClickEvent",
            "EntityDamageByEntityEvent",
            "EntityExplodeEvent",
            "BlockExplodeEvent",
            "HangingBreakByEntityEvent",
            "HangingPlaceEvent",
            "PlayerDropItemEvent",
            "EntityPickupItemEvent",
            "PlayerArmorStandManipulateEvent",
            "PlayerShearEntityEvent",
            "PlayerLeashEntityEvent",
            "PlayerUnleashEntityEvent",
            "VehicleDestroyEvent",
            "BlockIgniteEvent",
            "BlockBurnEvent",
            "BlockSpreadEvent",
            "LeavesDecayEvent",
            "FluidLevelChangeEvent",
            "BlockFromToEvent"
    );

    private ProtectionDecisionPolicy() {
    }

    public static List<String> syncAllowedSources() {
        return SYNC_ALLOWED_SOURCES;
    }

    public static List<String> syncForbiddenSources() {
        return SYNC_FORBIDDEN_SOURCES;
    }

    public static boolean syncSourceAllowed(String source) {
        String normalized = normalize(source);
        return SYNC_ALLOWED_SOURCES.stream().anyMatch(value -> normalize(value).equals(normalized));
    }

    public static boolean syncSourceForbidden(String source) {
        String normalized = normalize(source);
        return SYNC_FORBIDDEN_SOURCES.stream().anyMatch(value -> normalize(value).equals(normalized));
    }

    public static String syncSourceDecision(String source) {
        if (syncSourceAllowed(source)) {
            return "ALLOW_LOCAL_CACHE";
        }
        if (syncSourceForbidden(source)) {
            return "DENY_SYNC_IO";
        }
        return "DENY_UNKNOWN_SOURCE";
    }

    public static List<String> protectedEvents() {
        return PROTECTED_EVENTS;
    }

    public static boolean protectedEvent(String eventName) {
        String normalized = normalize(eventName);
        return PROTECTED_EVENTS.stream().anyMatch(event -> normalize(event).equals(normalized));
    }

    public static String borderAction(String role) {
        String normalized = normalize(role);
        if (normalized.equals("admin") || normalized.equals("operator") || normalized.equals("bypass")) {
            return "ALLOW_BYPASS";
        }
        if (normalized.equals("visitor") || normalized.equals("banned")) {
            return "TELEPORT_VISITOR_SPAWN";
        }
        if (normalized.equals("owner")
                || normalized.equals("co-owner")
                || normalized.equals("moderator")
                || normalized.equals("member")
                || normalized.equals("trusted")) {
            return "TELEPORT_ISLAND_SPAWN";
        }
        return "BLOCK_OR_RETURN_TO_SAFE_SPAWN";
    }

    private static String normalize(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT).replace('_', '-').trim();
    }
}
