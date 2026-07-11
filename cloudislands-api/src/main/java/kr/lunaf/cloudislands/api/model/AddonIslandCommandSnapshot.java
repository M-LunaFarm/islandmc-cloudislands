package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record AddonIslandCommandSnapshot(
    String addonId,
    String primaryAlias,
    List<String> aliases,
    String permission,
    String usage,
    String description
) {
    public AddonIslandCommandSnapshot {
        addonId = safe(addonId);
        primaryAlias = safe(primaryAlias).toLowerCase(java.util.Locale.ROOT);
        aliases = aliases == null ? List.of() : aliases.stream().filter(java.util.Objects::nonNull).map(value -> value.trim().toLowerCase(java.util.Locale.ROOT)).filter(value -> !value.isBlank()).distinct().toList();
        permission = safe(permission);
        usage = oneLine(usage, 256);
        description = oneLine(description, 512);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String oneLine(String value, int limit) {
        String normalized = safe(value).replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
