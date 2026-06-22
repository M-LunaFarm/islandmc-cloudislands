package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkAdminStorageClient implements AdminStorageQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminStorageClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminStorageStatusView> status() {
        return core.postWithResultBody("/v1/admin/storage", "{}")
            .thenApply(JdkAdminStorageClient::status);
    }

    static AdminStorageStatusView status(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminStorageStatusView.NodeView> nodes = SimpleJson.list(root.get("nodes")).stream()
            .map(SimpleJson::object)
            .map(JdkAdminStorageClient::node)
            .filter(node -> !node.nodeId().isBlank())
            .toList();
        return new AdminStorageStatusView(nodes);
    }

    private static AdminStorageStatusView.NodeView node(Map<?, ?> object) {
        Map<?, ?> storage = SimpleJson.object(object.get("storage"));
        return new AdminStorageStatusView.NodeView(
            firstText(object, "nodeId", "id"),
            bool(object, "storageAvailable"),
            text(storage, "backend"),
            bool(storage, "primaryDegraded"),
            number(storage, "saveRetryQueueTotal"),
            decimal(storage, "uploadSeconds"),
            decimal(storage, "downloadSeconds"),
            number(storage, "healthCheckFailures"),
            number(storage, "uploadFailures"),
            number(storage, "downloadFailures"),
            number(storage, "operationFailures")
        );
    }

    private static String firstText(Map<?, ?> object, String firstKey, String secondKey) {
        String first = text(object, firstKey);
        return first.isBlank() ? text(object, secondKey) : first;
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
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
}
