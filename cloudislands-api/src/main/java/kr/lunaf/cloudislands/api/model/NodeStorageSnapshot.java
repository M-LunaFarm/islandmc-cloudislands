package kr.lunaf.cloudislands.api.model;

public record NodeStorageSnapshot(double uploadSeconds, double downloadSeconds, long healthCheckFailures, long uploadFailures, long downloadFailures, long operationFailures) {
    public static NodeStorageSnapshot empty() {
        return new NodeStorageSnapshot(0.0D, 0.0D, 0L, 0L, 0L, 0L);
    }
}
