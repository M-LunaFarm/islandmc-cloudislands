package kr.lunaf.cloudislands.api.compat;

public final class ApiCompatibilityPolicy {
    private ApiCompatibilityPolicy() {
    }

    public static ApiCompatibilityDecision evaluate(String requestedApiVersion, String runtimeApiVersion) {
        ApiVersion requested;
        ApiVersion runtime;
        try {
            requested = ApiVersion.parse(requestedApiVersion);
            runtime = ApiVersion.parse(runtimeApiVersion);
        } catch (IllegalArgumentException ex) {
            return new ApiCompatibilityDecision(ApiCompatibilityStatus.INVALID_VERSION, requestedApiVersion, runtimeApiVersion, ex.getMessage());
        }
        if (!requested.sameMajor(runtime)) {
            return new ApiCompatibilityDecision(ApiCompatibilityStatus.MAJOR_VERSION_MISMATCH, requested.toString(), runtime.toString(), "major versions differ");
        }
        if (!runtime.atLeast(requested)) {
            return new ApiCompatibilityDecision(ApiCompatibilityStatus.RUNTIME_TOO_OLD, requested.toString(), runtime.toString(), "runtime api is older than requested api");
        }
        return new ApiCompatibilityDecision(ApiCompatibilityStatus.COMPATIBLE, requested.toString(), runtime.toString(), "compatible");
    }

    public static DeprecationDecision deprecationRemoval(String deprecatedInVersion, String removalVersion) {
        ApiVersion deprecatedIn;
        ApiVersion removal;
        try {
            deprecatedIn = ApiVersion.parse(deprecatedInVersion);
            removal = ApiVersion.parse(removalVersion);
        } catch (IllegalArgumentException ex) {
            return new DeprecationDecision(false, deprecatedInVersion, removalVersion, "invalid-version");
        }
        if (removal.major() > deprecatedIn.major()) {
            return new DeprecationDecision(true, deprecatedIn.toString(), removal.toString(), "major-release-removal-window");
        }
        if (removal.major() == deprecatedIn.major() && removal.minor() > deprecatedIn.minor()) {
            return new DeprecationDecision(true, deprecatedIn.toString(), removal.toString(), "minor-release-removal-window");
        }
        return new DeprecationDecision(false, deprecatedIn.toString(), removal.toString(), "deprecated-api-must-survive-one-full-minor-release");
    }
}
