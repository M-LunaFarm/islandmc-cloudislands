package kr.lunaf.cloudislands.storage.object;

import java.time.Duration;

public record ObjectStoragePutOptions(
    boolean immutable,
    Duration timeout,
    int maxAttempts,
    long multipartThresholdBytes,
    long multipartPartBytes
) {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long DEFAULT_MULTIPART_THRESHOLD = 8L * 1024L * 1024L;
    private static final long DEFAULT_MULTIPART_PART = 8L * 1024L * 1024L;

    public ObjectStoragePutOptions {
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        multipartThresholdBytes = Math.max(1L, multipartThresholdBytes <= 0L ? DEFAULT_MULTIPART_THRESHOLD : multipartThresholdBytes);
        multipartPartBytes = Math.max(1L, multipartPartBytes <= 0L ? DEFAULT_MULTIPART_PART : multipartPartBytes);
    }

    public static ObjectStoragePutOptions defaults() {
        return new ObjectStoragePutOptions(false, DEFAULT_TIMEOUT, 3, DEFAULT_MULTIPART_THRESHOLD, DEFAULT_MULTIPART_PART);
    }

    public ObjectStoragePutOptions asImmutable() {
        return new ObjectStoragePutOptions(true, timeout, maxAttempts, multipartThresholdBytes, multipartPartBytes);
    }

    public ObjectStoragePutOptions withMultipart(long thresholdBytes, long partBytes) {
        return new ObjectStoragePutOptions(immutable, timeout, maxAttempts, thresholdBytes, partBytes);
    }
}
