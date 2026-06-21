package kr.lunaf.cloudislands.coreclient;

public record MutationResult<T>(
    T value,
    String version,
    boolean changed
) {
    public MutationResult {
        version = version == null ? "" : version.trim();
    }
}
