package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.SnapshotActionView;
import kr.lunaf.cloudislands.coreclient.SnapshotCommandClient;
import kr.lunaf.cloudislands.coreclient.SnapshotQueryClient;
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
	            new Class<?>[] { CoreApiClient.class, SnapshotQueryClient.class, SnapshotCommandClient.class },
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "snapshots" -> (SnapshotQueryClient) _proxy;
	                case "snapshotCommands" -> (SnapshotCommandClient) _proxy;
	                case "records" -> core.list((int) args[1]);
	                case "listSnapshots" -> core.views((int) args[1]);
	                case "requestSnapshot" -> core.request((String) args[1]);
	                case "restoreSnapshot" -> core.restore((long) args[1]);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static final class ScriptedCoreSnapshots {
        final List<String> calls = new ArrayList<>();

        CompletableFuture<List<IslandSnapshotRecord>> list(int limit) {
            calls.add("list:" + limit);
            return CompletableFuture.completedFuture(List.of(new IslandSnapshotRecord(UUID.randomUUID(), new UUID(0L, 0L), 7L, "", "manual", new UUID(0L, 0L), "abcdef1234567890", 4096L, Instant.EPOCH)));
        }

        CompletableFuture<List<kr.lunaf.cloudislands.coreclient.CoreGuiViews.SnapshotView>> views(int limit) {
            calls.add("list:" + limit);
            return CompletableFuture.completedFuture(List.of(new kr.lunaf.cloudislands.coreclient.CoreGuiViews.SnapshotView(7L, "manual", 4096L, "", "abcdef1234567890", "")));
        }

        CompletableFuture<SnapshotActionView> request(String reason) {
            calls.add("request:" + reason);
            return CompletableFuture.completedFuture(new SnapshotActionView(true, "SNAPSHOT_REQUESTED"));
        }

        CompletableFuture<SnapshotActionView> restore(long snapshotNo) {
            calls.add("restore:" + snapshotNo);
            return CompletableFuture.completedFuture(new SnapshotActionView(true, "RESTORE_REQUESTED"));
        }
    }
}
