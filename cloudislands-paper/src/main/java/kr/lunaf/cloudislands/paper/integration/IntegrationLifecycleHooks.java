package kr.lunaf.cloudislands.paper.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.integration.spi.CloudIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;

public final class IntegrationLifecycleHooks {
    private static final IntegrationLifecycleHooks NOOP = new IntegrationLifecycleHooks(
        "",
        _capability -> List.of(),
        (_request, _context) -> IntegrationResult.skipped("integration lifecycle hooks are disabled")
    );

    private final String nodeId;
    private final Function<IntegrationCapability, List<String>> pluginSelector;
    private final BiFunction<HookRequest, IntegrationContext, IntegrationResult> invoker;

    private IntegrationLifecycleHooks(
        String nodeId,
        Function<IntegrationCapability, List<String>> pluginSelector,
        BiFunction<HookRequest, IntegrationContext, IntegrationResult> invoker
    ) {
        this.nodeId = nodeId == null ? "" : nodeId.trim();
        this.pluginSelector = pluginSelector;
        this.invoker = invoker;
    }

    public static IntegrationLifecycleHooks noop() {
        return NOOP;
    }

    public static IntegrationLifecycleHooks fromRegistry(PaperIntegrationRegistry registry, String nodeId) {
        if (registry == null) {
            return noop();
        }
        return new IntegrationLifecycleHooks(
            nodeId,
            capability -> registry.snapshot().stream()
                .filter(PaperIntegrationRegistry.IntegrationStatus::enabled)
                .filter(status -> status.capabilities().contains(capability))
                .map(PaperIntegrationRegistry.IntegrationStatus::pluginName)
                .toList(),
            (request, context) -> switch (request.capability()) {
                case ISLAND_ACTIVATE -> registry.onIslandActivate(request.pluginName(), context);
                case ISLAND_DEACTIVATE -> registry.onIslandDeactivate(request.pluginName(), context);
                case STATE_RESTORE -> registry.restoreState(request.pluginName(), context);
                default -> registry.exportState(request.pluginName(), context);
            }
        );
    }

    static IntegrationLifecycleHooks direct(String nodeId, List<CloudIntegration> integrations) {
        List<CloudIntegration> safeIntegrations = integrations == null ? List.of() : List.copyOf(integrations);
        return new IntegrationLifecycleHooks(
            nodeId,
            capability -> safeIntegrations.stream()
                .filter(integration -> integration.capabilities().contains(capability))
                .map(CloudIntegration::pluginName)
                .toList(),
            (request, context) -> safeIntegrations.stream()
                .filter(integration -> integration.pluginName().equals(request.pluginName()))
                .findFirst()
                .map(integration -> switch (request.capability()) {
                    case ISLAND_ACTIVATE -> integration.onIslandActivate(context);
                    case ISLAND_DEACTIVATE -> integration.onIslandDeactivate(context);
                    case STATE_RESTORE -> integration.restoreState(context);
                    default -> integration.exportState(context);
                })
                .orElseGet(() -> IntegrationResult.skipped(request.pluginName() + " integration is not registered"))
        );
    }

    public LifecycleBatch onIslandActivated(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) {
        if (islandId == null || activeIsland == null) {
            return LifecycleBatch.empty("activate");
        }
        IntegrationContext context = lifecycleContext("island-activate", islandId, activeIsland, 0L, null, "");
        return invoke("activate", IntegrationCapability.ISLAND_ACTIVATE, context);
    }

    public LifecycleBatch onIslandDeactivated(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path bundlePath) {
        if (islandId == null || activeIsland == null) {
            return LifecycleBatch.empty("deactivate");
        }
        IntegrationContext context = lifecycleContext("island-deactivate", islandId, activeIsland, 0L, bundlePath, "");
        return invoke("deactivate", IntegrationCapability.ISLAND_DEACTIVATE, context);
    }

    public LifecycleBatch exportState(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, long snapshotNo, Path bundlePath) {
        if (islandId == null || activeIsland == null) {
            return LifecycleBatch.empty("export");
        }
        IntegrationContext context = lifecycleContext("bundle-export", islandId, activeIsland, snapshotNo, bundlePath, "");
        return invoke("export", IntegrationCapability.STATE_EXPORT, context);
    }

    private IntegrationContext lifecycleContext(
        String operation,
        UUID islandId,
        ActiveIslandRegistry.ActiveIsland activeIsland,
        long snapshotNo,
        Path bundlePath,
        String storagePath
    ) {
        Map<String, String> metadata = bundleMetadata(
            activeIsland.worldName(),
            activeIsland.cellX(),
            activeIsland.cellZ(),
            activeIsland.originX(),
            activeIsland.originZ(),
            activeIsland.islandSize(),
            snapshotNo,
            bundlePath,
            storagePath
        );
        String safeOperation = operation == null || operation.isBlank() ? "integration" : operation;
        return new IntegrationContext(
            islandId,
            nodeId,
            activeIsland.fencingToken(),
            true,
            safeOperation + ":" + islandId + ":" + Math.max(0L, snapshotNo),
            metadata
        );
    }

