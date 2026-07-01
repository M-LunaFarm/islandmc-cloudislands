package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;

public record CloudIslandsStatusSnapshot(
    String platform,
    String role,
    String nodeId,
    String version,
    boolean providerRegistered,
    boolean coreTokenConfigured,
    boolean adminTokenConfigured,
    boolean forwardingRequired,
    boolean forwardingSecretConfigured,
    boolean routeSessionEnforced,
    int onlinePlayers,
    int activeIslands,
    int activationQueue,
    Instant sampledAt,
    String readPolicy,
    String writeAuthority,
    String syncEventPolicy,
    String addonStoragePolicy,
    String javaPluginApiPolicy,
    String internalApiPolicy,
    String eventApiPolicy,
    String coreAuthPolicy,
    String adminEndpointPolicy,
    String networkExposurePolicy,
    String securityPostureSummary,
    String topologyPrivacyPolicy,
    String consistencyAuthorityPolicy,
    String contractVersion
) {
    public CloudIslandsStatusSnapshot(
        String platform,
        String role,
        String nodeId,
        String version,
        boolean providerRegistered,
        boolean coreTokenConfigured,
        boolean adminTokenConfigured,
        boolean forwardingRequired,
        boolean forwardingSecretConfigured,
        boolean routeSessionEnforced,
        int onlinePlayers,
        int activeIslands,
        int activationQueue,
        Instant sampledAt
    ) {
        this(
            platform,
            role,
            nodeId,
            version,
            providerRegistered,
            coreTokenConfigured,
            adminTokenConfigured,
            forwardingRequired,
            forwardingSecretConfigured,
            routeSessionEnforced,
            onlinePlayers,
            activeIslands,
            activationQueue,
            sampledAt,
            CloudIslandsApiContract.READ_POLICY,
            CloudIslandsApiContract.WRITE_AUTHORITY,
            CloudIslandsApiContract.SYNC_EVENT_POLICY,
            CloudIslandsApiContract.ADDON_STORAGE_POLICY
        );
    }

    public CloudIslandsStatusSnapshot(
        String platform,
        String role,
        String nodeId,
        String version,
        boolean providerRegistered,
        boolean coreTokenConfigured,
        boolean adminTokenConfigured,
        boolean forwardingRequired,
        boolean forwardingSecretConfigured,
        boolean routeSessionEnforced,
        int onlinePlayers,
        int activeIslands,
        int activationQueue,
        Instant sampledAt,
        String readPolicy,
        String writeAuthority,
        String syncEventPolicy,
        String addonStoragePolicy
    ) {
        this(
            platform,
            role,
            nodeId,
            version,
            providerRegistered,
            coreTokenConfigured,
            adminTokenConfigured,
            forwardingRequired,
            forwardingSecretConfigured,
            routeSessionEnforced,
            onlinePlayers,
            activeIslands,
            activationQueue,
            sampledAt,
            readPolicy,
            writeAuthority,
            syncEventPolicy,
            addonStoragePolicy,
            CloudIslandsApiContract.JAVA_PLUGIN_API_POLICY,
            CloudIslandsApiContract.INTERNAL_API_POLICY,
            CloudIslandsApiContract.EVENT_API_POLICY
        );
    }

    public CloudIslandsStatusSnapshot(
        String platform,
        String role,
        String nodeId,
        String version,
        boolean providerRegistered,
        boolean coreTokenConfigured,
        boolean adminTokenConfigured,
        boolean forwardingRequired,
        boolean forwardingSecretConfigured,
        boolean routeSessionEnforced,
        int onlinePlayers,
        int activeIslands,
        int activationQueue,
        Instant sampledAt,
        String readPolicy,
        String writeAuthority,
        String syncEventPolicy,
        String addonStoragePolicy,
        String javaPluginApiPolicy,
        String internalApiPolicy,
        String eventApiPolicy
    ) {
        this(
            platform,
            role,
            nodeId,
            version,
            providerRegistered,
            coreTokenConfigured,
            adminTokenConfigured,
            forwardingRequired,
            forwardingSecretConfigured,
            routeSessionEnforced,
            onlinePlayers,
            activeIslands,
            activationQueue,
            sampledAt,
            readPolicy,
            writeAuthority,
            syncEventPolicy,
            addonStoragePolicy,
            javaPluginApiPolicy,
            internalApiPolicy,
            eventApiPolicy,
            CloudIslandsApiContract.CORE_AUTH_POLICY,
            CloudIslandsApiContract.ADMIN_ENDPOINT_POLICY,
            CloudIslandsApiContract.NETWORK_EXPOSURE_POLICY,
            CloudIslandsApiContract.SECURITY_POSTURE_SUMMARY,
            CloudIslandsApiContract.TOPOLOGY_PRIVACY_POLICY,
            CloudIslandsApiContract.CONSISTENCY_AUTHORITY_POLICY,
            CloudIslandsApiContract.CONTRACT_VERSION
        );
    }

    public Map<String, String> contractMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("contract-version", safe(contractVersion));
        metadata.put("compatibility-status", "compatible");
        metadata.put("required-metadata-keys", CloudIslandsApiContract.requiredMetadataKeysCsv());
        metadata.put("runtime-api-version", CloudIslandsApiContract.RUNTIME_API_VERSION);
        metadata.put("capabilities", CloudIslandsApiContract.capabilitiesCsv());
        metadata.put("read-policy", safe(readPolicy));
        metadata.put("write-authority", safe(writeAuthority));
        metadata.put("sync-event-policy", safe(syncEventPolicy));
        metadata.put("addon-storage-policy", safe(addonStoragePolicy));
        metadata.put("addon-packaging-policy", CloudIslandsApiContract.ADDON_PACKAGING_POLICY);
        metadata.put("addon-supported-packaging", CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING);
        metadata.put("addon-descriptor-policy", CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY);
        metadata.put("addon-distribution-policy", CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY);
        metadata.put("addon-removal-policy", CloudIslandsApiContract.ADDON_REMOVAL_POLICY);
        metadata.put("addon-reconnect-policy", CloudIslandsApiContract.ADDON_RECONNECT_POLICY);
        metadata.put("semantic-version-policy", CloudIslandsApiContract.SEMANTIC_VERSION_POLICY);
        metadata.put("deprecation-policy", CloudIslandsApiContract.DEPRECATION_POLICY);
        metadata.put("compatibility-levels", CloudIslandsApiContract.COMPATIBILITY_LEVELS);
        metadata.put("deprecation-removal-policy", CloudIslandsApiContract.DEPRECATION_REMOVAL_POLICY);
        metadata.put("event-delivery-policy", CloudIslandsApiContract.EVENT_DELIVERY_POLICY);
        metadata.put("threading-policy", CloudIslandsApiContract.THREADING_POLICY);
        metadata.put("core-failure-policy", CloudIslandsApiContract.CORE_FAILURE_POLICY);
        metadata.put("timeout-retry-policy", CloudIslandsApiContract.TIMEOUT_RETRY_POLICY);
        metadata.put("compatibility-testkit-policy", CloudIslandsApiContract.COMPATIBILITY_TESTKIT_POLICY);
        metadata.put("integration-port-policy", CloudIslandsApiContract.INTEGRATION_PORT_POLICY);
        metadata.put("java-plugin-api-policy", safe(javaPluginApiPolicy));
        metadata.put("internal-api-policy", safe(internalApiPolicy));
        metadata.put("event-api-policy", safe(eventApiPolicy));
        metadata.put("core-auth-policy", safe(coreAuthPolicy));
        metadata.put("admin-endpoint-policy", safe(adminEndpointPolicy));
        metadata.put("network-exposure-policy", safe(networkExposurePolicy));
        metadata.put("security-posture-summary", safe(securityPostureSummary));
        metadata.put("topology-privacy-policy", safe(topologyPrivacyPolicy));
        metadata.put("consistency-authority-policy", safe(consistencyAuthorityPolicy));
        return Map.copyOf(metadata);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public String contractCompatibilityStatus() {
        return CloudIslandsApiContract.metadataCompatibilityStatus(contractMetadata());
    }

    public boolean contractCompatible() {
        return CloudIslandsApiContract.compatibleMetadata(contractMetadata());
    }

    public List<String> missingContractMetadataKeys() {
        return CloudIslandsApiContract.missingMetadataKeys(contractMetadata());
    }
}
