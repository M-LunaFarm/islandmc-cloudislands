package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.AdminNodeActionView;
import kr.lunaf.cloudislands.coreclient.AdminNodeCommandClient;
import kr.lunaf.cloudislands.coreclient.AdminNodeQueryClient;
import kr.lunaf.cloudislands.coreclient.AdminNodeSummaryView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import org.junit.jupiter.api.Test;

class IslandAdminNodeUseCaseTest {
    @Test
    void adminNodeOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandAdminNodeUseCase useCase = useCase(calls);

        assertEquals("nodes=2", useCase.listNodesSummary().join().text());
        assertEquals("READY", useCase.nodeInfoView(" node-a ").join().state());
        assertEquals("node=node-a count=2", useCase.nodeIslandsSummary("node-a", 500).join().text());
        assertEquals("DRAIN", useCase.drainAction("node-a", mutationRunner(calls)).join().operation());
        assertEquals("UNDRAIN", useCase.undrainAction("node-a", mutationRunner(calls)).join().operation());
        assertEquals("SWEEP", useCase.sweepAction("node-a", mutationRunner(calls)).join().operation());
        assertEquals("KICKALL", useCase.kickAllAction("node-a", "admin", idempotentMutationRunner(calls)).join().operation());
        assertEquals("SHUTDOWN_SAFE", useCase.shutdownSafelyAction("node-a", "admin", idempotentMutationRunner(calls)).join().operation());

        assertEquals(List.of(
            "listNodes",
            "nodeInfo:node-a",
            "nodeIslands:node-a:100",
            "audit:admin.node.drain",
            "drainNode:node-a",
            "audit:admin.node.undrain",
            "undrainNode:node-a",
            "audit:admin.node.sweep",
            "sweepNode:node-a",
            "audit:admin.node.kickall",
            "kickAllNode:node-a:admin",
            "audit:admin.node.shutdown-safe",
            "shutdownNodeSafely:node-a:admin"
        ), calls);
    }

    private static IslandAdminNodeUseCase useCase(List<String> calls) {
        CoreApiClient core = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });
        return new IslandAdminNodeUseCase(core, adminNodeQueries(calls), adminNodeCommands(calls));
    }

    private static AdminNodeQueryClient adminNodeQueries(List<String> calls) {
        return new AdminNodeQueryClient() {
            @Override
            public CompletableFuture<List<IslandNodeSnapshot>> nodes() {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<AdminNodeSummaryView> listNodesSummary() {
                calls.add("listNodes");
                return CompletableFuture.completedFuture(new AdminNodeSummaryView("nodes=2"));
            }

            @Override
            public CompletableFuture<Optional<IslandNodeSnapshot>> nodeSnapshot(String nodeId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId) {
                calls.add("nodeInfo:" + nodeId);
                return CompletableFuture.completedFuture(new CoreGuiViews.NodeSummaryView(nodeId, "READY", "island", 3L, 80L, 100L, 4L, 20L, 1L, 10L, "12.5"));
            }

            @Override
            public CompletableFuture<List<AdminIslandRuntimeView>> nodeIslandRuntimes(String nodeId, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit) {
                calls.add("nodeIslands:" + nodeId + ":" + Math.max(1, Math.min(limit, 100)));
                return CompletableFuture.completedFuture(new AdminNodeSummaryView("node=node-a count=2"));
            }
        };
    }

    private static AdminNodeCommandClient adminNodeCommands(List<String> calls) {
        return new AdminNodeCommandClient() {
            @Override
            public CompletableFuture<AdminNodeActionView> drainNode(String nodeId) {
                calls.add("drainNode:" + nodeId);
                return CompletableFuture.completedFuture(nodeAction("DRAIN"));
            }

            @Override
            public CompletableFuture<AdminNodeActionView> undrainNode(String nodeId) {
                calls.add("undrainNode:" + nodeId);
                return CompletableFuture.completedFuture(nodeAction("UNDRAIN"));
            }

            @Override
            public CompletableFuture<AdminNodeActionView> sweepNode(String nodeId) {
                calls.add("sweepNode:" + nodeId);
                return CompletableFuture.completedFuture(nodeAction("SWEEP"));
            }

            @Override
            public CompletableFuture<AdminNodeActionView> kickAllNode(String nodeId, String reason) {
                calls.add("kickAllNode:" + nodeId + ":" + reason);
                return CompletableFuture.completedFuture(nodeAction("KICKALL"));
            }

            @Override
            public CompletableFuture<AdminNodeActionView> shutdownNodeSafely(String nodeId, String reason) {
                calls.add("shutdownNodeSafely:" + nodeId + ":" + reason);
                return CompletableFuture.completedFuture(nodeAction("SHUTDOWN_SAFE"));
            }
        };
    }

    private static AdminNodeActionView nodeAction(String operation) {
        return new AdminNodeActionView(true, "", "node-a", operation);
    }

    private static IslandAdminNodeUseCase.MutationRunner mutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static IslandAdminNodeUseCase.IdempotentMutationRunner idempotentMutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

}
