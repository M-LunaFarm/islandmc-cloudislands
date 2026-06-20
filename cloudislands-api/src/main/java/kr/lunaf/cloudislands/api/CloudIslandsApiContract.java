package kr.lunaf.cloudislands.api;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class CloudIslandsApiContract {
    public static final String CONTRACT_VERSION = "1";
    public static final String RUNTIME_API_VERSION = "1.1.0";
    public static final String READ_POLICY = "query-services-use-core-api-or-local-cache-snapshots-no-direct-storage-access";
    public static final String WRITE_AUTHORITY = "all-island-writes-go-through-core-api-transaction-endpoints";
    public static final String SYNC_EVENT_POLICY = "synchronous-paper-events-must-use-local-protection-permission-caches";
    public static final String ADDON_STORAGE_POLICY = "addons-use-addon-state-api-or-their-own-shared-database-never-cloudislands-internals";
    public static final String ADDON_PACKAGING_POLICY = "addons-may-run-as-external-plugins-or-built-in-feature-packs-through-the-same-spi";
    public static final String ADDON_SUPPORTED_PACKAGING = "external-plugin,built-in-feature-pack,built-in-compatible";
    public static final String ADDON_DESCRIPTOR_POLICY = "addon-descriptor-may-be-embedded-in-jar-or-distributed-as-sidecar-cloudislands-addon-yml";
    public static final String ADDON_DISTRIBUTION_POLICY = "distAddons-and-distAddonBundle-package-addon-jars-and-descriptor-sidecars-separately-from-required-core";
    public static final String ADDON_REMOVAL_POLICY = "missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore";
    public static final String ADDON_RECONNECT_POLICY = "reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid";
    public static final String JAVA_PLUGIN_API_POLICY = "paper-plugins-use-cloudislands-api-services-and-never-core-internals";
    public static final String INTERNAL_API_POLICY = "http-admin-and-runtime-endpoints-are-token-or-mtls-protected-core-boundaries";
    public static final String EVENT_API_POLICY = "global-events-are-append-only-cache-invalidation-and-addon-lifecycle-contract";
    public static final String SEMANTIC_VERSION_POLICY = "major-version-breaks-binary-api-minor-adds-compatible-api-patch-fixes-only";
    public static final String DEPRECATION_POLICY = "deprecated-api-remains-for-at-least-one-minor-release-before-removal";
    public static final String COMPATIBILITY_LEVELS = "compatible,runtime-too-old,major-version-mismatch,invalid-version";
    public static final String DEPRECATION_REMOVAL_POLICY = "deprecated-api-removal-requires-major-bump-or-one-full-minor-window";
    public static final String EVENT_DELIVERY_POLICY = "global-events-are-at-least-once-delivered-and-consumers-must-deduplicate-by-event-id";
    public static final String THREADING_POLICY = "api-futures-complete-off-main-thread-paper-callers-must-schedule-bukkit-world-and-player-access";
    public static final String CORE_FAILURE_POLICY = "core-unavailable-fails-closed-for-writes-and-may-return-marked-stale-snapshots-for-reads";
    public static final String TIMEOUT_RETRY_POLICY = "typed-core-client-uses-bounded-timeouts-and-retries-read-requests-only-unless-idempotency-key-is-present";
    public static final String COMPATIBILITY_TESTKIT_POLICY = "addons-validate-against-cloudislands-testkit-contract-fixtures-before-certification";
    public static final String INTEGRATION_PORT_POLICY = "external-hooks-use-applyValidated-so-core-state-mutations-require-island-uuid-node-id-runtime-fencing-token-node-ownership-and-idempotency-key";
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
            "compatibility-status",
            "required-metadata-keys",
            "runtime-api-version",
            "read-policy",
            "write-authority",
            "sync-event-policy",
            "addon-storage-policy",
            "addon-packaging-policy",
            "addon-supported-packaging",
            "addon-descriptor-policy",
            "addon-distribution-policy",
            "addon-removal-policy",
            "addon-reconnect-policy",
            "java-plugin-api-policy",
            "internal-api-policy",
            "event-api-policy",
            "semantic-version-policy",
            "deprecation-policy",
            "compatibility-levels",
            "deprecation-removal-policy",
            "event-delivery-policy",
            "threading-policy",
            "core-failure-policy",
            "timeout-retry-policy",
            "compatibility-testkit-policy",
            "integration-port-policy",
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
        return "compatible".equals(metadataCompatibilityStatus(metadata));
    }

    public static List<String> missingMetadataKeys(Map<String, String> metadata) {
        List<String> missing = new ArrayList<>();
        if (metadata == null) {
            missing.addAll(requiredMetadataKeys());
            return List.copyOf(missing);
        }
        for (String key : requiredMetadataKeys()) {
            if (!metadata.containsKey(key)) {
                missing.add(key);
            }
        }
        return List.copyOf(missing);
    }

    public static String metadataCompatibilityStatus(Map<String, String> metadata) {
        if (metadata == null) {
            return "missing-metadata";
        }
        String version = metadata.get("contract-version");
        if (!CONTRACT_VERSION.equals(version)) {
            return "version-mismatch";
        }
        List<String> missing = missingMetadataKeys(metadata);
        if (!missing.isEmpty()) {
            return "missing-required-keys:" + String.join(",", missing);
        }
        String requiredKeys = metadata.get("required-metadata-keys");
        if (!requiredMetadataKeysCsv().equals(requiredKeys)) {
            return "required-keys-mismatch";
        }
        return "compatible";
    }

    public static Map<String, String> metadata() {
        return Map.ofEntries(
            Map.entry("contract-version", CONTRACT_VERSION),
            Map.entry("compatibility-status", "compatible"),
            Map.entry("required-metadata-keys", requiredMetadataKeysCsv()),
            Map.entry("runtime-api-version", RUNTIME_API_VERSION),
            Map.entry("read-policy", READ_POLICY),
            Map.entry("write-authority", WRITE_AUTHORITY),
            Map.entry("sync-event-policy", SYNC_EVENT_POLICY),
            Map.entry("addon-storage-policy", ADDON_STORAGE_POLICY),
            Map.entry("addon-packaging-policy", ADDON_PACKAGING_POLICY),
            Map.entry("addon-supported-packaging", ADDON_SUPPORTED_PACKAGING),
            Map.entry("addon-descriptor-policy", ADDON_DESCRIPTOR_POLICY),
            Map.entry("addon-distribution-policy", ADDON_DISTRIBUTION_POLICY),
            Map.entry("addon-removal-policy", ADDON_REMOVAL_POLICY),
            Map.entry("addon-reconnect-policy", ADDON_RECONNECT_POLICY),
            Map.entry("java-plugin-api-policy", JAVA_PLUGIN_API_POLICY),
            Map.entry("internal-api-policy", INTERNAL_API_POLICY),
            Map.entry("event-api-policy", EVENT_API_POLICY),
            Map.entry("semantic-version-policy", SEMANTIC_VERSION_POLICY),
            Map.entry("deprecation-policy", DEPRECATION_POLICY),
            Map.entry("compatibility-levels", COMPATIBILITY_LEVELS),
            Map.entry("deprecation-removal-policy", DEPRECATION_REMOVAL_POLICY),
            Map.entry("event-delivery-policy", EVENT_DELIVERY_POLICY),
            Map.entry("threading-policy", THREADING_POLICY),
            Map.entry("core-failure-policy", CORE_FAILURE_POLICY),
            Map.entry("timeout-retry-policy", TIMEOUT_RETRY_POLICY),
            Map.entry("compatibility-testkit-policy", COMPATIBILITY_TESTKIT_POLICY),
            Map.entry("integration-port-policy", INTEGRATION_PORT_POLICY),
            Map.entry("core-auth-policy", CORE_AUTH_POLICY),
            Map.entry("admin-endpoint-policy", ADMIN_ENDPOINT_POLICY),
            Map.entry("network-exposure-policy", NETWORK_EXPOSURE_POLICY),
            Map.entry("security-posture-summary", SECURITY_POSTURE_SUMMARY),
            Map.entry("topology-privacy-policy", TOPOLOGY_PRIVACY_POLICY),
            Map.entry("consistency-authority-policy", CONSISTENCY_AUTHORITY_POLICY)
        );
    }
}
