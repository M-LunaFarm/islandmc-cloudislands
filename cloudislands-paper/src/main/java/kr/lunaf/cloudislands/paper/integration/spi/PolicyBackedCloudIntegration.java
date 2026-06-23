package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;

public class PolicyBackedCloudIntegration implements CloudIntegration {
    private final String pluginName;
    private final Set<IntegrationCapability> capabilities;
    private final IntegrationExternalRuntime externalRuntime;

    public PolicyBackedCloudIntegration(String pluginName, Set<IntegrationCapability> capabilities) {
        this(pluginName, capabilities, IntegrationExternalRuntime.noop());
    }

    protected PolicyBackedCloudIntegration(String pluginName, Set<IntegrationCapability> capabilities, IntegrationExternalRuntime externalRuntime) {
        this.pluginName = pluginName == null ? "" : pluginName;
        this.capabilities = capabilities == null || capabilities.isEmpty()
            ? Set.of(IntegrationCapability.DETECT)
            : Set.copyOf(capabilities);
        this.externalRuntime = externalRuntime == null ? IntegrationExternalRuntime.noop() : externalRuntime;
    }

    @Override
    public String pluginName() {
        return pluginName;
    }

    @Override
    public String category() {
        return CloudIntegrationPolicy.category(pluginName);
    }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return capabilities;
    }

    @Override
    public IntegrationResult validateVersion(IntegrationContext context) {
        if (!capabilities.contains(IntegrationCapability.VALIDATE_VERSION)) {
            return IntegrationResult.skipped(pluginName + " version validation is not declared");
        }
        Set<String> missingMetadata = context == null ? Set.of("context") : context.missingMetadata("pluginVersion");
        if (!missingMetadata.isEmpty()) {
            return IntegrationResult.failed(pluginName + " version validation missing metadata: " + String.join(",", missingMetadata));
        }
        String pluginVersion = context.metadata().get("pluginVersion");
        String minSupportedVersion = context.metadata().getOrDefault("minSupportedVersion", "");
        if (!minSupportedVersion.isBlank() && compareVersions(pluginVersion, minSupportedVersion) < 0) {
            return IntegrationResult.failed(pluginName + " version " + pluginVersion + " is older than supported " + minSupportedVersion);
        }
        return IntegrationResult.success(pluginName + " version " + pluginVersion + " accepted");
    }

    protected IntegrationResult guardedStateHook(String operation, IntegrationContext context, String... requiredMetadata) {
        CloudIntegrationPolicy.HookDecision decision = validateRuntimeAuthority(context, true);
        if (!decision.allowed()) {
            return IntegrationResult.failed(
                pluginName + " " + operation + " denied: " + String.join(",", decision.violations()),
                failureDetails(operation, context, "violations", String.join(",", decision.violations())));
        }
        return guardedMetadataHook(operation, context, true, requiredMetadata);
    }

    protected IntegrationResult guardedObservationHook(String operation, IntegrationContext context, String... requiredMetadata) {
        CloudIntegrationPolicy.HookDecision decision = validateRuntimeAuthority(context, false);
        if (!decision.allowed()) {
            return IntegrationResult.failed(
                pluginName + " " + operation + " denied: " + String.join(",", decision.violations()),
                failureDetails(operation, context, "violations", String.join(",", decision.violations())));
        }
        return guardedMetadataHook(operation, context, false, requiredMetadata);
    }

    protected IntegrationResult guardedMetadataHook(String operation, IntegrationContext context, boolean stateChanging, String... requiredMetadata) {
        Set<String> missingMetadata = context == null ? Set.of("context") : context.missingMetadata(requiredMetadata);
        if (!missingMetadata.isEmpty()) {
            return IntegrationResult.failed(
                pluginName + " " + operation + " missing metadata: " + String.join(",", missingMetadata),
                failureDetails(operation, context, stateChanging, requiredMetadata, "missingMetadata", String.join(",", missingMetadata)));
        }
        IntegrationOperationPlan plan = IntegrationOperationPlan.of(pluginName, category(), operation, externalApiCall(operation), stateChanging, requiredMetadata);
        LinkedHashMap<String, String> details = new LinkedHashMap<>(successDetails(operation, context, stateChanging, requiredMetadata));
        IntegrationResult external = externalRuntime.invoke(pluginName, category(), operation, context, plan);
        details.put("external.result", external.status().name());
        details.put("external.message", external.message());
        external.details().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> details.put("external.runtime." + entry.getKey(), entry.getValue()));
        if (external.status() == IntegrationResult.Status.FAILED) {
            return IntegrationResult.failed(pluginName + " " + operation + " external hook failed: " + external.message(), details);
        }
        if (external.status() == IntegrationResult.Status.SKIPPED) {
            return IntegrationResult.skipped(pluginName + " " + operation + " external hook skipped: " + external.message(), details);
        }
        return IntegrationResult.success(pluginName + " " + operation + " accepted for island " + context.islandId(), details);
    }

    private Map<String, String> failureDetails(String operation, IntegrationContext context, String key, String value) {
        return failureDetails(operation, context, true, new String[0], key, value);
    }

    private Map<String, String> failureDetails(String operation, IntegrationContext context, boolean stateChanging, String[] requiredMetadata, String key, String value) {
        Map<String, String> details = contextDetails(operation, context, stateChanging, requiredMetadata);
        details.put(key, value == null ? "" : value);
        return details;
    }

    private Map<String, String> successDetails(String operation, IntegrationContext context, boolean stateChanging, String[] requiredMetadata) {
        LinkedHashMap<String, String> details = contextDetails(operation, context, stateChanging, requiredMetadata);
        if (stateChanging) {
            details.putAll(IntegrationStateManifest.from(pluginName, category(), operation, context).details());
        }
        return Map.copyOf(details);
    }

    private LinkedHashMap<String, String> contextDetails(String operation, IntegrationContext context, boolean stateChanging, String[] requiredMetadata) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("plugin", pluginName);
        details.put("operation", operation == null ? "" : operation);
        details.put("external.plugin", pluginName);
        details.put("external.operation", operation == null ? "" : operation);
        String externalApi = externalApiCall(operation);
        if (!externalApi.isBlank()) {
            details.put("external.api", externalApi);
        }
        String externalArtifacts = externalStateArtifacts(operation);
        if (!externalArtifacts.isBlank()) {
            details.put("external.artifacts", externalArtifacts);
        }
        String safetyBarriers = externalSafetyBarriers(operation);
        if (!safetyBarriers.isBlank()) {
            details.put("external.safetyBarriers", safetyBarriers);
        }
        details.putAll(IntegrationOperationPlan.of(pluginName, category(), operation, externalApi, stateChanging, requiredMetadata).details());
        if (context == null) {
            return details;
        }
        details.put("islandId", context.islandId() == null ? "" : context.islandId().toString());
        details.put("nodeId", context.nodeId());
        details.put("fencingToken", Long.toString(context.fencingToken()));
        details.put("nodeOwnsIsland", Boolean.toString(context.nodeOwnsIsland()));
        details.put("idempotencyKey", context.idempotencyKey());
        context.metadata().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> details.put("metadata." + entry.getKey(), entry.getValue()));
        return details;
    }

    protected String externalApiCall(String operation) {
        return "";
    }

    protected String externalStateArtifacts(String operation) {
        return "";
    }

    protected String externalSafetyBarriers(String operation) {
        return "";
    }

    private static int compareVersions(String actual, String minimum) {
        int[] actualParts = versionParts(actual);
        int[] minimumParts = versionParts(minimum);
        int length = Math.max(actualParts.length, minimumParts.length);
        for (int index = 0; index < length; index++) {
            int actualPart = index < actualParts.length ? actualParts[index] : 0;
            int minimumPart = index < minimumParts.length ? minimumParts[index] : 0;
            if (actualPart != minimumPart) {
                return Integer.compare(actualPart, minimumPart);
            }
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        if (value == null || value.isBlank()) {
            return new int[] {0};
        }
        String[] rawParts = value.trim().split("[^0-9]+");
        java.util.ArrayList<Integer> parts = new java.util.ArrayList<>();
        for (String rawPart : rawParts) {
            if (!rawPart.isBlank()) {
                parts.add(Integer.parseInt(rawPart));
            }
        }
        if (parts.isEmpty()) {
            return new int[] {0};
        }
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }
}
