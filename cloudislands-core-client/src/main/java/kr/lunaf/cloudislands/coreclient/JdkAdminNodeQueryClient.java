package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.NodeLevelScanSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.NodeStorageSnapshot;

final class JdkAdminNodeQueryClient implements AdminNodeQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminNodeQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<IslandNodeSnapshot>> nodes() {
        return core.postResultBody("/v1/admin/nodes/list", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminNodeQueryClient::nodes);
    }

    @Override
    public CompletableFuture<AdminNodeSummaryView> listNodesSummary() {
        return core.postResultBody("/v1/admin/nodes/list", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminNodeQueryClient::summary);
    }

    @Override
    public CompletableFuture<Optional<IslandNodeSnapshot>> nodeSnapshot(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return core.postResultBody("/v1/admin/nodes/info", CoreJsonPayload.object("nodeId", normalizedNodeId))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> node(normalizedNodeId, body));
    }

    @Override
    public CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return core.postResultBody("/v1/admin/nodes/info", CoreJsonPayload.object("nodeId", normalizedNodeId))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> nodeSummary(normalizedNodeId, body));
    }

    @Override
    public CompletableFuture<List<AdminIslandRuntimeView>> nodeIslandRuntimes(String nodeId, int limit) {
        return core.postResultBody("/v1/admin/nodes/islands", CoreJsonPayload.object("nodeId", requireNode(nodeId), "limit", Math.max(1, Math.min(limit, 100))))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminNodeQueryClient::runtimes);
    }

    @Override
    public CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit) {
        return core.postResultBody("/v1/admin/nodes/islands", CoreJsonPayload.object("nodeId", requireNode(nodeId), "limit", Math.max(1, Math.min(limit, 100))))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminNodeQueryClient::summary);
    }

    static List<IslandNodeSnapshot> nodes(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "nodes").stream()
            .map(node -> node(CoreJson.firstText(node, "nodeId", "id"), node))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    static Optional<IslandNodeSnapshot> node(String fallbackNodeId, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Map<?, ?> root = CoreJson.object(body);
        if (root.containsKey("error") || root.containsKey("code")) {
            return Optional.empty();
        }
        return node(fallbackNodeId, root);
    }

    private static Optional<IslandNodeSnapshot> node(String fallbackNodeId, Map<?, ?> root) {
        String nodeId = CoreJson.firstText(root, "nodeId", "id");
        if (nodeId.isBlank()) {
            nodeId = fallbackNodeId == null ? "" : fallbackNodeId;
        }
        if (nodeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandNodeSnapshot(
            nodeId,
            CoreJson.text(root, "pool"),
            CoreJson.firstText(root, "serverName", "server"),
            CoreJson.text(root, "nodeVersion"),
            enumValue(CoreJson.text(root, "state")),
            intValue(root, "players"),
            intValue(root, "softPlayerCap"),
            intValue(root, "hardPlayerCap"),
            intValue(root, "reservedSlots"),
            intValue(root, "activeIslands"),
            intValue(root, "maxActiveIslands"),
            CoreJson.decimal(root, "mspt"),
            intValue(root, "activationQueue"),
            intValue(root, "maxActivationQueue"),
            CoreJson.decimal(root, "chunkLoadPressure"),
            CoreJson.number(root, "heapUsedMb"),
            CoreJson.number(root, "heapMaxMb"),
            intValue(root, "recentFailurePenalty"),
            CoreJson.bool(root, "storageAvailable"),
            CoreJson.text(root, "supportedTemplates"),
            instant(CoreJson.text(root, "lastHeartbeat")),
            CoreJson.decimal(root, "score"),
            decimalMap(CoreJson.objectValue(root, "scoreBreakdown")),
            CoreJson.bool(root, "eligibleForNewActivation"),
            CoreJson.text(root, "allocationBlockReason"),
            levelScan(CoreJson.objectValue(root, "levelScan")),
            storage(CoreJson.objectValue(root, "storage"))
        ));
    }

    static List<AdminIslandRuntimeView> runtimes(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "islands").stream()
            .map(runtime -> new AdminIslandRuntimeView(
                CoreJson.text(runtime, "islandId"),
                CoreJson.text(runtime, "state"),
                nullableText(runtime, "activeNode"),
                nullableText(runtime, "activeWorld"),
                nullableNumber(runtime, "cellX"),
                nullableNumber(runtime, "cellZ"),
                nullableText(runtime, "leaseOwner"),
                CoreJson.number(runtime, "fencingToken"),
                nullableText(runtime, "activatedAt"),
                nullableText(runtime, "lastHeartbeat"),
                CoreJson.text(runtime, "code")
            ))
            .toList();
    }

    static AdminNodeSummaryView summary(String body) {
        Object parsed = CoreJson.value(body);
        Map<?, ?> root = parsed instanceof Map<?, ?> object ? object : Map.of();
        if (!root.isEmpty()) {
            String code = CoreJson.text(root, "code");
            if (!code.isBlank()) {
                return new AdminNodeSummaryView("code=" + code);
            }
            String nodeId = CoreJson.text(root, "nodeId");
            if (!nodeId.isBlank()) {
                long count = CoreJson.number(root, "count");
                return count > 0L
                    ? new AdminNodeSummaryView("node=" + compactId(nodeId) + " count=" + count)
                    : new AdminNodeSummaryView("node=" + compactId(nodeId));
            }
            List<Map<?, ?>> nodes = CoreJson.objects(root, "nodes");
            if (!nodes.isEmpty()) {
                long nodeCount = CoreJson.number(root, "nodeCount");
                return new AdminNodeSummaryView(
                    "nodes=" + nodes.size(),
                    nodeCount > 0L ? nodeCount : nodes.size(),
                    CoreJson.number(root, "routeCandidateCount"),
                    CoreJson.number(root, "staleNodeCount"),
                    CoreJson.number(root, "heartbeatTimeoutSeconds")
                );
            }
        }
        List<Map<?, ?>> values = parsed instanceof List<?> ? CoreJson.entries(body) : List.of();
        if (!values.isEmpty()) {
            return new AdminNodeSummaryView("nodes=" + values.size(), values.size(), 0L, 0L, 0L);
        }
        if (body == null || body.isBlank()) {
            return new AdminNodeSummaryView("");
        }
        return new AdminNodeSummaryView(clip(body, 180));
    }

    static CoreGuiViews.NodeSummaryView nodeSummary(String nodeId, String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new CoreGuiViews.NodeSummaryView(
            nodeId,
            CoreJson.text(root, "state"),
            CoreJson.text(root, "pool"),
            CoreJson.number(root, "players"),
            CoreJson.number(root, "softPlayerCap"),
            CoreJson.number(root, "hardPlayerCap"),
            CoreJson.number(root, "activeIslands"),
            CoreJson.number(root, "maxActiveIslands"),
            CoreJson.number(root, "activationQueue"),
            CoreJson.number(root, "maxActivationQueue"),
            CoreJson.text(root, "mspt")
        );
    }

    private static String requireNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }

    private static Long nullableNumber(Map<?, ?> object, String key) {
        return !object.containsKey(key) || object.get(key) == null ? null : CoreJson.number(object, key);
    }

    private static String nullableText(Map<?, ?> object, String key) {
        if (!object.containsKey(key) || object.get(key) == null) {
            return null;
        }
        return CoreJson.text(object, key);
    }

    private static int intValue(Map<?, ?> object, String key) {
        long value = CoreJson.number(object, key);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static NodeState enumValue(String value) {
        if (value == null || value.isBlank()) {
            return NodeState.DOWN;
        }
        try {
            return NodeState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NodeState.DOWN;
        }
    }

    private static Map<String, Double> decimalMap(Map<?, ?> object) {
        return object.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> CoreJson.textValue(entry.getKey()),
            entry -> CoreJson.decimalValue(entry.getValue())
        ));
    }

    private static NodeLevelScanSnapshot levelScan(Map<?, ?> object) {
        if (object.isEmpty()) {
            return NodeLevelScanSnapshot.empty();
        }
        return new NodeLevelScanSnapshot(
            CoreJson.bool(object, "running"),
            CoreJson.text(object, "lastIsland"),
            CoreJson.number(object, "startedAt"),
            CoreJson.number(object, "finishedAt"),
            CoreJson.number(object, "failedAt")
        );
    }

    private static NodeStorageSnapshot storage(Map<?, ?> object) {
        if (object.isEmpty()) {
            return NodeStorageSnapshot.empty();
        }
        return new NodeStorageSnapshot(
            CoreJson.bool(object, "primaryDegraded"),
            CoreJson.decimal(object, "uploadSeconds"),
            CoreJson.decimal(object, "downloadSeconds"),
            CoreJson.number(object, "healthCheckFailures"),
            CoreJson.number(object, "uploadFailures"),
            CoreJson.number(object, "downloadFailures"),
            CoreJson.number(object, "operationFailures")
        );
    }

    private static String compactId(String value) {
        if (value == null || value.length() != 36 || !value.contains("-")) {
            return value == null ? "" : value;
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
    }

    private static String clip(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return new StringBuilder(maxLength + 3).append(value, 0, maxLength).append("...").toString();
    }
}
