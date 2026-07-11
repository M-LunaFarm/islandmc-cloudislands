package kr.lunaf.cloudislands.paper.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.economy.VaultIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.placeholder.PlaceholderApiIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.CloudIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;

public final class IntegrationRuntimeCertification {
    private static final List<String> PRIORITY_PLUGINS = List.of(
        "Vault",
        "LuckPerms",
        "PlaceholderAPI"
    );

    private IntegrationRuntimeCertification() {
    }

    public static List<String> priorityPlugins() {
        return PRIORITY_PLUGINS;
    }

    public static List<CertificationResult> certifyPriorityPlugins(IntegrationExternalRuntime runtime) {
        IntegrationExternalRuntime externalRuntime = runtime == null ? IntegrationExternalRuntime.noop() : runtime;
        return List.of(
            certify(new VaultIntegration(externalRuntime), Operation.ACTIVATE, context("vault:operation-smoke")),
            certify(new LuckPermsIntegration(externalRuntime), Operation.EXPORT, context("luckperms:permission-smoke")),
            certify(new PlaceholderApiIntegration(externalRuntime), Operation.ACTIVATE, context("placeholderapi:render-smoke"))
        );
    }

    public static CertificationReport report(
            List<PaperIntegrationRegistry.IntegrationStatus> statuses,
            List<CertificationResult> results,
            Map<String, String> pluginVersions) {
        Map<String, PaperIntegrationRegistry.IntegrationStatus> statusByPlugin = new LinkedHashMap<>();
        if (statuses != null) {
            statuses.forEach(status -> statusByPlugin.put(status.pluginName(), status));
        }
        Map<String, CertificationResult> resultByPlugin = new LinkedHashMap<>();
        if (results != null) {
            results.forEach(result -> resultByPlugin.put(result.pluginName(), result));
        }
        Map<String, String> versions = pluginVersions == null ? Map.of() : pluginVersions;
        List<CertificationReportEntry> entries = new ArrayList<>();
        for (String pluginName : CloudIntegrationPolicy.knownPlugins()) {
            PaperIntegrationRegistry.IntegrationStatus status = statusByPlugin.get(pluginName);
            CertificationResult result = resultByPlugin.get(pluginName);
            entries.add(reportEntry(pluginName, status, result, versions));
        }
        resultByPlugin.values().stream()
            .filter(result -> !CloudIntegrationPolicy.knownPlugins().contains(result.pluginName()))
            .sorted(Comparator.comparing(CertificationResult::pluginName))
            .map(result -> reportEntry(result.pluginName(), statusByPlugin.get(result.pluginName()), result, versions))
            .forEach(entries::add);
        return new CertificationReport(Instant.now(), entries);
    }

    private static CertificationReportEntry reportEntry(
            String pluginName,
            PaperIntegrationRegistry.IntegrationStatus status,
            CertificationResult result,
            Map<String, String> pluginVersions) {
        String category = status == null ? CloudIntegrationPolicy.category(pluginName) : status.category();
        boolean enabled = status != null && status.enabled();
        IntegrationSupportState state = status == null ? IntegrationSupportState.NOT_INSTALLED : status.state();
        IntegrationSupportState discoveryState = status == null ? IntegrationSupportState.NOT_INSTALLED : status.discoveryState();
        IntegrationSupportState apiState = status == null ? IntegrationSupportState.NOT_INSTALLED : status.apiState();
        IntegrationSupportState adapterState = status == null ? IntegrationSupportState.ADAPTER_INACTIVE : status.adapterState();
        IntegrationSupportState operationState = result == null ? null : result.operationState();
        String operation = result == null ? "" : result.operation();
        String resultStatus = result == null ? "" : result.resultStatus().name();
        boolean certified = result != null && result.certified();
        Map<String, String> details = result == null ? Map.of() : result.details();
        String version = firstNonBlank(
            pluginVersions.get(pluginName),
            details.get("external.runtime.pluginVersion"),
            details.get("metadata.pluginVersion"),
            details.get("pluginVersion")
        );
        String remediation = remediation(pluginName, state, operationState, details);
        return new CertificationReportEntry(
            pluginName,
            category,
            version,
            enabled,
            state,
            discoveryState,
            apiState,
            adapterState,
            operation,
            operationState,
            resultStatus,
            certified,
            remediation,
            details
        );
    }

