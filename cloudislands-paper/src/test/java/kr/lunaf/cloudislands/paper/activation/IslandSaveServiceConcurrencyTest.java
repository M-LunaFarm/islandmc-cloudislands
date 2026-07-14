package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.world.export.IslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IslandSaveServiceConcurrencyTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesConcurrentSavesForTheSameIsland() throws Exception {
        UUID islandId = UUID.randomUUID();
        IslandBundleManifest manifest = manifest(islandId);
        IslandStorage storage = storage(manifest);
        CountDownLatch firstExportEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstExport = new CountDownLatch(1);
        AtomicInteger exportCalls = new AtomicInteger();
        AtomicInteger concurrentExports = new AtomicInteger();
        AtomicInteger maxConcurrentExports = new AtomicInteger();
        IslandBundleExporter exporter = (id, active, target) -> {
            int call = exportCalls.incrementAndGet();
            int concurrent = concurrentExports.incrementAndGet();
            maxConcurrentExports.accumulateAndGet(concurrent, Math::max);
            try {
                if (call == 1) {
                    firstExportEntered.countDown();
                    if (!releaseFirstExport.await(5, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("test timed out waiting to release first export");
                    }
                }
                Path bundle = tempDir.resolve("bundle-" + call + ".tar.zst");
                Files.writeString(bundle, "bundle-" + call);
                return new IslandBundleExporter.ExportedIslandBundle(id, bundle, call);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("test interrupted", exception);
            } finally {
                concurrentExports.decrementAndGet();
            }
        };
        IslandSaveService service = new IslandSaveService(storage, exporter, tempDir.resolve("exports"));
        ActiveIslandRegistry.ActiveIsland active = new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 0, 0, 0, 0, 100, 1L, 10L, Instant.now()
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<IslandSaveService.SaveResult> first = executor.submit(() -> service.save(islandId, active));
            firstExportEntered.await(5, TimeUnit.SECONDS);
            Future<IslandSaveService.SaveResult> second = executor.submit(() -> service.save(islandId, active));

            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            assertEquals(1, exportCalls.get());
            releaseFirstExport.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertEquals(2, exportCalls.get());
        assertEquals(1, maxConcurrentExports.get());
    }

    @Test
    void flushesTheLiveWorldBeforeExportAndPublishesTheCurrentBundleSchema() throws Exception {
        UUID islandId = UUID.randomUUID();
        AtomicInteger sequence = new AtomicInteger();
        AtomicReference<IslandBundleManifest> storedManifest = new AtomicReference<>();
        IslandStorage storage = (IslandStorage) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {IslandStorage.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "readManifest" -> manifest(islandId);
                case "writeSnapshot" -> {
                    storedManifest.set((IslandBundleManifest) args[3]);
                    yield new IslandStorage.StoredBundle("checksum", 8L, "snapshot", "SHA-256", "zstd");
                }
                case "pruneSnapshots" -> 0;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandBundleExporter exporter = (id, active, target) -> {
            assertEquals(1, sequence.getAndIncrement(), "world flush must complete before region-file export starts");
            Path bundle = tempDir.resolve("flushed-bundle.tar.zst");
            Files.writeString(bundle, "bundle");
            return new IslandBundleExporter.ExportedIslandBundle(id, bundle, 1L);
        };
        IslandWorldFlush flush = (active, reason) -> {
            assertEquals("AUTO", reason);
            assertEquals(0, sequence.getAndIncrement());
        };
        IslandSaveService service = new IslandSaveService(
            storage, exporter, tempDir.resolve("exports"), SnapshotRetentionPolicy.defaultPolicy(), flush
        );
        ActiveIslandRegistry.ActiveIsland active = new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 0, 0, 0, 0, 100, 1L, 10L, Instant.now()
        );

        service.save(islandId, active);

        assertEquals(2, sequence.get());
        assertEquals(IslandBundleManifest.CURRENT_FORMAT_VERSION, storedManifest.get().formatVersion());
    }

    @Test
    void flushFailurePreventsOfflineRegionExport() {
        UUID islandId = UUID.randomUUID();
        AtomicInteger exportCalls = new AtomicInteger();
        IslandBundleExporter exporter = (id, active, target) -> {
            exportCalls.incrementAndGet();
            throw new IOException("export must not run");
        };
        IslandSaveService service = new IslandSaveService(
            storage(manifest(islandId)), exporter, tempDir.resolve("exports"), SnapshotRetentionPolicy.defaultPolicy(),
            (active, reason) -> { throw new IOException("flush failed"); }
        );
        ActiveIslandRegistry.ActiveIsland active = new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 0, 0, 0, 0, 100, 1L, 10L, Instant.now()
        );

        IOException exception = assertThrows(IOException.class, () -> service.save(islandId, active));

        assertEquals("flush failed", exception.getMessage());
        assertEquals(0, exportCalls.get());
    }

    private IslandStorage storage(IslandBundleManifest manifest) {
        return (IslandStorage) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {IslandStorage.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "readManifest" -> manifest;
                case "writeSnapshot" -> new IslandStorage.StoredBundle("checksum", 8L, "snapshot", "SHA-256", "zstd");
                case "pruneSnapshots" -> 0;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private IslandBundleManifest manifest(UUID islandId) {
        Instant now = Instant.now();
        return new IslandBundleManifest(
            islandId,
            UUID.randomUUID(),
            3,
            "1.21.11",
            1,
            100,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            now,
            now,
            ""
        );
    }
}
