package kr.lunaf.cloudislands.api;

import java.util.Map;

public final class CloudIslandsApiContract {
    public static final String READ_POLICY = "query-services-use-core-api-or-local-cache-snapshots-no-direct-storage-access";
    public static final String WRITE_AUTHORITY = "all-island-writes-go-through-core-api-transaction-endpoints";
    public static final String SYNC_EVENT_POLICY = "synchronous-paper-events-must-use-local-protection-permission-caches";
    public static final String ADDON_STORAGE_POLICY = "addons-use-addon-state-api-or-their-own-shared-database-never-cloudislands-internals";
    public static final String JAVA_PLUGIN_API_POLICY = "paper-plugins-use-cloudislands-api-services-and-never-core-internals";
    public static final String INTERNAL_API_POLICY = "http-admin-and-runtime-endpoints-are-token-or-mtls-protected-core-boundaries";
    public static final String EVENT_API_POLICY = "global-events-are-append-only-cache-invalidation-and-addon-lifecycle-contract";
    public static final String CORE_AUTH_POLICY = "core-api-requires-api-token-or-mtls-for-non-health-requests";
    public static final String ADMIN_ENDPOINT_POLICY = "admin-endpoints-use-separate-admin-token-and-per-command-permissions";
    public static final String NETWORK_EXPOSURE_POLICY = "bind-public-only-with-ip-allowlist-mtls-and-token-controls";
    public static final String SECURITY_POSTURE_SUMMARY = "token-or-mtls-core-api,separate-admin-token,per-command-permission,ip-allowlist-for-public-bind";

    private CloudIslandsApiContract() {
    }

    public static Map<String, String> metadata() {
        return Map.of(
            "read-policy", READ_POLICY,
            "write-authority", WRITE_AUTHORITY,
            "sync-event-policy", SYNC_EVENT_POLICY,
            "addon-storage-policy", ADDON_STORAGE_POLICY,
            "java-plugin-api-policy", JAVA_PLUGIN_API_POLICY,
            "internal-api-policy", INTERNAL_API_POLICY,
            "event-api-policy", EVENT_API_POLICY,
            "core-auth-policy", CORE_AUTH_POLICY,
            "admin-endpoint-policy", ADMIN_ENDPOINT_POLICY,
            "network-exposure-policy", NETWORK_EXPOSURE_POLICY,
            "security-posture-summary", SECURITY_POSTURE_SUMMARY
        );
    }
}
