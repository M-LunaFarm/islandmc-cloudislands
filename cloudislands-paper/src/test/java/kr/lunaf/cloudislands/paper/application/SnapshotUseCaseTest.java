package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class SnapshotUseCaseTest {
    @Test
    void listSnapshotsBoundsLimitBeforeCallingCore() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        UUID islandId = UUID.randomUUID();

        assertEquals("{\"snapshots\":[]}", useCase.listSnapshots(islandId, 999).join());
        assertEquals(List.of("list:20"), core.calls);
        assertEquals(1, SnapshotUseCase.boundedLimit(-5));
        assertEquals(12, SnapshotUseCase.boundedLimit(12));
    }

    @Test
    void requestSnapshotUsesRequestMutationMetadataAndNormalizesReason() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        List<String> auditActions = new ArrayList<>();

        String body = useCase.requestSnapshot(UUID.randomUUID(), "  ", (auditAction, operation) -> {
            auditActions.add(auditAction);
            return operation.get();
        }).join();

        assertEquals("{\"code\":\"SNAPSHOT_REQUESTED\"}", body);
        assertEquals(List.of("island.snapshot.create"), auditActions);
        assertEquals(List.of("request:manual"), core.calls);
    }

    @Test
    void restoreSnapshotUsesIdempotentMutationMetadata() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        List<String> auditActions = new ArrayList<>();

        String body = useCase.restoreSnapshot(UUID.randomUUID(), 7L, (auditAction, operation) -> {
            auditActions.add(auditAction);
            return operation.get();
        }).join();

        assertEquals("{\"code\":\"RESTORE_REQUESTED\"}", body);
        assertEquals(List.of("island.snapshot.restore"), auditActions);
        assertEquals(List.of("restore:7"), core.calls);
    }

    @Test
    void rejectsInvalidSnapshotNumberBeforeMutation() {
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(new ScriptedCoreSnapshots()));

        assertEquals(9L, SnapshotUseCase.positiveSnapshotNo(" 9 "));
        assertEquals(0L, SnapshotUseCase.positiveSnapshotNo("0"));
        assertEquals(0L, SnapshotUseCase.positiveSnapshotNo("bad"));
        assertThrows(IllegalArgumentException.class, () -> useCase.restoreSnapshot(
            UUID.randomUUID(),
            0L,
            (_auditAction, operation) -> operation.get()
        ));
    }

    private CoreApiClient coreApiClient(ScriptedCoreSnapshots core) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listIslandSnapshots" -> core.list((int) args[1]);
                case "requestIslandSnapshotResult" -> core.request((String) args[1]);
                case "restoreIslandSnapshotResult" -> core.restore((long) args[1]);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static final class ScriptedCoreSnapshots {
        final List<String> calls = new ArrayList<>();

        CompletableFuture<String> list(int limit) {
            calls.add("list:" + limit);
            return CompletableFuture.completedFuture("{\"snapshots\":[]}");
        }

        CompletableFuture<String> request(String reason) {
            calls.add("request:" + reason);
            return CompletableFuture.completedFuture("{\"code\":\"SNAPSHOT_REQUESTED\"}");
        }

        CompletableFuture<String> restore(long snapshotNo) {
            calls.add("restore:" + snapshotNo);
            return CompletableFuture.completedFuture("{\"code\":\"RESTORE_REQUESTED\"}");
        }
    }
}
