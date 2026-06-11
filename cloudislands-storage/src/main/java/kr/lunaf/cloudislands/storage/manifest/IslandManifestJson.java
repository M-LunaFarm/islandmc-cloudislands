package kr.lunaf.cloudislands.storage.manifest;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;

public final class IslandManifestJson {
    private IslandManifestJson() {}

    public static String write(IslandBundleManifest manifest) {
        IslandLocation spawn = manifest.spawn();
        return "{"
            + "\"islandId\":\"" + manifest.islandId() + "\","
            + "\"ownerUuid\":\"" + manifest.ownerUuid() + "\","
            + "\"formatVersion\":" + manifest.formatVersion() + ","
            + "\"minecraftVersion\":\"" + manifest.minecraftVersion() + "\","
            + "\"schemaVersion\":" + manifest.schemaVersion() + ","
            + "\"size\":" + manifest.size() + ","
            + "\"spawn\":{"
            + "\"x\":" + spawn.localX() + ",\"y\":" + spawn.localY() + ",\"z\":" + spawn.localZ() + ","
            + "\"yaw\":" + spawn.yaw() + ",\"pitch\":" + spawn.pitch()
            + "},"
            + "\"createdAt\":\"" + manifest.createdAt() + "\","
            + "\"savedAt\":\"" + manifest.savedAt() + "\","
            + "\"checksum\":\"" + manifest.checksum() + "\""
            + "}";
    }

    public static IslandBundleManifest minimal(UUID islandId, UUID ownerUuid, String checksum) {
        Instant now = Instant.now();
        return new IslandBundleManifest(islandId, ownerUuid, 3, "1.21.11", 12, 300, new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F), now, now, checksum);
    }
}