    private static String remediation(String pluginName, IntegrationSupportState state, IntegrationSupportState operationState, Map<String, String> details) {
        if (operationState == IntegrationSupportState.OPERATION_FAILED) {
            String evidence = details.getOrDefault("external.evidenceRequired", "");
            String externalMessage = firstNonBlank(details.get("external.message"), details.get("external.runtime.external.message"));
            StringBuilder builder = new StringBuilder();
            builder.append("Verify ").append(pluginName).append(" is enabled on the island runtime node, then rerun the operation smoke.");
            if (!evidence.isBlank()) {
                builder.append(" Provide ").append(evidence).append(" evidence.");
            }
            if (!externalMessage.isBlank()) {
                builder.append(" Runtime message: ").append(externalMessage).append('.');
            }
            return builder.toString();
        }
        if (state == IntegrationSupportState.NOT_INSTALLED) {
            return "Install and enable " + pluginName + " on every node that can own an island runtime.";
        }
        if (state == IntegrationSupportState.API_INCOMPATIBLE) {
            return "Upgrade " + pluginName + " to a compatible API version and rerun /ciadmin integrations report.";
        }
        if (state == IntegrationSupportState.UNSUPPORTED) {
            return "Add a CloudIslands adapter for " + pluginName + " before depending on runtime state transfer.";
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static CertificationResult certify(CloudIntegration integration, Operation operation, IntegrationContext context) {
        IntegrationResult result = switch (operation) {
            case ACTIVATE -> integration.onIslandActivate(context);
            case EXPORT -> integration.exportState(context);
        };
        IntegrationSupportState operationState = PaperIntegrationRegistry.operationState(result);
        return new CertificationResult(
            integration.pluginName(),
            operation.name(),
            operationState,
            result.status(),
            CloudIntegrationPolicy.requiresRuntimeAuthority(integration.pluginName(), false),
            CloudIntegrationPolicy.requiredRuntimeClaims(),
            result.details()
        );
    }

    private static IntegrationContext context(String idempotencyKey) {
        return new IntegrationContext(
            UUID.fromString("00000000-0000-0000-0000-000000000911"),
            "island-node-certification",
            911L,
            true,
            idempotencyKey,
            metadata()
        );
    }

    private static Map<String, String> metadata() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("world", "cloudislands_cert_world");
        metadata.put("cell", "4,-2");
        metadata.put("region", "64,0,-32..127,319,31");
        metadata.put("bundleKey", "certification/island-000000000911.tar.zst");
        metadata.put("activeOperationsDrained", "true");
        metadata.put("editSessionFlushed", "true");
        metadata.put("permissionNode", "cloudislands.island.member");
        metadata.put("bypassScope", "island");
        metadata.put("contextKey", "cloudislands:island");
        metadata.put("providerName", "Vault");
        metadata.put("currency", "coins");
        metadata.put("testAccount", "00000000-0000-0000-0000-000000000911");
        metadata.put("economyTransactionId", "vault-certification-000000000911");
        metadata.put("placeholderKeys", "%cloudislands_island_level%,%cloudislands_island_bank%");
        metadata.put("renderTarget", "certification-player");
        return Map.copyOf(metadata);
    }

    private enum Operation {
        ACTIVATE,
        EXPORT
    }

    public record CertificationResult(
        String pluginName,
        String operation,
        IntegrationSupportState operationState,
        IntegrationResult.Status resultStatus,
        boolean runtimeAuthorityRequired,
        List<String> requiredRuntimeClaims,
        Map<String, String> details
    ) {
        public boolean certified() {
            return operationState == IntegrationSupportState.OPERATION_SUCCEEDED && resultStatus == IntegrationResult.Status.SUCCESS;
        }
    }

    public record CertificationReport(
        Instant generatedAt,
        List<CertificationReportEntry> entries
    ) {
        public CertificationReport {
            generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public List<CertificationReportEntry> failedOperations() {
            return entries.stream()
                .filter(entry -> entry.operationState() == IntegrationSupportState.OPERATION_FAILED)
                .toList();
        }

        public String summaryLine() {
            long certifiedCount = entries.stream().filter(CertificationReportEntry::certified).count();
            long failedCount = failedOperations().size();
            return "plugins=" + entries.size() + " certified=" + certifiedCount + " failed=" + failedCount;
        }

        public String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            jsonField(builder, "generatedAt", generatedAt.toString()).append(',');
            jsonField(builder, "summary", summaryLine()).append(',');
            builder.append("\"entries\":[");
            for (int index = 0; index < entries.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(entries.get(index).toJson());
            }
            builder.append("]}");
            return builder.toString();
        }

        public String toMarkdown() {
            StringBuilder builder = new StringBuilder();
            builder.append("# CloudIslands Integration Certification\n\n");
            builder.append("- Generated: ").append(generatedAt).append('\n');
            builder.append("- Summary: ").append(summaryLine()).append("\n\n");
            builder.append("| Plugin | Version | State | Operation | Result | Certified | Remediation |\n");
            builder.append("| --- | --- | --- | --- | --- | --- | --- |\n");
            for (CertificationReportEntry entry : entries) {
                builder.append("| ")
                    .append(markdownCell(entry.pluginName())).append(" | ")
                    .append(markdownCell(entry.pluginVersion())).append(" | ")
                    .append(entry.state()).append(" | ")
                    .append(markdownCell(entry.operation())).append(" | ")
                    .append(entry.operationState() == null ? "" : entry.operationState()).append(" | ")
                    .append(entry.certified()).append(" | ")
                    .append(markdownCell(entry.remediation())).append(" |\n");
            }
            if (!failedOperations().isEmpty()) {
                builder.append("\n## Remediation\n");
                failedOperations().forEach(entry -> builder.append("- ")
                    .append(entry.pluginName())
                    .append(": ")
                    .append(entry.remediation())
                    .append('\n'));
            }
            return builder.toString();
        }
    }

    public record CertificationReportEntry(
        String pluginName,
        String category,
        String pluginVersion,
        boolean enabled,
        IntegrationSupportState state,
        IntegrationSupportState discoveryState,
        IntegrationSupportState apiState,
        IntegrationSupportState adapterState,
        String operation,
        IntegrationSupportState operationState,
        String resultStatus,
        boolean certified,
        String remediation,
        Map<String, String> details
    ) {
        public CertificationReportEntry {
            pluginName = Objects.toString(pluginName, "");
            category = Objects.toString(category, "");
            pluginVersion = Objects.toString(pluginVersion, "");
            state = state == null ? IntegrationSupportState.NOT_INSTALLED : state;
            discoveryState = discoveryState == null ? IntegrationSupportState.NOT_INSTALLED : discoveryState;
            apiState = apiState == null ? IntegrationSupportState.NOT_INSTALLED : apiState;
            adapterState = adapterState == null ? IntegrationSupportState.ADAPTER_INACTIVE : adapterState;
            operation = Objects.toString(operation, "");
            resultStatus = Objects.toString(resultStatus, "");
            remediation = Objects.toString(remediation, "");
            details = details == null ? Map.of() : Map.copyOf(details);
        }

        private String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            jsonField(builder, "pluginName", pluginName).append(',');
            jsonField(builder, "category", category).append(',');
            jsonField(builder, "pluginVersion", pluginVersion).append(',');
            builder.append("\"enabled\":").append(enabled).append(',');
            jsonField(builder, "state", state.name()).append(',');
            jsonField(builder, "discoveryState", discoveryState.name()).append(',');
            jsonField(builder, "apiState", apiState.name()).append(',');
            jsonField(builder, "adapterState", adapterState.name()).append(',');
            jsonField(builder, "operation", operation).append(',');
            jsonField(builder, "operationState", operationState == null ? "" : operationState.name()).append(',');
            jsonField(builder, "resultStatus", resultStatus).append(',');
            builder.append("\"certified\":").append(certified).append(',');
            jsonField(builder, "remediation", remediation).append(',');
            builder.append("\"details\":{");
            List<Map.Entry<String, String>> sortedDetails = details.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
            for (int index = 0; index < sortedDetails.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                Map.Entry<String, String> entry = sortedDetails.get(index);
                jsonField(builder, entry.getKey(), entry.getValue());
            }
            builder.append("}}");
            return builder.toString();
        }
    }

    private static StringBuilder jsonField(StringBuilder builder, String key, String value) {
        return builder.append('"').append(jsonEscape(key)).append("\":\"").append(jsonEscape(value)).append('"');
    }

    private static String jsonEscape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String markdownCell(String value) {
        return Objects.toString(value, "").replace("|", "\\|").replace('\n', ' ');
    }
}
