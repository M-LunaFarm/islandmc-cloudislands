package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.compat.ApiCompatibilityDecision;
import kr.lunaf.cloudislands.api.compat.ApiCompatibilityPolicy;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public final class ApiContractVerifier {
    public static final String ADDON_CERTIFICATION_POLICY = "addons-pass-api-version-contract-metadata-event-threading-timeout-and-core-failure-policy-checks";
    public static final String CLOUDISLANDS_API_METADATA_PREFIX = "cloudislands-api-";
    public static final String REQUESTED_API_VERSION_KEY = "cloudislands-api-requested-version";
    public static final List<String> ADDON_CERTIFICATION_METADATA_KEYS = List.of(
        "addon-standard-metadata-version",
        "addon-packaging",
        "addon-supported-packaging",
        "addon-removal-safe",
        "addon-removal-policy",
        "addon-reconnect-policy",
        "addon-config-gate-policy",
        "addon-event-delivery",
        "addon-event-failure-policy"
    );

    private ApiContractVerifier() {
    }

    public static ApiContractVerification verifyRuntimeMetadata(Map<String, String> contractMetadata) {
        String runtimeVersion = valueOrDefault(contractMetadata, "runtime-api-version", CloudIslandsApiContract.RUNTIME_API_VERSION);
        return verifyRuntimeMetadata("runtime", runtimeVersion, contractMetadata);
    }

    public static ApiContractVerification verifyRuntimeMetadata(String subject, String requestedApiVersion, Map<String, String> contractMetadata) {
        String runtimeVersion = valueOrDefault(contractMetadata, "runtime-api-version", CloudIslandsApiContract.RUNTIME_API_VERSION);
        ApiCompatibilityDecision compatibility = ApiCompatibilityPolicy.evaluate(requestedApiVersion, runtimeVersion);
        String metadataStatus = CloudIslandsApiContract.metadataCompatibilityStatus(contractMetadata);
        List<String> missingContractKeys = CloudIslandsApiContract.missingMetadataKeys(contractMetadata);
        return new ApiContractVerification(subject, requestedApiVersion, runtimeVersion, compatibility, metadataStatus, missingContractKeys, List.of());
    }

    public static ApiContractVerification verifyAddon(CloudIslandsAddonSnapshot addon) {
        if (addon == null) {
            return verifyAddon("null-addon", CloudIslandsApiContract.RUNTIME_API_VERSION, Map.of());
        }
        return verifyAddon(addon.id(), requestedApiVersion(addon.metadata()), addon.metadata());
    }

    public static ApiContractVerification verifyAddon(String addonId, String requestedApiVersion, Map<String, String> addonMetadata) {
        Map<String, String> contractMetadata = prefixedContractMetadata(addonMetadata);
        String runtimeVersion = valueOrDefault(contractMetadata, "runtime-api-version", CloudIslandsApiContract.RUNTIME_API_VERSION);
        ApiCompatibilityDecision compatibility = ApiCompatibilityPolicy.evaluate(requestedApiVersion, runtimeVersion);
        String metadataStatus = CloudIslandsApiContract.metadataCompatibilityStatus(contractMetadata);
        List<String> missingContractKeys = CloudIslandsApiContract.missingMetadataKeys(contractMetadata);
        List<String> missingAddonKeys = missingAddonMetadataKeys(addonMetadata);
        return new ApiContractVerification(addonId, requestedApiVersion, runtimeVersion, compatibility, metadataStatus, missingContractKeys, missingAddonKeys);
    }

    public static Map<String, String> addonCertificationMetadata(Map<String, String> addonMetadata, Map<String, String> contractMetadata) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (addonMetadata != null) {
            metadata.putAll(addonMetadata);
        }
        Map<String, String> effectiveContract = contractMetadata == null || contractMetadata.isEmpty()
            ? CloudIslandsApiContract.metadata()
            : contractMetadata;
        effectiveContract.forEach((key, value) -> metadata.put(CLOUDISLANDS_API_METADATA_PREFIX + key, value));
        metadata.putIfAbsent("cloudislands-api-contract-compatibility", CloudIslandsApiContract.metadataCompatibilityStatus(effectiveContract));
        metadata.putIfAbsent("cloudislands-api-contract-compatible", Boolean.toString(CloudIslandsApiContract.compatibleMetadata(effectiveContract)));
        metadata.putIfAbsent(REQUESTED_API_VERSION_KEY, effectiveContract.getOrDefault("runtime-api-version", CloudIslandsApiContract.RUNTIME_API_VERSION));
        metadata.putIfAbsent("cloudislands-addon-certification-policy", ADDON_CERTIFICATION_POLICY);
        return Map.copyOf(metadata);
    }

    public static List<String> missingAddonMetadataKeys(Map<String, String> addonMetadata) {
        List<String> missing = new ArrayList<>();
        if (addonMetadata == null) {
            missing.addAll(ADDON_CERTIFICATION_METADATA_KEYS);
            return List.copyOf(missing);
        }
        for (String key : ADDON_CERTIFICATION_METADATA_KEYS) {
            String value = addonMetadata.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        return List.copyOf(missing);
    }

    public static Map<String, String> prefixedContractMetadata(Map<String, String> addonMetadata) {
        if (addonMetadata == null || addonMetadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> contractMetadata = new LinkedHashMap<>();
        addonMetadata.forEach((key, value) -> {
            if (key != null && key.startsWith(CLOUDISLANDS_API_METADATA_PREFIX) && value != null) {
                String contractKey = key.substring(CLOUDISLANDS_API_METADATA_PREFIX.length());
                if (!contractKey.isBlank()) {
                    contractMetadata.put(contractKey, value);
                }
            }
        });
        return Map.copyOf(contractMetadata);
    }

    public static String requestedApiVersion(Map<String, String> addonMetadata) {
        if (addonMetadata == null || addonMetadata.isEmpty()) {
            return CloudIslandsApiContract.RUNTIME_API_VERSION;
        }
        for (String key : List.of(REQUESTED_API_VERSION_KEY, "cloudislands-api-version", "requested-cloudislands-api-version", "required-cloudislands-api-version")) {
            String value = addonMetadata.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String prefixedRuntime = addonMetadata.get(CLOUDISLANDS_API_METADATA_PREFIX + "runtime-api-version");
        return prefixedRuntime == null || prefixedRuntime.isBlank() ? CloudIslandsApiContract.RUNTIME_API_VERSION : prefixedRuntime;
    }

    private static String valueOrDefault(Map<String, String> metadata, String key, String fallback) {
        if (metadata == null || metadata.isEmpty()) {
            return fallback;
        }
        String value = metadata.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
