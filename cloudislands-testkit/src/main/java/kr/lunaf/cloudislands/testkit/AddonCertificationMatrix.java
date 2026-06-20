package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public final class AddonCertificationMatrix {
    public static final String CERTIFICATION_LEVEL = "cloudislands-addon-sdk-1";
    public static final String MATRIX_POLICY = "certifies-packaging-metadata-runtime-api-removal-safety-events-threading-failure-and-timeout-contracts";

    private AddonCertificationMatrix() {
    }

    public static AddonCertificationReport certify(CloudIslandsAddon addon, Map<String, String> runtimeContractMetadata) {
        if (addon == null) {
            return certify("null-addon", Map.of(), runtimeContractMetadata);
        }
        Map<String, String> metadata = new java.util.LinkedHashMap<>(addon.addonStandardMetadata());
        metadata.putAll(safeMetadata(addon.addonMetadata()));
        return certify(addon.addonId(), metadata, runtimeContractMetadata);
    }

    public static AddonCertificationReport certify(CloudIslandsAddonSnapshot snapshot) {
        if (snapshot == null) {
            return certify("null-addon", Map.of(), Map.of());
        }
        return certify(snapshot.id(), snapshot.metadata(), Map.of());
    }

    public static AddonCertificationReport certify(String addonId, Map<String, String> addonMetadata, Map<String, String> runtimeContractMetadata) {
        Map<String, String> certifiedMetadata = ApiContractVerifier.addonCertificationMetadata(addonMetadata, runtimeContractMetadata);
        ApiContractVerification verification = ApiContractVerifier.verifyAddon(addonId, ApiContractVerifier.requestedApiVersion(certifiedMetadata), certifiedMetadata);
        List<AddonCertificationCheck> checks = new ArrayList<>();
        checks.add(check("api-contract", "api", true, verification.passed(), String.join(",", verification.failures())));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-requested-version", "api", true));
        checks.add(allowedValue(certifiedMetadata, "addon-packaging", "packaging", true, List.of(
            CloudIslandsAddon.PACKAGING_EXTERNAL_PLUGIN,
            CloudIslandsAddon.PACKAGING_BUILT_IN_FEATURE_PACK,
            CloudIslandsAddon.PACKAGING_BUILT_IN_COMPATIBLE
        )));
        checks.add(csvContains(certifiedMetadata, "addon-supported-packaging", "packaging", true, CloudIslandsAddon.PACKAGING_EXTERNAL_PLUGIN));
        checks.add(booleanValue(certifiedMetadata, "addon-removal-safe", "lifecycle", true, true));
        checks.add(booleanValue(certifiedMetadata, "addon-runtime-owns-islands", "lifecycle", true, false));
        checks.add(booleanValue(certifiedMetadata, "addon-core-lifecycle-owner", "lifecycle", true, false));
        checks.add(metadataEquals(certifiedMetadata, "addon-removal-policy", "lifecycle", true, "missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore"));
        checks.add(metadataEquals(certifiedMetadata, "addon-reconnect-policy", "lifecycle", true, "reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid"));
        checks.add(metadataEquals(certifiedMetadata, "addon-event-source", "events", true, "cloudislands-global-event-stream"));
        checks.add(metadataEquals(certifiedMetadata, "addon-event-delivery", "events", true, "typed-cloud-event-callbacks-through-cloudislands-api"));
        checks.add(metadataEquals(certifiedMetadata, "addon-event-failure-policy", "events", true, "addon-callback-exceptions-are-logged-and-isolated"));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-threading-policy", "threading", true));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-core-failure-policy", "failure", true));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-timeout-retry-policy", "timeout", true));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-event-delivery-policy", "events", true));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-api-compatibility-testkit-policy", "testkit", true));
        checks.add(metadataPresent(certifiedMetadata, "cloudislands-addon-certification-policy", "testkit", true));
        checks.add(metadataPresent(certifiedMetadata, "feature-dependencies", "features", false));
        return new AddonCertificationReport(addonId, CERTIFICATION_LEVEL, verification, checks);
    }

    private static Map<String, String> safeMetadata(Map<String, String> source) {
        return source == null ? Map.of() : source;
    }

    private static AddonCertificationCheck metadataPresent(Map<String, String> metadata, String key, String category, boolean required) {
        String value = metadata.get(key);
        return check(key, category, required, value != null && !value.isBlank(), "missing-or-blank");
    }

    private static AddonCertificationCheck metadataEquals(Map<String, String> metadata, String key, String category, boolean required, String expected) {
        String value = metadata.get(key);
        return check(key, category, required, expected.equals(value), "expected=" + expected + ",actual=" + (value == null ? "" : value));
    }

    private static AddonCertificationCheck booleanValue(Map<String, String> metadata, String key, String category, boolean required, boolean expected) {
        String value = metadata.get(key);
        return check(key, category, required, Boolean.toString(expected).equals(value), "expected=" + expected + ",actual=" + (value == null ? "" : value));
    }

    private static AddonCertificationCheck allowedValue(Map<String, String> metadata, String key, String category, boolean required, List<String> allowed) {
        String value = metadata.get(key);
        return check(key, category, required, value != null && allowed.contains(value), "allowed=" + String.join(",", allowed) + ",actual=" + (value == null ? "" : value));
    }

    private static AddonCertificationCheck csvContains(Map<String, String> metadata, String key, String category, boolean required, String expected) {
        String value = metadata.get(key);
        boolean contains = value != null && List.of(value.split(",")).stream().map(String::trim).anyMatch(expected::equals);
        return check(key, category, required, contains, "expected-token=" + expected + ",actual=" + (value == null ? "" : value));
    }

    private static AddonCertificationCheck check(String id, String category, boolean required, boolean passed, String detail) {
        return new AddonCertificationCheck(id, category, required, passed, passed ? "passed" : detail);
    }
}
