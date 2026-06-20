package kr.lunaf.cloudislands.storage;

import java.util.Optional;

public interface MinecraftKeyMigration {
    Optional<String> migrateMaterial(String oldKey, int sourceDataVersion);

    Optional<String> migrateBiome(String oldKey, int sourceDataVersion);
}
