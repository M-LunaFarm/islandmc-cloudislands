package kr.lunaf.cloudislands.common.config;

public record ConfigIssue(String code, String path, String message) {
    public ConfigIssue {
        code = normalize(code);
        path = path == null ? "" : path.trim();
        message = message == null ? "" : message.trim();
    }

    public boolean hasCode(String expected) {
        return code.equals(normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
