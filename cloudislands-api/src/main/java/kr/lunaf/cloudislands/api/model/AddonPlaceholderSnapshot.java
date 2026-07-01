package kr.lunaf.cloudislands.api.model;

public record AddonPlaceholderSnapshot(
    String key,
    String description,
    String sampleValue,
    boolean playerScoped,
    boolean enabled
) {
    public AddonPlaceholderSnapshot {
        key = safe(key, "").toLowerCase();
        description = safe(description, "");
        sampleValue = safe(sampleValue, "");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
