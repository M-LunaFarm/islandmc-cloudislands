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

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "listNodes" -> {
                    calls.add("listNodes");
                    yield CompletableFuture.completedFuture("[\"node-a\",\"node-b\"]");
                }
                case "nodeInfo" -> {
                    calls.add("nodeInfo:" + args[0]);
                    yield CompletableFuture.completedFuture("{\"state\":\"READY\",\"pool\":\"island\",\"players\":3,\"softPlayerCap\":80,\"hardPlayerCap\":100,\"activeIslands\":4,\"maxActiveIslands\":20,\"activationQueue\":1,\"maxActivationQueue\":10,\"mspt\":\"12.5\"}");
                }
                case "nodeIslands" -> {
                    calls.add("nodeIslands:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture(nodeIslandsJson());
                }
                case "drainNode" -> {
                    calls.add("drainNode:" + args[0]);
                    yield CompletableFuture.completedFuture(nodeLifecycleJson("DRAIN"));
                }
                case "undrainNode" -> {
                    calls.add("undrainNode:" + args[0]);
                    yield CompletableFuture.completedFuture(nodeLifecycleJson("UNDRAIN"));
                }
                case "sweepNode" -> {
                    calls.add("sweepNode:" + args[0]);
                    yield CompletableFuture.completedFuture(nodeLifecycleJson("SWEEP"));
                }
                case "kickAllNode" -> {
                    calls.add("kickAllNode:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture(nodeLifecycleJson("KICKALL"));
                }
                case "shutdownNodeSafely" -> {
                    calls.add("shutdownNodeSafely:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture(nodeLifecycleJson("SHUTDOWN_SAFE"));
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

    private static String nodeIslandsJson() {
        return "{\"nodeId\":\"node-a\",\"count\":2,\"islands\":[]}";
    }

    private static String nodeLifecycleJson(String operation) {
        return "{\"accepted\":true,\"nodeId\":\"node-a\",\"state\":\"DRAINING\",\"operation\":\"" + operation + "\"}";
    }
}
