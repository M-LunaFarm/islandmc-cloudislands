package kr.lunaf.cloudislands.common.protection;

public final class ProtectionDecisionPolicy {
    public static final String HOT_PATH_POLICY = "region-index-and-local-permission-cache-only";
    public static final String NO_SYNC_IO_POLICY = "no-core-api-http-database-or-redis-call-on-bukkit-event-thread";
    public static final String CACHE_REFRESH_POLICY = "async-core-event-poller-refreshes-local-cache-outside-protection-decision";
    public static final String MIGRATION_POLICY = "deny-protected-actions-while-island-region-is-migrating";

    private ProtectionDecisionPolicy() {
    }
}
