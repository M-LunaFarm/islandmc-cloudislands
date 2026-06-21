package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record AdminStorageStatusView(List<NodeView> nodes) {
    public AdminStorageStatusView {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public long unavailableCount() {
        return nodes.stream().filter(node -> !node.storageAvailable()).count();
    }

    public record NodeView(
        String nodeId,
        boolean storageAvailable,
        String backend,
        boolean primaryDegraded,
        long saveRetryQueueTotal,
        double uploadSeconds,
        double downloadSeconds,
        long healthCheckFailures,
        long uploadFailures,
        long downloadFailures,
        long operationFailures
    ) {
        public NodeView {
            nodeId = nodeId == null ? "" : nodeId;
            backend = backend == null ? "" : backend;
        }

        public long totalFailures() {
            return healthCheckFailures + uploadFailures + downloadFailures + operationFailures;
        }
    }
}
