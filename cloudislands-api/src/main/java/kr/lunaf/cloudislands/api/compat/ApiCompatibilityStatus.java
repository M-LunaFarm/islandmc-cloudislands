package kr.lunaf.cloudislands.api.compat;

public enum ApiCompatibilityStatus {
    COMPATIBLE("compatible"),
    RUNTIME_TOO_OLD("runtime-too-old"),
    MAJOR_VERSION_MISMATCH("major-version-mismatch"),
    INVALID_VERSION("invalid-version");

    private final String code;

    ApiCompatibilityStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
