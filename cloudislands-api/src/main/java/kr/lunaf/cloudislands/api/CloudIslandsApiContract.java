package kr.lunaf.cloudislands.api;

import java.util.List;
import java.util.Map;

public final class CloudIslandsApiContract {
    public static final String CONTRACT_VERSION = "1";
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
    public static final String TOPOLOGY_PRIVACY_POLICY = "player-facing-messages-hide-physical-island-node-and-server-names";
    public static final String CONSISTENCY_AUTHORITY_POLICY = "database-transactions-and-fencing-tokens-are-authoritative-redis-locks-are-advisory";

    private CloudIslandsApiContract() {
    }

    public static List<String> requiredMetadataKeys() {
        return List.of(
            "contract-version",
            "required-metadata-keys",
            "read-policy",
            "write-authority",
            "sync-event-policy",
            "addon-storage-policy",
            "java-plugin-api-policy",
            "internal-api-policy",
            "event-api-policy",
            "core-auth-policy",
            "admin-endpoint-policy",
            "network-exposure-policy",
            "security-posture-summary",
            "topology-privacy-policy",
            "consistency-authority-policy"
        );
    }

    public static String requiredMetadataKeysCsv() {
        return String.join(",", requiredMetadataKeys());
    }

    public static boolean compatibleMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return false;
        }
        String version = metadata.get("contract-version");
        if (!CONTRACT_VERSION.equals(version)) {
            return false;
        }
        return requiredMetadataKeys().stream().allMatch(metadata::containsKey);
    }

    public static Map<String, String> metadata() {
        return Map.ofEntries(
            Map.entry("contract-version", CONTRACT_VERSION),
            Map.entry("required-metadata-keys", requiredMetadataKeysCsv()),
            Map.entry("read-policy", READ_POLICY),
            Map.entry("write-authority", WRITE_AUTHORITY),
            Map.entry("sync-event-policy", SYNC_EVENT_POLICY),
            Map.entry("addon-storage-policy", ADDON_STORAGE_POLICY),
            Map.entry("java-plugin-api-policy", JAVA_PLUGIN_API_POLICY),
            Map.entry("internal-api-policy", INTERNAL_API_POLICY),
            Map.entry("event-api-policy", EVENT_API_POLICY),
            Map.entry("core-auth-policy", CORE_AUTH_POLICY),
            Map.entry("admin-endpoint-policy", ADMIN_ENDPOINT_POLICY),
            Map.entry("network-exposure-policy", NETWORK_EXPOSURE_POLICY),
            Map.entry("security-posture-summary", SECURITY_POSTURE_SUMMARY),
            Map.entry("topology-privacy-policy", TOPOLOGY_PRIVACY_POLICY),
            Map.entry("consistency-authority-policy", CONSISTENCY_AUTHORITY_POLICY)
        );
    }
}
