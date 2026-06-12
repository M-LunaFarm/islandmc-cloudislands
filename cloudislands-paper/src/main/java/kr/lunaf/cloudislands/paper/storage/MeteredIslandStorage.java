package kr.lunaf.cloudislands.paper.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class MeteredIslandStorage implements IslandStorage {
    private final IslandStorage delegate;
    private volatile double lastUploadSeconds;
    private volatile double lastDownloadSeconds;
    private final AtomicLong uploadFailures = new AtomicLong();
    private final AtomicLong downloadFailures = new AtomicLong();
    private final AtomicLong operationFailures = new AtomicLong();

    public MeteredIslandStorage(IslandStorage delegate) {
        this.delegate = delegate;
    }

    public double lastUploadSeconds() {
        return lastUploadSeconds;
    }

    public double lastDownloadSeconds() {
        return lastDownloadSeconds;
    }

    public long uploadFailures() {
        return uploadFailures.get();
    }

    public long downloadFailures() {
        return downloadFailures.get();
    }

    public long operationFailures() {
        return operationFailures.get();
    }

    @Override
    public boolean available() throws IOException {
        return delegate.available();
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.readManifest(islandId);
        } catch (IOException exception) {
            recordDownloadFailure();
            throw exception;
        } finally {
            recordDownload(started);
        }
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.openLatestBundle(islandId);
        } catch (IOException exception) {
            recordDownloadFailure();
            throw exception;
        } finally {
            recordDownload(started);
        }
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.openSnapshotBundle(islandId, snapshotNo);
        } catch (IOException exception) {
            recordDownloadFailure();
            throw exception;
        } finally {
            recordDownload(started);
        }
    }

    @Override
    public InputStream openBundle(String storagePath) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.openBundle(storagePath);
        } catch (IOException exception) {
            recordDownloadFailure();
            throw exception;
        } finally {
            recordDownload(started);
        }
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        long started = System.nanoTime();
        try {
            delegate.writeSnapshot(islandId, snapshotNo, bundle, manifest);
        } catch (IOException exception) {
            recordUploadFailure();
            throw exception;
        } finally {
            recordUpload(started);
        }
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.writeDeleteBackup(islandId, snapshotNo, bundle, manifest);
        } catch (IOException exception) {
            recordUploadFailure();
            throw exception;
        } finally {
            recordUpload(started);
        }
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.writeDeleteBackupFromLatest(islandId, snapshotNo);
        } catch (IOException exception) {
            recordUploadFailure();
            throw exception;
        } finally {
            recordUpload(started);
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        try {
            delegate.promoteSnapshot(islandId, snapshotNo);
        } catch (IOException exception) {
            recordOperationFailure();
            throw exception;
        }
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        long started = System.nanoTime();
        try {
            delegate.promoteBundle(islandId, snapshotNo, storagePath);
        } catch (IOException exception) {
            recordUploadFailure();
            throw exception;
        } finally {
            recordUpload(started);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        try {
            return delegate.pruneSnapshots(islandId, keepLatest);
        } catch (IOException exception) {
            recordOperationFailure();
            throw exception;
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        try {
            return delegate.pruneSnapshots(islandId, policy);
        } catch (IOException exception) {
            recordOperationFailure();
            throw exception;
        }
    }

    @Override
    public void deleteLiveState(UUID islandId) throws IOException {
        try {
            delegate.deleteLiveState(islandId);
        } catch (IOException exception) {
            recordOperationFailure();
            throw exception;
        }
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        try {
            delegate.deleteIsland(islandId);
        } catch (IOException exception) {
            recordOperationFailure();
            throw exception;
        }
    }

    private void recordUpload(long startedNanos) {
        lastUploadSeconds = elapsedSeconds(startedNanos);
    }

    private void recordDownload(long startedNanos) {
        lastDownloadSeconds = elapsedSeconds(startedNanos);
    }

    private void recordUploadFailure() {
        uploadFailures.incrementAndGet();
    }

    private void recordDownloadFailure() {
        downloadFailures.incrementAndGet();
    }

    private void recordOperationFailure() {
        operationFailures.incrementAndGet();
    }

    private double elapsedSeconds(long startedNanos) {
        return Math.max(0.0D, (System.nanoTime() - startedNanos) / 1_000_000_000.0D);
    }
}
