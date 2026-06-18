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

    private static String normalize(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT).replace('_', '-').trim();
    }
}
