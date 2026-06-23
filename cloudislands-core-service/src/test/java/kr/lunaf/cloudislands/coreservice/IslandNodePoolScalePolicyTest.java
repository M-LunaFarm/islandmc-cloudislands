package kr.lunaf.cloudislands.coreservice;

import static kr.lunaf.cloudislands.coreservice.config.CoreIslandPoolSummary.islandPoolFiveSixNodeHealthy;
import static kr.lunaf.cloudislands.coreservice.config.CoreIslandPoolSummary.islandPoolFiveSixNodeStatus;
import static kr.lunaf.cloudislands.coreservice.config.CoreIslandPoolSummary.islandPoolRouteCandidateRecommendedMinimum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

class IslandNodePoolScalePolicyTest {
    @Test
    void fiveAndSixIslandNodePoolsAreTreatedAsHealthyWhenAllNodesAreRouteCandidates() {
        CoreServiceConfig config = config();

        assertTrue(islandPoolFiveSixNodeHealthy(config, new InMemoryNodeRegistry(5)));
        assertEquals("READY", islandPoolFiveSixNodeStatus(config, new InMemoryNodeRegistry(5)));
        assertEquals(5L, islandPoolRouteCandidateRecommendedMinimum(config, new InMemoryNodeRegistry(5)));

        assertTrue(islandPoolFiveSixNodeHealthy(config, new InMemoryNodeRegistry(6)));
        assertEquals("READY", islandPoolFiveSixNodeStatus(config, new InMemoryNodeRegistry(6)));
        assertEquals(6L, islandPoolRouteCandidateRecommendedMinimum(config, new InMemoryNodeRegistry(6)));
    }

    @Test
    void poolsAboveSixNodesRemainSupportedButUseSixAsRecommendationFloor() {
        CoreServiceConfig config = config();

        assertFalse(islandPoolFiveSixNodeHealthy(config, new InMemoryNodeRegistry(7)));
        assertEquals("READY_ABOVE_6_NODES", islandPoolFiveSixNodeStatus(config, new InMemoryNodeRegistry(7)));
        assertEquals(6L, islandPoolRouteCandidateRecommendedMinimum(config, new InMemoryNodeRegistry(7)));
    }

    private static CoreServiceConfig config() {
        return new CoreServiceConfig(
                "127.0.0.1",
                8443,
                "JDBC",
                "REDIS",
                "REDIS",
                "jdbc:postgresql://postgres.internal:5432/cloudislands",
                "POSTGRESQL",
                "cloudislands",
                "",
                20,
                false,
                true,
                "POSTGRESQL,MYSQL,MARIADB,CORE_API",
                true,
                true,
                "POSTGRESQL,MYSQL,MARIADB,CORE_API",
                "production",
                false,
                false,
                "http://127.0.0.1:8443",
                true,
                true,
                3000,
                URI.create("redis://127.0.0.1:6379"),
                "S3",
                URI.create("http://127.0.0.1:9000"),
                "cloudislands",
                "cloudislands-storage",
                "us-east-1",
                "",
                "",
                "*",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "EXPRESSION",
                "floor(total_level_points / 1000)",
                "SUM_BLOCK_VALUES",
                "island",
                "AVOID_NEW_ACTIVATIONS",
                "DENY_OR_QUEUE",
                "INACTIVE_ONLY_AUTOMATIC",
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                SnapshotRetentionPolicy.defaultPolicy().retainedSnapshotCount(),
                SnapshotRetentionPolicy.defaultPolicy(),
                true,
                true,
                "X-SSL-Client-Verify",
                "SUCCESS",
                "127.0.0.1,localhost,::1",
                240,
                Duration.ofSeconds(60),
                8,
                128,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)
        );
    }
}