    public LifecycleBatch restoreState(
        UUID islandId,
        String worldName,
        int cellX,
        int cellZ,
        int originX,
        int originZ,
        long fencingToken,
        long snapshotNo,
        String storagePath,
        Path bundlePath,
        IslandBundleManifest manifest
    ) {
        if (islandId == null || manifest == null) {
            return LifecycleBatch.empty("restore");
        }
        Map<String, String> metadata = bundleMetadata(
            worldName,
            cellX,
            cellZ,
            originX,
            originZ,
            manifest.size(),
            snapshotNo,
            bundlePath,
            storagePath
        );
        metadata.put("rollbackSeconds", "0");
        IntegrationContext context = new IntegrationContext(
            islandId,
            nodeId,
            fencingToken,
            fencingToken > 0L,
            "bundle-restore:" + islandId + ":" + (snapshotNo > 0L ? snapshotNo : "latest") + ":" + metadata.get("bundleKey"),
            metadata
        );
        return invoke("restore", IntegrationCapability.STATE_RESTORE, context);
    }

    private LifecycleBatch invoke(String operation, IntegrationCapability capability, IntegrationContext context) {
        List<LifecycleResult> results = new ArrayList<>();
        for (String pluginName : pluginSelector.apply(capability)) {
            IntegrationResult result = invoker.apply(new HookRequest(pluginName, capability), context);
            results.add(new LifecycleResult(pluginName, result.status(), result.message(), result.details()));
        }
        return new LifecycleBatch(operation, context, results);
    }

    private Map<String, String> bundleMetadata(
        String worldName,
        int cellX,
        int cellZ,
        int originX,
        int originZ,
        int islandSize,
        long snapshotNo,
        Path bundlePath,
        String storagePath
    ) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("world", worldName == null ? "" : worldName);
        metadata.put("cell", cellX + "," + cellZ);
        metadata.put("origin", originX + "," + originZ);
        metadata.put("region", region(originX, originZ, islandSize));
        metadata.put("islandSize", Integer.toString(Math.max(1, islandSize)));
        metadata.put("snapshotNo", Long.toString(Math.max(0L, snapshotNo)));
        metadata.put("bundleKey", bundlePath == null || bundlePath.getFileName() == null ? "" : bundlePath.getFileName().toString());
        metadata.put("bundlePath", bundlePath == null ? "" : bundlePath.toString());
        metadata.put("storagePath", storagePath == null ? "" : storagePath);
        metadata.put("namespace", "cloudislands");
        metadata.put("activeOperationsDrained", "true");
        metadata.put("editSessionFlushed", "true");
        metadata.put("externalBlockIds", "cloudislands:bundle-state");
        metadata.put("coreBlockValueKeys", "limits.blocks.effective");
        metadata.put("entityCountKey", "limits.entities.effective");
        metadata.put("spawnerCountKey", "limits.spawners.effective");
        metadata.put("permissionNode", "cloudislands.island.runtime");
        metadata.put("bypassScope", "island-runtime");
        metadata.put("contextKey", "cloudislands-island");
        metadata.put("analyticsScope", "island-runtime");
        metadata.put("presenceKey", worldName + ":" + cellX + "," + cellZ);
        return metadata;
    }

    private String region(int originX, int originZ, int islandSize) {
        int maxX = originX + Math.max(1, islandSize) - 1;
        int maxZ = originZ + Math.max(1, islandSize) - 1;
        return originX + ",0," + originZ + ".." + maxX + ",319," + maxZ;
    }

    private record HookRequest(String pluginName, IntegrationCapability capability) {}

    public record LifecycleBatch(String operation, IntegrationContext context, List<LifecycleResult> results) {
        public LifecycleBatch {
            operation = operation == null ? "" : operation;
            results = results == null ? List.of() : List.copyOf(results);
        }

        public static LifecycleBatch empty(String operation) {
            return new LifecycleBatch(operation, null, List.of());
        }

        public void throwIfFailed() throws IOException {
            List<String> failures = results.stream()
                .filter(result -> result.status() == IntegrationResult.Status.FAILED)
                .map(result -> result.pluginName() + ": " + result.message())
                .toList();
            if (!failures.isEmpty()) {
                throw new IOException("integration " + operation + " hook failed: " + String.join("; ", failures));
            }
        }

        public void writeIfPresent(Path path) throws IOException {
            if (results.isEmpty() || path == null) {
                return;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, SimpleJson.stringify(toJson()), StandardCharsets.UTF_8);
        }

        private Map<String, Object> toJson() {
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            root.put("operation", operation);
            if (context != null) {
                root.put("islandId", context.islandId() == null ? "" : context.islandId().toString());
                root.put("nodeId", context.nodeId());
                root.put("fencingToken", context.fencingToken());
                root.put("nodeOwnsIsland", context.nodeOwnsIsland());
                root.put("idempotencyKey", context.idempotencyKey());
                root.put("metadata", context.metadata());
            }
            root.put("results", results.stream().map(LifecycleResult::toJson).toList());
            root.put("stateManifests", results.stream()
                .map(LifecycleResult::stateManifestJson)
                .filter(map -> !map.isEmpty())
                .toList());
            return root;
        }
    }

    public record LifecycleResult(String pluginName, IntegrationResult.Status status, String message, Map<String, String> details) {
        public LifecycleResult {
            pluginName = pluginName == null ? "" : pluginName;
            status = status == null ? IntegrationResult.Status.SKIPPED : status;
            message = message == null ? "" : message;
            details = details == null ? Map.of() : Map.copyOf(details);
        }

        private Map<String, Object> toJson() {
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            root.put("pluginName", pluginName);
            root.put("status", status.name());
            root.put("message", message);
            root.put("details", details);
            return root;
        }

        private Map<String, Object> stateManifestJson() {
            if (status != IntegrationResult.Status.SUCCESS || details.getOrDefault("manifest.plugin", "").isBlank()) {
                return Map.of();
            }
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            details.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("manifest."))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> root.put(entry.getKey().substring("manifest.".length()), entry.getValue()));
            return root;
        }
    }
}
