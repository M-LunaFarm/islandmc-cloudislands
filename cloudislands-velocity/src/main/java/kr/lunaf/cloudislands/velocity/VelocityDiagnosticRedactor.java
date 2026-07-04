package kr.lunaf.cloudislands.velocity;

final class VelocityDiagnosticRedactor {
    private VelocityDiagnosticRedactor() {
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceAll("(?i)(token|secret|password|authorization|accessKey|secretKey)\\\"?\\s*[:=]\\s*\\\"?[^,\\n\\r\\\"]+", "$1=***")
            .replaceAll("ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+", "***");
    }
}
