package kr.lunaf.cloudislands.storage.object;

public record ObjectStoragePutResult(
    String key,
    String checksum,
    long sizeBytes,
    String checksumAlgorithm,
    boolean multipart
) {
}
