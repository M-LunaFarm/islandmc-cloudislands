package kr.lunaf.cloudislands.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuperiorSkyblock2MigrationScannerTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000002003");

    @TempDir
    Path root;

    @Test
    void scannerUsesStructuredJsonValuesInsteadOfStringSlicing() throws Exception {
        Path islands = root.resolve("islands");
        Files.createDirectories(islands);
        Files.writeString(islands.resolve(ISLAND_ID + ".json"), """
            {
              "islandId": "%s",
              "ownerUuid": "%s",
              "members": ["%s"],
              "homeName": "main",
              "homeWorld": "sky\\"world",
              "homeX": 12.5,
              "homeY": 91.0,
              "homeZ": -7.25,
              "completedMissions": ["farm_master"],
              "blockValues": {
                "minecraft:diamond_block": {
                  "worth": "1500.50",
                  "levelPoints": 12,
                  "limit": 64
                }
              }
            }
            """.formatted(ISLAND_ID, OWNER_ID, MEMBER_ID), StandardCharsets.UTF_8);

        SuperiorSkyblock2MigrationScanner.ScanResult result = new SuperiorSkyblock2MigrationScanner().scan(root);

        assertTrue(result.issues().isEmpty(), result.issues().toString());
        assertEquals(1, result.manifests().size());
        MigrationManifest manifest = result.manifests().get(0);
        assertEquals(OWNER_ID, manifest.ownerUuid());
        assertEquals(MEMBER_ID, manifest.members().stream().filter(MEMBER_ID::equals).findFirst().orElseThrow());
        assertEquals("sky\"world", manifest.homes().get(0).worldName());
        assertEquals("farm_master", manifest.completedMissions().get(0).missionKey());
        assertEquals("minecraft:diamond_block", manifest.blockValues().get(0).materialKey());
        assertEquals("1500.50", manifest.blockValues().get(0).worth());
        assertEquals(12L, manifest.blockValues().get(0).levelPoints());
        assertEquals(64L, manifest.blockValues().get(0).limit());
    }

    @Test
    void scannerReadsNestedYamlPathsAndBlocks() throws Exception {
        Path islands = root.resolve("islands");
        Files.createDirectories(islands);
        Files.writeString(islands.resolve(ISLAND_ID + ".yml"), """
            islandId: "%s"
            ownerUuid: "%s"
            members:
              - "%s"
            homes:
              spawn:
                world: islands
                x: 1.5
                y: 80
                z: -2.5
                yaw: 90
                pitch: 10
            blockValues:
              "minecraft:emerald_block":
                worth: "900.25"
                levelPoints: 8
                limit: 32
            blockCounts:
              "minecraft:emerald_block": 11
            public: true
            biome: plains
            """.formatted(ISLAND_ID, OWNER_ID, MEMBER_ID), StandardCharsets.UTF_8);

        SuperiorSkyblock2MigrationScanner.ScanResult result = new SuperiorSkyblock2MigrationScanner().scan(root);

        assertTrue(result.issues().isEmpty(), result.issues().toString());
        MigrationManifest manifest = result.manifests().get(0);
        assertEquals("spawn", manifest.homes().get(0).name());
        assertEquals("islands", manifest.homes().get(0).worldName());
        assertEquals(1.5D, manifest.homes().get(0).x());
        assertEquals("minecraft:emerald_block", manifest.blockValues().get(0).materialKey());
        assertEquals("900.25", manifest.blockValues().get(0).worth());
        assertEquals(11L, manifest.blockCounts().get(0).count());
        assertEquals("minecraft:plains", manifest.biomeKey());
        assertTrue(manifest.publicAccess());
    }

    @Test
    void scannerReadsYamlFixtureFromResources() throws Exception {
        SuperiorSkyblock2MigrationScanner.ScanResult result = new SuperiorSkyblock2MigrationScanner().scan(fixturePath("yaml-basic"));

        assertTrue(result.issues().isEmpty(), result.issues().toString());
        MigrationManifest manifest = result.manifests().get(0);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000003001"), manifest.islandId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000003002"), manifest.ownerUuid());
        assertEquals(2, manifest.members().size());
        assertEquals(1, manifest.bannedVisitors().size());
        assertEquals("main", manifest.homes().get(0).name());
        assertEquals("shop", manifest.warps().get(0).name());
        assertEquals("farm_master", manifest.completedMissions().get(0).missionKey());
        assertEquals("CHALLENGE", manifest.completedMissions().get(1).kind());
        assertEquals("minecraft:diamond_block", manifest.blockValues().get(0).materialKey());
        assertEquals(7L, manifest.blockCounts().get(0).count());
        assertEquals("minecraft:plains", manifest.biomeKey());
        assertEquals("250.75", manifest.bankBalance());
        assertEquals(300, manifest.size());
        assertEquals(42L, manifest.level());
        assertEquals("9999.99", manifest.worth());
    }

    @Test
    void scannerReadsJsonFixtureFromResources() throws Exception {
        SuperiorSkyblock2MigrationScanner.ScanResult result = new SuperiorSkyblock2MigrationScanner().scan(fixturePath("json-basic"));

        assertTrue(result.issues().isEmpty(), result.issues().toString());
        MigrationManifest manifest = result.manifests().get(0);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000004001"), manifest.islandId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000004002"), manifest.ownerUuid());
        assertEquals("spawn", manifest.homes().get(0).name());
        assertEquals("miner_one", manifest.completedMissions().get(0).missionKey());
        assertEquals("minecraft:emerald_block", manifest.blockValues().get(0).materialKey());
        assertEquals(11L, manifest.blockCounts().get(0).count());
        assertEquals("minecraft:desert", manifest.biomeKey());
        assertEquals("88.00", manifest.bankBalance());
        assertTrue(manifest.publicAccess());
    }

    @Test
    void scannerReportsBrokenFixtureAsFatalIssue() throws Exception {
        SuperiorSkyblock2MigrationScanner.ScanResult result = new SuperiorSkyblock2MigrationScanner().scan(fixturePath("broken-files"));

        assertEquals(0, result.manifests().size());
        assertEquals("OWNER_NOT_FOUND", result.issues().get(0).code());
        assertTrue(result.issues().get(0).blocking());
    }

    private Path fixturePath(String name) throws Exception {
        return Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/ss2/" + name)).toURI());
    }
}
