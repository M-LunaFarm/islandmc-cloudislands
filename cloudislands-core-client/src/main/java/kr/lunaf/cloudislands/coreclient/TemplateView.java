package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record TemplateView(
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
    List<String> tags
) {
    public TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
        this(id, displayName, "", "default", enabled, minNodeVersion, "", "GRASS_BLOCK", 0, "", "", "", 0L, 3, 300, 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, "default", "normal", "minecraft:plains", "BLUE", "0", "0", 0, List.of());
    }

    public TemplateView {
        id = id == null ? "" : id;
        displayName = displayName == null ? "" : displayName;
        description = description == null ? "" : description;
        category = category == null || category.isBlank() ? "default" : category;
        minNodeVersion = minNodeVersion == null ? "" : minNodeVersion;
        requiredPermission = requiredPermission == null ? "" : requiredPermission;
        iconMaterial = iconMaterial == null || iconMaterial.isBlank() ? "GRASS_BLOCK" : iconMaterial;
        previewImageKey = previewImageKey == null ? "" : previewImageKey;
        bundleStoragePath = bundleStoragePath == null ? "" : bundleStoragePath;
        bundleChecksum = bundleChecksum == null ? "" : bundleChecksum;
        schemaVersion = Math.max(1, schemaVersion);
        defaultIslandSize = Math.max(1, defaultIslandSize);
        homeName = homeName == null || homeName.isBlank() ? "default" : homeName;
        environmentPreset = environmentPreset == null || environmentPreset.isBlank() ? "normal" : environmentPreset;
        biomeKey = biomeKey == null || biomeKey.isBlank() ? "minecraft:plains" : biomeKey;
        borderColor = borderColor == null || borderColor.isBlank() ? "BLUE" : borderColor;
        bankInitialBalance = bankInitialBalance == null || bankInitialBalance.isBlank() ? "0" : bankInitialBalance;
        creationCost = creationCost == null || creationCost.isBlank() ? "0" : creationCost;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
