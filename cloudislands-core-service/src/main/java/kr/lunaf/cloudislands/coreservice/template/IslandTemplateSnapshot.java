package kr.lunaf.cloudislands.coreservice.template;

import java.time.Instant;
import java.util.List;

public record IslandTemplateSnapshot(
    String id,
    String displayName,
    String description,
    String category,
    boolean enabled,
    String minNodeVersion,
    String requiredPermission,
    String iconMaterial,
    int iconCustomModelData,
    String previewImageKey,
    String bundleStoragePath,
    String bundleChecksum,
    long bundleSizeBytes,
    int schemaVersion,
    int defaultIslandSize,
    double spawnWorldOffsetX,
    double spawnWorldOffsetY,
    double spawnWorldOffsetZ,
    float spawnYaw,
    float spawnPitch,
    String homeName,
    String environmentPreset,
    String biomeKey,
    String borderColor,
    String bankInitialBalance,
    String creationCost,
    int sortOrder,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt
) {
    public IslandTemplateSnapshot(String id, String displayName, boolean enabled, String minNodeVersion) {
        this(
            id,
            displayName,
            "",
            "default",
            enabled,
            minNodeVersion,
            "",
            "GRASS_BLOCK",
            0,
            "",
            "",
            "",
            0L,
            3,
            300,
            0.5D,
            100.0D,
            0.5D,
            180.0F,
            0.0F,
            "default",
            "normal",
            "minecraft:plains",
            "BLUE",
            "0",
            "0",
            0,
            List.of(),
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    public IslandTemplateSnapshot {
        id = normalizeId(id);
        displayName = blankDefault(displayName, id);
        description = text(description);
        category = normalizeId(blankDefault(category, "default"));
        minNodeVersion = text(minNodeVersion);
        requiredPermission = text(requiredPermission);
        iconMaterial = blankDefault(iconMaterial, "GRASS_BLOCK").trim().toUpperCase();
        iconCustomModelData = Math.max(0, iconCustomModelData);
        previewImageKey = text(previewImageKey);
        bundleStoragePath = text(bundleStoragePath);
        bundleChecksum = text(bundleChecksum);
        bundleSizeBytes = Math.max(0L, bundleSizeBytes);
        schemaVersion = Math.max(1, schemaVersion);
        defaultIslandSize = Math.max(1, defaultIslandSize);
        homeName = normalizeId(blankDefault(homeName, "default"));
        environmentPreset = normalizeId(blankDefault(environmentPreset, "normal"));
        biomeKey = blankDefault(biomeKey, "minecraft:plains").trim().toLowerCase();
        borderColor = blankDefault(borderColor, "BLUE").trim().toUpperCase();
        bankInitialBalance = amount(bankInitialBalance);
        creationCost = amount(creationCost);
        sortOrder = Math.max(0, sortOrder);
        tags = tags == null ? List.of() : tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(tag -> tag.trim().toLowerCase())
            .distinct()
            .toList();
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public boolean hasBundle() {
        return !bundleStoragePath.isBlank();
    }

    private static String normalizeId(String value) {
        return blankDefault(value, "default").trim().toLowerCase();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String amount(String value) {
        String normalized = text(value);
        return normalized.isBlank() ? "0" : normalized;
    }
}
