package kr.lunaf.cloudislands.paper.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class FallbackIslandStorage implements IslandStorage {
    private final IslandStorage primary;
    private final IslandStorage fallback;
    private final Logger logger;
    private final AtomicLong primaryFailures = new AtomicLong();
    private final AtomicLong fallbackReads = new AtomicLong();
    private final AtomicLong fallbackWrites = new AtomicLong();
    private final AtomicLong fallbackDeletes = new AtomicLong();
    private final AtomicLong fallbackOperations = new AtomicLong();
    private volatile long lastPrimaryFailureMillis;
    private volatile long lastPrimarySuccessMillis;
    private volatile String lastFallbackReason = "";

    public FallbackIslandStorage(IslandStorage primary, IslandStorage fallback, Logger logger) {
        this.primary = primary;
        this.fallback = fallback;
        this.logger = logger;
    }

    @Override
    public boolean available() throws IOException {
        IOException primaryFailure = null;
        try {
            if (primary.available()) {
                markPrimarySuccess();
                return true;
            }
            markFallback("Primary island storage health check returned unavailable", FallbackUse.OPERATION);
        } catch (IOException exception) {
            primaryFailure = exception;
            markFallback("Primary island storage health check failed", FallbackUse.OPERATION);
            warn("Primary island storage health check failed, checking fallback", exception);
        }
        try {
            return fallback.available();
        } catch (IOException fallbackFailure) {
            if (primaryFailure != null) {
                fallbackFailure.addSuppressed(primaryFailure);
            }
            throw fallbackFailure;
        }
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        try {
            IslandBundleManifest manifest = primary.readManifest(islandId);
            markPrimarySuccess();
            return manifest;
        } catch (IOException exception) {
            markFallback("Primary island manifest read failed for " + islandId, FallbackUse.READ);
            warn("Primary island manifest read failed, using fallback for " + islandId, exception);
            return fallback.readManifest(islandId);
        }
    }

    @Override
    public Optional<IslandBundleManifest> readSnapshotManifest(UUID islandId, long snapshotNo) throws IOException {
        try {
            Optional<IslandBundleManifest> manifest = primary.readSnapshotManifest(islandId, snapshotNo);
            if (manifest.isPresent()) {
                markPrimarySuccess();
                return manifest;
            }
        } catch (IOException exception) {
            markFallback("Primary island snapshot manifest read failed for " + islandId + " #" + snapshotNo, FallbackUse.READ);
            warn("Primary island snapshot manifest read failed, using fallback for " + islandId + " #" + snapshotNo, exception);
        }
        return fallback.readSnapshotManifest(islandId, snapshotNo);
    }

    @Override
    public Optional<IslandBundleManifest> readBundleManifest(String storagePath) throws IOException {
        try {
            Optional<IslandBundleManifest> manifest = primary.readBundleManifest(storagePath);
            if (manifest.isPresent()) {
                markPrimarySuccess();
                return manifest;
            }
        } catch (IOException exception) {
            markFallback("Primary island bundle manifest read failed for " + storagePath, FallbackUse.READ);
            warn("Primary island bundle manifest read failed, using fallback for " + storagePath, exception);
        }
        return fallback.readBundleManifest(storagePath);
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        try {
            InputStream input = primary.openLatestBundle(islandId);
            markPrimarySuccess();
            return input;
        } catch (IOException exception) {
            markFallback("Primary latest island bundle read failed for " + islandId, FallbackUse.READ);
            warn("Primary latest island bundle read failed, using fallback for " + islandId, exception);
            return fallback.openLatestBundle(islandId);
        }
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        try {
            InputStream input = primary.openSnapshotBundle(islandId, snapshotNo);
            markPrimarySuccess();
            return input;
        } catch (IOException exception) {
            markFallback("Primary island snapshot read failed for " + islandId + " #" + snapshotNo, FallbackUse.READ);
            warn("Primary island snapshot read failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            return fallback.openSnapshotBundle(islandId, snapshotNo);
        }
    }

    @Override
    public InputStream openBundle(String storagePath) throws IOException {
        try {
            InputStream input = primary.openBundle(storagePath);
            markPrimarySuccess();
            return input;
        } catch (IOException exception) {
            markFallback("Primary island bundle path read failed for " + storagePath, FallbackUse.READ);
            warn("Primary island bundle path read failed, using fallback for " + storagePath, exception);
            return fallback.openBundle(storagePath);
        }
    }

    @Override
    public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path spool = spoolBundle(bundle);
        try {
            StoredBundle stored;
            try (InputStream input = Files.newInputStream(spool)) {
                stored = primary.writeSnapshot(islandId, snapshotNo, input, manifest);
            }
            markPrimarySuccess();
            mirrorSnapshot(islandId, snapshotNo, spool, manifest);
            return stored;
        } catch (IOException exception) {
            markFallback("Primary island snapshot write failed for " + islandId + " #" + snapshotNo, FallbackUse.WRITE);
            warn("Primary island snapshot write failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            try (InputStream input = Files.newInputStream(spool)) {
                return fallback.writeSnapshot(islandId, snapshotNo, input, manifest);
            }
        } finally {
            Files.deleteIfExists(spool);
        }
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path spool = spoolBundle(bundle);
        try {
            StoredBundle stored;
            try (InputStream input = Files.newInputStream(spool)) {
                stored = primary.writeDeleteBackup(islandId, snapshotNo, input, manifest);
            }
            markPrimarySuccess();
            mirrorDeleteBackup(islandId, snapshotNo, spool, manifest);
            return stored;
        } catch (IOException exception) {
            markFallback("Primary island delete backup write failed for " + islandId + " #" + snapshotNo, FallbackUse.WRITE);
            warn("Primary island delete backup write failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            try (InputStream input = Files.newInputStream(spool)) {
                return fallback.writeDeleteBackup(islandId, snapshotNo, input, manifest);
            }
        } finally {
            Files.deleteIfExists(spool);
        }
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        return writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId).withSnapshotReason(reason);
        try (InputStream input = openLatestBundle(islandId)) {
            return writeDeleteBackup(islandId, snapshotNo, input, manifest);
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        try {
            primary.promoteSnapshot(islandId, snapshotNo);
            markPrimarySuccess();
            mirrorPromoteSnapshot(islandId, snapshotNo);
        } catch (IOException exception) {
            markFallback("Primary island snapshot promote failed for " + islandId + " #" + snapshotNo, FallbackUse.OPERATION);
            warn("Primary island snapshot promote failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            fallback.promoteSnapshot(islandId, snapshotNo);
        }
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        try {
            primary.promoteBundle(islandId, snapshotNo, storagePath);
            markPrimarySuccess();
            mirrorPromoteBundle(islandId, snapshotNo, storagePath);
        } catch (IOException exception) {
            markFallback("Primary island bundle promote failed for " + islandId + " #" + snapshotNo, FallbackUse.WRITE);
            warn("Primary island bundle promote failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            fallback.promoteBundle(islandId, snapshotNo, storagePath);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        try {
            int pruned = primary.pruneSnapshots(islandId, keepLatest);
            markPrimarySuccess();
            mirrorPruneSnapshots(islandId, keepLatest);
            return pruned;
        } catch (IOException exception) {
            markFallback("Primary island snapshot prune failed for " + islandId, FallbackUse.OPERATION);
            warn("Primary island snapshot prune failed, using fallback for " + islandId, exception);
            return fallback.pruneSnapshots(islandId, keepLatest);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        try {
            int pruned = primary.pruneSnapshots(islandId, policy);
            markPrimarySuccess();
            mirrorPruneSnapshots(islandId, policy);
            return pruned;
        } catch (IOException exception) {
            markFallback("Primary island retention prune failed for " + islandId, FallbackUse.OPERATION);
            warn("Primary island retention prune failed, using fallback for " + islandId, exception);
            return fallback.pruneSnapshots(islandId, policy);
        }
    }

    @Override
    public void deleteLiveState(UUID islandId) throws IOException {
        try {
            primary.deleteLiveState(islandId);
            markPrimarySuccess();
            mirrorDeleteLiveState(islandId);
        } catch (IOException exception) {
            markFallback("Primary island live state delete failed for " + islandId, FallbackUse.DELETE);
            warn("Primary island live state delete failed, using fallback for " + islandId, exception);
            fallback.deleteLiveState(islandId);
        }
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        try {
            primary.deleteIsland(islandId);
            markPrimarySuccess();
            mirrorDeleteIsland(islandId);
        } catch (IOException exception) {
            markFallback("Primary island delete failed for " + islandId, FallbackUse.DELETE);
            warn("Primary island delete failed, using fallback for " + islandId, exception);
            fallback.deleteIsland(islandId);
        }
    }

    public boolean primaryDegraded() {
        return lastPrimaryFailureMillis > lastPrimarySuccessMillis;
    }

    public long primaryFailures() {
        return primaryFailures.get();
    }

    public long fallbackReads() {
        return fallbackReads.get();
    }

    public long fallbackWrites() {
        return fallbackWrites.get();
    }

    public long fallbackDeletes() {
        return fallbackDeletes.get();
    }

    public long fallbackOperations() {
        return fallbackOperations.get();
    }

    public String lastFallbackReason() {
        return lastFallbackReason;
    }

    private Path spoolBundle(InputStream bundle) throws IOException {
        Path spool = Files.createTempFile("cloudislands-fallback-bundle-", ".tmp");
        try {
            Files.copy(bundle, spool, StandardCopyOption.REPLACE_EXISTING);
            return spool;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(spool);
            throw exception;
        }
    }

    private void mirrorSnapshot(UUID islandId, long snapshotNo, Path spool, IslandBundleManifest manifest) {
        try {
            try (InputStream input = Files.newInputStream(spool)) {
                fallback.writeSnapshot(islandId, snapshotNo, input, manifest);
            }
        } catch (IOException exception) {
            warn("Fallback island snapshot mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorDeleteBackup(UUID islandId, long snapshotNo, Path spool, IslandBundleManifest manifest) {
        try {
            try (InputStream input = Files.newInputStream(spool)) {
                fallback.writeDeleteBackup(islandId, snapshotNo, input, manifest);
            }
        } catch (IOException exception) {
            warn("Fallback island delete backup mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPromoteSnapshot(UUID islandId, long snapshotNo) {
        try {
            fallback.promoteSnapshot(islandId, snapshotNo);
        } catch (IOException exception) {
            warn("Fallback island snapshot promote mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPromoteBundle(UUID islandId, long snapshotNo, String storagePath) {
        try {
            fallback.promoteBundle(islandId, snapshotNo, storagePath);
        } catch (IOException exception) {
            warn("Fallback island bundle promote mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPruneSnapshots(UUID islandId, int keepLatest) {
        try {
            fallback.pruneSnapshots(islandId, keepLatest);
        } catch (IOException exception) {
            warn("Fallback island snapshot prune mirror failed for " + islandId, exception);
        }
    }

    private void mirrorPruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) {
        try {
            fallback.pruneSnapshots(islandId, policy);
        } catch (IOException exception) {
            warn("Fallback island retention prune mirror failed for " + islandId, exception);
        }
    }

    private void mirrorDeleteLiveState(UUID islandId) {
        try {
            fallback.deleteLiveState(islandId);
        } catch (IOException exception) {
            warn("Fallback island live state delete mirror failed for " + islandId, exception);
        }
    }

    private void mirrorDeleteIsland(UUID islandId) {
        try {
            fallback.deleteIsland(islandId);
        } catch (IOException exception) {
            warn("Fallback island delete mirror failed for " + islandId, exception);
        }
    }

    private void warn(String message, IOException exception) {
        if (logger != null) {
            logger.warning(message + ": " + exception.getMessage());
        }
    }

    private void markPrimarySuccess() {
        lastPrimarySuccessMillis = System.currentTimeMillis();
    }

    private void markFallback(String reason, FallbackUse use) {
        primaryFailures.incrementAndGet();
        lastPrimaryFailureMillis = System.currentTimeMillis();
        lastFallbackReason = reason == null ? "" : reason;
        switch (use) {
            case READ -> fallbackReads.incrementAndGet();
            case WRITE -> fallbackWrites.incrementAndGet();
            case DELETE -> fallbackDeletes.incrementAndGet();
            case OPERATION -> fallbackOperations.incrementAndGet();
        }
    }

    private enum FallbackUse {
        READ,
        WRITE,
        DELETE,
        OPERATION
    }
}
