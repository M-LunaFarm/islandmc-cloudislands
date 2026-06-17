package kr.lunaf.cloudislands.storage.snapshot;

import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotRollbackServiceTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000404");
    private static final Instant CREATED_AT = Instant.parse("2026-06-17T00:00:00Z");

    @TempDir
    Path root;

    @Test
    void restoreSnapshotVerifiesChecksumAndPromotesTargetSnapshot() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        SnapshotRollbackService rollback = new SnapshotRollbackService(storage);
        IslandStorage.StoredBundle target = storage.writeSnapshot(ISLAND_ID, 1L, input("old-state"), manifest("MANUAL", 1, true));
        IslandStorage.StoredBundle current = storage.writeSnapshot(ISLAND_ID, 2L, input("current-state"), manifest("PERIODIC", 2, true));

        SnapshotRollbackService.RollbackPlan plan = rollback.plan(ISLAND_ID, 1L);
        SnapshotRollbackService.RollbackResult result = rollback.restoreSnapshot(plan);

        assertEquals(current.checksum(), plan.currentChecksum());
        assertEquals(target.checksum(), plan.targetChecksum());
        assertEquals(SnapshotReason.BEFORE_RESTORE, plan.preRestoreReason());
        assertEquals(true, plan.preRestoreSnapshotRequired());
        assertEquals("lock-restoring-lobby-transfer-pre-restore-snapshot-restore-runtime-reset-reactivate", plan.rollbackPolicy());
        assertEquals("snapshot", result.source());
        assertEquals(target.checksum(), result.checksum());
        assertEquals(target.checksum(), storage.readManifest(ISLAND_ID).checksum());
        assertEquals("old-state", new String(storage.openLatestBundle(ISLAND_ID).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void writePreRestoreSnapshotStoresRecoverableBeforeRestorePoint() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        SnapshotRollbackService rollback = new SnapshotRollbackService(storage);

        rollback.writePreRestoreSnapshot(ISLAND_ID, input("before-restore"), manifest("BEFORE_RESTORE", 9, true), 9L);

        IslandBundleManifest stored = storage.readSnapshotManifest(ISLAND_ID, 9L).orElseThrow();
        assertEquals("BEFORE_RESTORE", stored.snapshotReason());
        assertEquals(9, stored.schemaVersion());
        assertEquals("before-restore", new String(storage.openSnapshotBundle(ISLAND_ID, 9L).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void restoreSnapshotRejectsChecksumMismatch() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        SnapshotRollbackService rollback = new SnapshotRollbackService(storage);
        storage.writeSnapshot(ISLAND_ID, 1L, input("target-state"), manifest("MANUAL", 1, true));
        storage.writeSnapshot(ISLAND_ID, 2L, input("current-state"), manifest("PERIODIC", 2, true));
        SnapshotRollbackService.RollbackPlan plan = rollback.plan(ISLAND_ID, 1L);
        Files.writeString(root.resolve("islands").resolve(ISLAND_ID.toString()).resolve("snapshots").resolve("000001").resolve("bundle.tar.zst"), "tampered", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> rollback.restoreSnapshot(plan));

        assertEquals("rollback checksum mismatch for snapshot 1", exception.getMessage());
        assertEquals("current-state", new String(storage.openLatestBundle(ISLAND_ID).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void restoreBundleRejectsNonPortableBundle() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        SnapshotRollbackService rollback = new SnapshotRollbackService(storage);
        storage.writeSnapshot(ISLAND_ID, 1L, input("current-state"), manifest("PERIODIC", 1, true));
        IslandStorage.StoredBundle backup = storage.writeDeleteBackup(ISLAND_ID, 2L, input("node-bound-backup"), manifest("BEFORE_RESTORE", 2, false));

        IOException exception = assertThrows(IOException.class, () -> rollback.restoreBundle(ISLAND_ID, 3L, backup.storagePath()));

        assertEquals("rollback bundle is not portable: " + backup.storagePath(), exception.getMessage());
        assertFalse(storage.readSnapshotManifest(ISLAND_ID, 3L).isPresent());
    }

    private static IslandBundleManifest manifest(String reason, int schemaVersion, boolean portable) {
        return new IslandBundleManifest(
                ISLAND_ID,
                OWNER_ID,
                3,
                "1.21.11",
                schemaVersion,
                300,
                new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
                List.of("default"),
                List.of("visitor"),
                List.of("plains"),
                CREATED_AT,
                CREATED_AT.plusSeconds(schemaVersion),
                "",
                "SHA-256",
                "zstd",
                "",
                0L,
                reason,
                portable,
                "node-agnostic-shard-cell-remap",
                "verify-checksum-then-restore-to-current-active-node"
        );
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
