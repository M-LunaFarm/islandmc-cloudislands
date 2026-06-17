package kr.lunaf.cloudislands.api;

import java.util.Map;

public final class CloudIslandsApiContract {
    public static final String READ_POLICY = "query-services-use-core-api-or-local-cache-snapshots-no-direct-storage-access";
    public static final String WRITE_AUTHORITY = "all-island-writes-go-through-core-api-transaction-endpoints";
    public static final String SYNC_EVENT_POLICY = "synchronous-paper-events-must-use-local-protection-permission-caches";
    public static final String ADDON_STORAGE_POLICY = "addons-use-addon-state-api-or-their-own-shared-database-never-cloudislands-internals";

    private CloudIslandsApiContract() {
    }

    public static Map<String, String> metadata() {
        return Map.of(
            "read-policy", READ_POLICY,
            "write-authority", WRITE_AUTHORITY,
            "sync-event-policy", SYNC_EVENT_POLICY,
            "addon-storage-policy", ADDON_STORAGE_POLICY
        );
    }
}
