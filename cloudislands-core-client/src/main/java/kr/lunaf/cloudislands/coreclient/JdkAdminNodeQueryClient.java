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
import kr.lunaf.cloudislands.common.json.SimpleJson;

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
        return SimpleJson.list(root.get("nodes")).stream()
            .map(SimpleJson::object)
            .map(node -> node(text(firstPresent(node, "nodeId", "id")), node))
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
        String nodeId = text(firstPresent(root, "nodeId", "id"));
        if (nodeId.isBlank()) {
            nodeId = fallbackNodeId == null ? "" : fallbackNodeId;
        }
        if (nodeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IslandNodeSnapshot(
            nodeId,
            text(root, "pool"),
            text(firstPresent(root, "serverName", "server")),
            text(root, "nodeVersion"),
            enumValue(text(root, "state")),
            intValue(root, "players"),
            intValue(root, "softPlayerCap"),
            intValue(root, "hardPlayerCap"),
            intValue(root, "reservedSlots"),
            intValue(root, "activeIslands"),
            intValue(root, "maxActiveIslands"),
            decimal(root, "mspt"),
            intValue(root, "activationQueue"),
            intValue(root, "maxActivationQueue"),
            decimal(root, "chunkLoadPressure"),
            number(root, "heapUsedMb"),
            number(root, "heapMaxMb"),
            intValue(root, "recentFailurePenalty"),
            bool(root, "storageAvailable"),
            text(root, "supportedTemplates"),
            instant(text(root, "lastHeartbeat")),
            decimal(root, "score"),
            decimalMap(SimpleJson.object(root.get("scoreBreakdown"))),
            bool(root, "eligibleForNewActivation"),
            text(root, "allocationBlockReason"),
            levelScan(SimpleJson.object(root.get("levelScan"))),
            storage(SimpleJson.object(root.get("storage")))
        ));
    }

    static List<AdminIslandRuntimeView> runtimes(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return SimpleJson.list(root.get("islands")).stream()
            .map(SimpleJson::object)
            .map(runtime -> new AdminIslandRuntimeView(
                text(runtime, "islandId"),
                text(runtime, "state"),
                nullableText(runtime, "activeNode"),
                nullableText(runtime, "activeWorld"),
                nullableNumber(runtime.get("cellX")),
                nullableNumber(runtime.get("cellZ")),
                nullableText(runtime, "leaseOwner"),
                number(runtime, "fencingToken"),
                nullableText(runtime, "activatedAt"),
                nullableText(runtime, "lastHeartbeat"),
                text(runtime, "code")
            ))
            .toList();
    }

    static AdminNodeSummaryView summary(String body) {
        Object parsed = CoreJson.value(body);
        Map<?, ?> root = SimpleJson.object(parsed);
        if (!root.isEmpty()) {
            String code = text(root, "code");
            if (!code.isBlank()) {
                return new AdminNodeSummaryView("code=" + code);
            }
            String nodeId = text(root, "nodeId");
            if (!nodeId.isBlank()) {
                long count = SimpleJson.number(root.get("count"));
                return count > 0L
                    ? new AdminNodeSummaryView("node=" + compactId(nodeId) + " count=" + count)
                    : new AdminNodeSummaryView("node=" + compactId(nodeId));
            }
            List<?> nodes = SimpleJson.list(root.get("nodes"));
            if (!nodes.isEmpty()) {
                long nodeCount = number(root, "nodeCount");
                return new AdminNodeSummaryView(
                    "nodes=" + nodes.size(),
                    nodeCount > 0L ? nodeCount : nodes.size(),
                    number(root, "routeCandidateCount"),
                    number(root, "staleNodeCount"),
                    number(root, "heartbeatTimeoutSeconds")
                );
            }
        }
        List<?> values = SimpleJson.list(parsed);
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
            text(root, "state"),
            text(root, "pool"),
            number(root, "players"),
            number(root, "softPlayerCap"),
            number(root, "hardPlayerCap"),
            number(root, "activeIslands"),
            number(root, "maxActiveIslands"),
            number(root, "activationQueue"),
            number(root, "maxActivationQueue"),
            text(root, "mspt")
        );
    }

    private static String requireNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static String text(Object value) {
        return SimpleJson.text(value);
    }

    private static Object firstPresent(Map<?, ?> object, String first, String second) {
        return object.containsKey(first) ? object.get(first) : object.get(second);
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : SimpleJson.number(value);
    }

    private static String nullableText(Map<?, ?> object, String key) {
        if (!object.containsKey(key) || object.get(key) == null) {
            return null;
        }
        return SimpleJson.text(object.get(key));
    }

    private static int intValue(Map<?, ?> object, String key) {
        long value = number(object, key);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static double decimal(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(SimpleJson.text(value));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
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
            entry -> SimpleJson.text(entry.getKey()),
            entry -> {
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                try {
                    return Double.parseDouble(SimpleJson.text(value));
                } catch (NumberFormatException ignored) {
                    return 0.0D;
                }
            }
        ));
    }

    private static NodeLevelScanSnapshot levelScan(Map<?, ?> object) {
        if (object.isEmpty()) {
            return NodeLevelScanSnapshot.empty();
        }
        return new NodeLevelScanSnapshot(
            bool(object, "running"),
            text(object, "lastIsland"),
            number(object, "startedAt"),
            number(object, "finishedAt"),
            number(object, "failedAt")
        );
    }

    private static NodeStorageSnapshot storage(Map<?, ?> object) {
        if (object.isEmpty()) {
            return NodeStorageSnapshot.empty();
        }
        return new NodeStorageSnapshot(
            bool(object, "primaryDegraded"),
            decimal(object, "uploadSeconds"),
            decimal(object, "downloadSeconds"),
            number(object, "healthCheckFailures"),
            number(object, "uploadFailures"),
            number(object, "downloadFailures"),
            number(object, "operationFailures")
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
