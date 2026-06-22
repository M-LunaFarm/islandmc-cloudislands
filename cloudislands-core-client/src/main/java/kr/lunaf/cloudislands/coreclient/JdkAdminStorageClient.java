package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        return core.postResultBody("/v1/admin/storage", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminStorageClient::status);
    }

    static AdminStorageStatusView status(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminStorageStatusView.NodeView> nodes = CoreJson.objects(root, "nodes").stream()
            .map(JdkAdminStorageClient::node)
            .filter(node -> !node.nodeId().isBlank())
            .toList();
        return new AdminStorageStatusView(nodes);
    }

    private static AdminStorageStatusView.NodeView node(Map<?, ?> object) {
        Map<?, ?> storage = CoreJson.objectValue(object, "storage");
        return new AdminStorageStatusView.NodeView(
            CoreJson.firstText(object, "nodeId", "id"),
            CoreJson.bool(object, "storageAvailable"),
            CoreJson.text(storage, "backend"),
            CoreJson.bool(storage, "primaryDegraded"),
            CoreJson.number(storage, "saveRetryQueueTotal"),
            CoreJson.decimal(storage, "uploadSeconds"),
            CoreJson.decimal(storage, "downloadSeconds"),
            CoreJson.number(storage, "healthCheckFailures"),
            CoreJson.number(storage, "uploadFailures"),
            CoreJson.number(storage, "downloadFailures"),
            CoreJson.number(storage, "operationFailures")
        );
    }
}
