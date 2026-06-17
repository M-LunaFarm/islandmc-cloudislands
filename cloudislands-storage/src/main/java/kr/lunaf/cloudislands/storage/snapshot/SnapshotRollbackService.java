package kr.lunaf.cloudislands.storage.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.compression.BundleCompressionPolicy;

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
            target.compression(),
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
        BundleCompressionPolicy.ensureSupported(plan.targetCompression(), "snapshot " + plan.targetSnapshotNo());
        verifyChecksum(plan.targetChecksum(), storage.openSnapshotBundle(plan.islandId(), plan.targetSnapshotNo()), "snapshot " + plan.targetSnapshotNo());
        storage.promoteSnapshot(plan.islandId(), plan.targetSnapshotNo());
        return new RollbackResult(plan.islandId(), plan.targetSnapshotNo(), "snapshot", plan.targetChecksum(), plan.targetCompression(), plan.restorePolicy());
    }

    public RollbackResult restoreBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        IslandBundleManifest target = storage.readBundleManifest(storagePath)
            .orElseThrow(() -> new IOException("missing rollback bundle manifest: " + storagePath));
        if (!target.portable()) {
            throw new IOException("rollback bundle is not portable: " + storagePath);
        }
        BundleCompressionPolicy.ensureSupported(target.compression(), storagePath);
        verifyChecksum(target.checksum(), storage.openBundle(storagePath), storagePath);
        storage.promoteBundle(islandId, snapshotNo, storagePath);
        return new RollbackResult(islandId, snapshotNo, "bundle", target.checksum(), target.compression(), target.restorePolicy());
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
        String targetCompression,
        boolean targetPortable,
        SnapshotReason preRestoreReason,
        String restorePolicy
    ) {
        public boolean preRestoreSnapshotRequired() {
            return preRestoreReason == SnapshotReason.BEFORE_RESTORE;
        }

        public String rollbackPolicy() {
            return BundleRestorePolicy.ROLLBACK_POLICY;
        }

        public String operationPolicy() {
            return SnapshotOperationPolicy.ACTIVE_ROLLBACK_POLICY;
        }

        public String playerEvacuationPolicy() {
            return SnapshotOperationPolicy.ACTIVE_ISLAND_PLAYER_POLICY;
        }

        public String rollbackSteps() {
            return SnapshotOperationPolicy.rollbackStepSummary();
        }

        public String checksumPolicy() {
            return SnapshotOperationPolicy.CHECKSUM_POLICY;
        }

        public String portabilityPolicy() {
            return SnapshotOperationPolicy.PORTABILITY_POLICY;
        }
    }

    public record RollbackResult(UUID islandId, long promotedSnapshotNo, String source, String checksum, String compression, String restorePolicy) {}
}
