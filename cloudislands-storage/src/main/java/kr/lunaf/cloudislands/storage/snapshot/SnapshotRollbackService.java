package kr.lunaf.cloudislands.storage.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;

public final class SnapshotRollbackService {
    private final IslandStorage storage;

    public SnapshotRollbackService(IslandStorage storage) {
        this.storage = storage;
    }

    public RollbackPlan plan(UUID islandId, long snapshotNo) throws IOException {
        IslandBundleManifest current = storage.readManifest(islandId);
        IslandBundleManifest target = storage.readSnapshotManifest(islandId, snapshotNo)
            .orElseThrow(() -> new IOException("missing rollback target snapshot manifest: " + islandId + " #" + snapshotNo));
        return new RollbackPlan(
            islandId,
            snapshotNo,
            current.schemaVersion(),
            target.schemaVersion(),
            current.checksum(),
            target.checksum(),
            target.portable(),
            SnapshotReason.BEFORE_RESTORE,
            target.restorePolicy()
        );
    }

    public void writePreRestoreSnapshot(UUID islandId, InputStream bundle, IslandBundleManifest manifest, long snapshotNo) throws IOException {
        storage.writeSnapshot(islandId, snapshotNo, bundle, manifest);
    }

    public RollbackResult restoreSnapshot(RollbackPlan plan) throws IOException {
        if (plan == null) {
            throw new IllegalArgumentException("rollback plan is required");
        }
        if (!plan.targetPortable()) {
            throw new IOException("rollback target is not portable: " + plan.islandId() + " #" + plan.targetSnapshotNo());
        }
        verifyChecksum(plan.targetChecksum(), storage.openSnapshotBundle(plan.islandId(), plan.targetSnapshotNo()), "snapshot " + plan.targetSnapshotNo());
        storage.promoteSnapshot(plan.islandId(), plan.targetSnapshotNo());
        return new RollbackResult(plan.islandId(), plan.targetSnapshotNo(), "snapshot", plan.targetChecksum(), plan.restorePolicy());
    }

    public RollbackResult restoreBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        IslandBundleManifest target = storage.readBundleManifest(storagePath)
            .orElseThrow(() -> new IOException("missing rollback bundle manifest: " + storagePath));
        if (!target.portable()) {
            throw new IOException("rollback bundle is not portable: " + storagePath);
        }
        verifyChecksum(target.checksum(), storage.openBundle(storagePath), storagePath);
        storage.promoteBundle(islandId, snapshotNo, storagePath);
        return new RollbackResult(islandId, snapshotNo, "bundle", target.checksum(), target.restorePolicy());
    }

    private void verifyChecksum(String expectedChecksum, InputStream bundle, String source) throws IOException {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            throw new IOException("missing rollback checksum: " + source);
        }
        try (InputStream input = bundle) {
            String actualChecksum = Sha256Checksums.of(input);
            if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
                throw new IOException("rollback checksum mismatch for " + source);
            }
        }
    }

    public record RollbackPlan(
        UUID islandId,
        long targetSnapshotNo,
        int currentSchemaVersion,
        int targetSchemaVersion,
        String currentChecksum,
        String targetChecksum,
        boolean targetPortable,
        SnapshotReason preRestoreReason,
        String restorePolicy
    ) {}

    public record RollbackResult(UUID islandId, long promotedSnapshotNo, String source, String checksum, String restorePolicy) {}
}
