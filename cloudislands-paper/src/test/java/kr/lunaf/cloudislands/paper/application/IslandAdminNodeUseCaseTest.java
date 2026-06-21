package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class IslandAdminNodeUseCaseTest {
    @Test
    void adminNodeOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandAdminNodeUseCase useCase = new IslandAdminNodeUseCase(client(calls));

        assertEquals("nodes", useCase.listNodes().join());
        assertEquals("info", useCase.nodeInfo(" node-a ").join());
        assertEquals("islands", useCase.nodeIslands("node-a", 500).join());
        assertEquals("drained", useCase.drain("node-a", mutationRunner(calls)).join());
        assertEquals("undrained", useCase.undrain("node-a", mutationRunner(calls)).join());
        assertEquals("swept", useCase.sweep("node-a", mutationRunner(calls)).join());
        assertEquals("kicked", useCase.kickAll("node-a", "admin", idempotentMutationRunner(calls)).join());
        assertEquals("shutdown", useCase.shutdownSafely("node-a", "admin", idempotentMutationRunner(calls)).join());

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

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "listNodes" -> {
                    calls.add("listNodes");
                    yield CompletableFuture.completedFuture("nodes");
                }
                case "nodeInfo" -> {
                    calls.add("nodeInfo:" + args[0]);
                    yield CompletableFuture.completedFuture("info");
                }
                case "nodeIslands" -> {
                    calls.add("nodeIslands:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("islands");
                }
                case "drainNode" -> {
                    calls.add("drainNode:" + args[0]);
                    yield CompletableFuture.completedFuture("drained");
                }
                case "undrainNode" -> {
                    calls.add("undrainNode:" + args[0]);
                    yield CompletableFuture.completedFuture("undrained");
                }
                case "sweepNode" -> {
                    calls.add("sweepNode:" + args[0]);
                    yield CompletableFuture.completedFuture("swept");
                }
                case "kickAllNode" -> {
                    calls.add("kickAllNode:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("kicked");
                }
                case "shutdownNodeSafely" -> {
                    calls.add("shutdownNodeSafely:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("shutdown");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
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
