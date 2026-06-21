package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreSnapshotCommandClient;
import kr.lunaf.cloudislands.coreclient.CoreSnapshotQueryClient;
import org.junit.jupiter.api.Test;

class SnapshotUseCaseTest {
    @Test
    void listSnapshotsBoundsLimitBeforeCallingCore() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        UUID islandId = UUID.randomUUID();

        assertEquals(7L, useCase.snapshotViews(islandId, 999).join().get(0).snapshotNo());
        assertEquals("abcdef1234567890", useCase.snapshotViews(islandId, 999).join().get(0).checksum());
        assertEquals(List.of("list:20", "list:20"), core.calls);
        assertEquals(1, SnapshotUseCase.boundedLimit(-5));
        assertEquals(12, SnapshotUseCase.boundedLimit(12));
    }

    @Test
    void requestSnapshotUsesRequestMutationMetadataAndNormalizesReason() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        List<String> auditActions = new ArrayList<>();

        SnapshotUseCase.SnapshotActionResult result = useCase.requestSnapshotAction(UUID.randomUUID(), "  ", new SnapshotUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                auditActions.add(auditAction);
                return operation.get();
            }
        }).join();

        assertTrue(result.accepted());
        assertEquals("SNAPSHOT_REQUESTED", result.code());
        assertEquals(List.of("island.snapshot.create"), auditActions);
        assertEquals(List.of("request:manual"), core.calls);
    }

    @Test
    void restoreSnapshotUsesIdempotentMutationMetadata() {
        ScriptedCoreSnapshots core = new ScriptedCoreSnapshots();
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(core));
        List<String> auditActions = new ArrayList<>();

        SnapshotUseCase.SnapshotActionResult result = useCase.restoreSnapshotAction(UUID.randomUUID(), 7L, new SnapshotUseCase.IdempotentMutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                auditActions.add(auditAction);
                return operation.get();
            }
        }).join();

        assertTrue(result.accepted());
        assertEquals("RESTORE_REQUESTED", result.code());
        assertEquals(List.of("island.snapshot.restore"), auditActions);
        assertEquals(List.of("restore:7"), core.calls);
    }

    @Test
    void rejectsInvalidSnapshotNumberBeforeMutation() {
        SnapshotUseCase useCase = new SnapshotUseCase(coreApiClient(new ScriptedCoreSnapshots()));

        assertEquals(9L, SnapshotUseCase.positiveSnapshotNo(" 9 "));
        assertEquals(0L, SnapshotUseCase.positiveSnapshotNo("0"));
        assertEquals(0L, SnapshotUseCase.positiveSnapshotNo("bad"));
        assertThrows(IllegalArgumentException.class, () -> useCase.restoreSnapshotAction(
            UUID.randomUUID(),
            0L,
            new SnapshotUseCase.IdempotentMutationRunner() {
                @Override
                public <T> CompletableFuture<T> mutateIdempotent(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                    return operation.get();
                }
            }
        ));
    }

    private CoreApiClient coreApiClient(ScriptedCoreSnapshots core) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
	            new Class<?>[] { CoreApiClient.class },
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "snapshots" -> new CoreSnapshotQueryClient((CoreApiClient) _proxy);
	                case "snapshotCommands" -> new CoreSnapshotCommandClient((CoreApiClient) _proxy);
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
            return CompletableFuture.completedFuture("{\"snapshots\":[{\"snapshotNo\":7,\"reason\":\"manual\",\"sizeBytes\":4096,\"checksum\":\"abcdef1234567890\"}]}");
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
