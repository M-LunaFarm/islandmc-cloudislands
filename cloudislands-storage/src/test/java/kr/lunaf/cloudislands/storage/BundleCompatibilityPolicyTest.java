package kr.lunaf.cloudislands.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import org.junit.jupiter.api.Test;

class BundleCompatibilityPolicyTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000222");
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

    @Test
    void currentBundleUsesCurrentReaderAdapter() {
        BundleCompatibilityPolicy.CompatibilityResult result = BundleCompatibilityPolicy.evaluate(manifest(
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION
        ));

        assertTrue(result.compatible());
        assertFalse(result.migrationRequired());
        assertEquals(BundleCompatibilityPolicy.TARGET_ADAPTER_ID, result.migrationAdapterId());
        assertEquals("compatible-current", result.summary());
        assertEquals(IslandBundleManifest.CURRENT_FORMAT_VERSION, result.sourceBundleSchemaVersion());
        assertEquals(IslandBundleManifest.CURRENT_FORMAT_VERSION, result.targetBundleSchemaVersion());
        assertEquals(IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION, result.sourceWorldDataVersion());
        assertEquals(IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION, result.targetWorldDataVersion());
    }

    @Test
    void olderWorldDataVersionRequiresExplicitUpgradeAdapter() {
        BundleCompatibilityPolicy.CompatibilityResult result = BundleCompatibilityPolicy.evaluate(manifest(
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION - 1
        ));

        assertTrue(result.compatible());
        assertTrue(result.migrationRequired());
        assertEquals(BundleCompatibilityPolicy.UPGRADE_ADAPTER_ID, result.migrationAdapterId());
        assertEquals("compatible-upgrade:" + BundleCompatibilityPolicy.UPGRADE_ADAPTER_ID, result.summary());
    }

    @Test
    void missingLegacyWorldDataVersionRequiresLegacyAdapter() {
        BundleCompatibilityPolicy.CompatibilityResult result = BundleCompatibilityPolicy.evaluate(manifest(
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            0
        ));

        assertTrue(result.compatible());
        assertTrue(result.migrationRequired());
        assertEquals(BundleCompatibilityPolicy.LEGACY_ADAPTER_ID, result.migrationAdapterId());
    }

    @Test
    void futureWorldDataVersionBlocksUnsafeDowngrade() {
        BundleCompatibilityPolicy.CompatibilityResult result = BundleCompatibilityPolicy.evaluate(manifest(
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION + 1
        ));

        assertFalse(result.compatible());
        assertFalse(result.migrationRequired());
        assertEquals(List.of("minecraftDataVersion"), result.missingRequirements());
        assertEquals("incompatible-minecraftDataVersion", result.summary());
    }

    @Test
    void futureBundleSchemaVersionBlocksUnknownReader() {
        BundleCompatibilityPolicy.CompatibilityResult result = BundleCompatibilityPolicy.evaluate(manifest(
            IslandBundleManifest.CURRENT_FORMAT_VERSION + 1,
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION
        ));

        assertFalse(result.compatible());
        assertFalse(result.migrationRequired());
        assertEquals(List.of("formatVersion"), result.missingRequirements());
        assertEquals("incompatible-formatVersion", result.summary());
    }

    private static IslandBundleManifest manifest(int formatVersion, int minecraftDataVersion) {
        return new IslandBundleManifest(
            ISLAND_ID,
            OWNER_ID,
            formatVersion,
            "1.21.11",
            12,
            300,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            List.of("default"),
            List.of("shop"),
            List.of("minecraft:plains"),
            NOW,
            NOW,
            "checksum",
            BundleRestorePolicy.CHECKSUM_ALGORITHM,
            BundleRestorePolicy.COMPRESSION,
            "islands/" + ISLAND_ID + "/snapshots/000001/bundle.tar.zst",
            42L,
            "CREATED",
            true,
            BundleRestorePolicy.PLACEMENT_POLICY,
            BundleRestorePolicy.RESTORE_POLICY,
            "1.0.1",
            minecraftDataVersion,
            "1.21.11",
            "skyblock-default@4"
        );
    }
}
