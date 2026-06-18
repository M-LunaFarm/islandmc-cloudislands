package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandNodePoolScalePolicyTest {
    @Test
    void fiveAndSixIslandNodePoolsAreTreatedAsHealthyWhenAllNodesAreRouteCandidates() throws Exception {
        CoreServiceConfig config = config();

        assertTrue((Boolean) invoke("islandPoolFiveSixNodeHealthy", config, new InMemoryNodeRegistry(5)));
        assertEquals("READY", invoke("islandPoolFiveSixNodeStatus", config, new InMemoryNodeRegistry(5)));
        assertEquals(5L, invoke("islandPoolRouteCandidateRecommendedMinimum", config, new InMemoryNodeRegistry(5)));

        assertTrue((Boolean) invoke("islandPoolFiveSixNodeHealthy", config, new InMemoryNodeRegistry(6)));
        assertEquals("READY", invoke("islandPoolFiveSixNodeStatus", config, new InMemoryNodeRegistry(6)));
        assertEquals(6L, invoke("islandPoolRouteCandidateRecommendedMinimum", config, new InMemoryNodeRegistry(6)));
    }

    @Test
    void poolsAboveSixNodesRemainSupportedButUseSixAsRecommendationFloor() throws Exception {
        CoreServiceConfig config = config();

        assertFalse((Boolean) invoke("islandPoolFiveSixNodeHealthy", config, new InMemoryNodeRegistry(7)));
        assertEquals("READY_ABOVE_6_NODES", invoke("islandPoolFiveSixNodeStatus", config, new InMemoryNodeRegistry(7)));
        assertEquals(6L, invoke("islandPoolRouteCandidateRecommendedMinimum", config, new InMemoryNodeRegistry(7)));
    }

    private static Object invoke(String methodName, CoreServiceConfig config, NodeRegistry nodes) throws Exception {
        Method method = CloudIslandsCoreApplication.class.getDeclaredMethod(methodName, CoreServiceConfig.class, NodeRegistry.class);
        method.setAccessible(true);
        return method.invoke(null, config, nodes);
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
                240,
                Duration.ofSeconds(60)
        );
    }
}
