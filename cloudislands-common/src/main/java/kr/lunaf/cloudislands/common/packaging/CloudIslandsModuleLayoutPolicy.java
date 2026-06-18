package kr.lunaf.cloudislands.common.packaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CloudIslandsModuleLayoutPolicy {
    private static final List<String> REQUIRED_MODULES = List.of(
        "cloudislands-api",
        "cloudislands-common",
        "cloudislands-protocol",
        "cloudislands-core-client",
        "cloudislands-core-service",
        "cloudislands-velocity",
        "cloudislands-paper",
        "cloudislands-storage",
        "cloudislands-migration",
        "cloudislands-testkit",
        "cloudislands-bom"
    );

    private static final List<String> OPTIONAL_EXTENSION_MODULES = List.of(
        "cloudislands-satis"
    );

    private static final Map<String, List<String>> MODULE_RESPONSIBILITIES = responsibilities();
    private static final Map<String, List<String>> MODULE_RUNTIME_SURFACES = runtimeSurfaces();
    private static final Map<String, List<String>> DISTRIBUTION_ARTIFACTS = distributionArtifacts();
    private static final Map<String, List<String>> DISTRIBUTION_TASKS = distributionTasks();

    private CloudIslandsModuleLayoutPolicy() {
    }

    public static List<String> requiredModules() {
        return REQUIRED_MODULES;
    }

    public static List<String> optionalExtensionModules() {
        return OPTIONAL_EXTENSION_MODULES;
    }

    public static Map<String, List<String>> moduleResponsibilities() {
        return MODULE_RESPONSIBILITIES;
    }

    public static Map<String, List<String>> distributionArtifacts() {
        return DISTRIBUTION_ARTIFACTS;
    }

    public static Map<String, List<String>> distributionTasks() {
        return DISTRIBUTION_TASKS;
    }

    public static Map<String, List<String>> moduleRuntimeSurfaces() {
        return MODULE_RUNTIME_SURFACES;
    }

    public static String requiredModuleSummary() {
        return String.join(",", REQUIRED_MODULES);
    }

    public static String optionalExtensionModuleSummary() {
        return String.join(",", OPTIONAL_EXTENSION_MODULES);
    }

    public static String moduleResponsibilitySummary() {
        return summarize(MODULE_RESPONSIBILITIES);
    }

    public static String moduleRuntimeSurfaceSummary() {
        return summarize(MODULE_RUNTIME_SURFACES);
    }

    public static String distributionArtifactSummary() {
        return summarize(DISTRIBUTION_ARTIFACTS);
    }

    public static String distributionTaskSummary() {
        return summarize(DISTRIBUTION_TASKS);
    }

    public static boolean requiredModule(String moduleName) {
        return moduleName != null && REQUIRED_MODULES.contains(moduleName);
    }

    public static boolean optionalExtensionModule(String moduleName) {
        return moduleName != null && OPTIONAL_EXTENSION_MODULES.contains(moduleName);
    }

    public static boolean knownModule(String moduleName) {
        return requiredModule(moduleName) || optionalExtensionModule(moduleName);
    }

    private static Map<String, List<String>> responsibilities() {
        LinkedHashMap<String, List<String>> modules = new LinkedHashMap<>();
        modules.put("cloudislands-api", List.of("interfaces", "events", "dto-snapshots", "permission-enums", "service-contracts"));
        modules.put("cloudislands-common", List.of("config", "serialization", "messages", "utils", "result-types", "exceptions"));
        modules.put("cloudislands-protocol", List.of("request-response-dto", "event-dto", "job-dto", "route-ticket-dto", "version-negotiation"));
        modules.put("cloudislands-core-client", List.of("typed-core-api-client", "timeouts", "auth", "json-transport"));
        modules.put("cloudislands-core-service", List.of("rest-grpc-server", "postgresql-repositories", "redis-event-bus", "routing-allocator", "job-scheduler", "transaction-manager", "admin-api"));
        modules.put("cloudislands-velocity", List.of("commands", "routing", "connection-handling", "fallback", "route-ticket-integration"));
        modules.put("cloudislands-paper", List.of("lobby-role", "island-node-role", "protection", "world-shard-cell-manager", "teleport-manager", "gui", "events"));
        modules.put("cloudislands-storage", List.of("s3-minio-backend", "local-filesystem-backend", "bundle-manifest", "snapshot-compression", "checksum", "restore-pipeline"));
        modules.put("cloudislands-migration", List.of("superiorskyblock2-importer", "dry-run-validator", "world-extractor", "report-generator"));
        modules.put("cloudislands-testkit", List.of("fixtures", "fake-repositories", "integration-test-helpers"));
        modules.put("cloudislands-bom", List.of("dependency-alignment", "published-version-coordinates"));
        modules.put("cloudislands-satis", List.of("satismc-feature-bridge", "config-gated-addon-runtime", "legacy-feature-migration", "addon-descriptor-sidecar"));
        return Collections.unmodifiableMap(modules);
    }

    private static Map<String, List<String>> distributionArtifacts() {
        LinkedHashMap<String, List<String>> artifacts = new LinkedHashMap<>();
        artifacts.put("plugins", List.of("cloudislands-paper", "cloudislands-velocity"));
        artifacts.put("addons", List.of("cloudislands-satis"));
        artifacts.put("services", List.of("cloudislands-core-service"));
        artifacts.put("libraries", List.of("cloudislands-api", "cloudislands-common", "cloudislands-protocol", "cloudislands-core-client", "cloudislands-storage", "cloudislands-migration"));
        artifacts.put("platform", List.of("cloudislands-bom", "cloudislands-testkit"));
        return Collections.unmodifiableMap(artifacts);
    }

    private static Map<String, List<String>> distributionTasks() {
        LinkedHashMap<String, List<String>> tasks = new LinkedHashMap<>();
        tasks.put("distPlugins", List.of("cloudislands-paper", "cloudislands-velocity"));
        tasks.put("distAddons", List.of("cloudislands-satis"));
        tasks.put("distAddonDescriptors", List.of("cloudislands-satis-descriptor"));
        tasks.put("distServices", List.of("cloudislands-core-service"));
        tasks.put("distTools", List.of("cloudislands-migration"));
        tasks.put("distDeveloperKit", List.of("cloudislands-api", "cloudislands-common", "cloudislands-protocol", "cloudislands-core-client", "cloudislands-storage", "cloudislands-migration", "cloudislands-testkit", "cloudislands-bom"));
        tasks.put("distBundle", List.of("plugins", "addons", "services", "tools", "devkit"));
        tasks.put("distAddonBundle", List.of("addons", "addon-descriptors"));
        return Collections.unmodifiableMap(tasks);
    }

    private static Map<String, List<String>> runtimeSurfaces() {
        LinkedHashMap<String, List<String>> surfaces = new LinkedHashMap<>();
        surfaces.put("cloudislands-core-service", List.of("rest-api", "grpc-ready-boundary", "admin-api", "routing-allocator", "job-scheduler", "transaction-boundary"));
        surfaces.put("cloudislands-velocity", List.of("player-command-entrypoint", "route-ticket-router", "connection-fallback", "status-and-metrics-endpoints"));
        surfaces.put("cloudislands-paper", List.of("lobby-gui-role", "island-node-runtime-role", "protection-listeners", "teleport-consumer", "world-cell-manager", "bukkit-addon-events"));
        surfaces.put("cloudislands-storage", List.of("s3-minio-object-storage", "local-filesystem-fallback", "bundle-manifest", "snapshot-restore-pipeline"));
        surfaces.put("cloudislands-migration", List.of("superiorskyblock2-readonly-scan", "manifest-dryrun", "world-cell-extract", "import-verify-rollback"));
        return Collections.unmodifiableMap(surfaces);
    }

    private static String summarize(Map<String, List<String>> values) {
        StringBuilder summary = new StringBuilder();
        values.forEach((key, entries) -> {
            if (!summary.isEmpty()) {
                summary.append(';');
            }
            summary.append(key).append('=').append(String.join("+", entries));
        });
        return summary.toString();
    }
}
