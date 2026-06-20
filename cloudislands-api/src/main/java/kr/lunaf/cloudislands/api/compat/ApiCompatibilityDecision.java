package kr.lunaf.cloudislands.api.compat;

public record ApiCompatibilityDecision(
    ApiCompatibilityStatus status,
    String requestedApiVersion,
    String runtimeApiVersion,
    String reason
) {
    public boolean compatible() {
        return status == ApiCompatibilityStatus.COMPATIBLE;
    }
}
