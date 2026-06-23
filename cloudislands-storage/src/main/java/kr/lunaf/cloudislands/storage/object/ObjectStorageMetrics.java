package kr.lunaf.cloudislands.storage.object;

import java.util.concurrent.atomic.AtomicLong;

public final class ObjectStorageMetrics {
    private final AtomicLong uploadedBytes = new AtomicLong();
    private final AtomicLong downloadedBytes = new AtomicLong();
    private final AtomicLong putAttempts = new AtomicLong();
    private final AtomicLong putFailures = new AtomicLong();
    private final AtomicLong multipartUploads = new AtomicLong();
    private final AtomicLong orphanCleanups = new AtomicLong();

    public void recordUpload(long bytes, boolean multipart) {
        uploadedBytes.addAndGet(Math.max(0L, bytes));
        if (multipart) {
            multipartUploads.incrementAndGet();
        }
    }

    public void recordDownload(long bytes) {
        downloadedBytes.addAndGet(Math.max(0L, bytes));
    }

    public void recordPutAttempt() {
        putAttempts.incrementAndGet();
    }

    public void recordPutFailure() {
        putFailures.incrementAndGet();
    }

    public void recordOrphanCleanup() {
        orphanCleanups.incrementAndGet();
    }

    public long uploadedBytes() {
        return uploadedBytes.get();
    }

    public long downloadedBytes() {
        return downloadedBytes.get();
    }

    public long putAttempts() {
        return putAttempts.get();
    }

    public long putFailures() {
        return putFailures.get();
    }

    public long multipartUploads() {
        return multipartUploads.get();
    }

    public long orphanCleanups() {
        return orphanCleanups.get();
    }
}
