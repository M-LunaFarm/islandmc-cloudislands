package kr.lunaf.cloudislands.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import kr.lunaf.cloudislands.common.config.ConfigSource;
import kr.lunaf.cloudislands.paper.AgentRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperAccessModeTest {
    @Test
    void basicSinglePaperModeDisablesProxyRequirements() {
        PaperRuntimeConfig config = load("SINGLE_PAPER");

        assertEquals(AgentRole.ISLAND_NODE, config.node().role());
        assertTrue(config.routing().directLocalTeleport());
        assertFalse(config.security().requireVelocityForwarding());
        assertFalse(config.security().enforceRouteSession());
        assertFalse(config.health().enabled());
        assertEquals("LOCAL_FILESYSTEM", config.storage().primary().backend());
    }

    @Test
    void basicLobbyModeKeepsApiAndCommandsWithoutIslandWorldExecutionRole() {
        PaperRuntimeConfig config = load("LOBBY");

        assertEquals(AgentRole.LOBBY, config.node().role());
        assertFalse(config.routing().directLocalTeleport());
        assertTrue(config.security().requireVelocityForwarding());
        assertTrue(config.redis().uri().isBlank());
        assertEquals("LOCAL_FILESYSTEM", config.storage().primary().backend());
    }

    @Test
    void basicRedisUrlOptsIntoLocalRedisWithoutS3Credentials() {
        PaperRuntimeConfig config = PaperRuntimeConfigLoader.loadV2(List.of(new ConfigSource(
            "paper/config-v2/config.yml",
            10,
            "configuration-mode: BASIC\nbasic:\n  topology: LOBBY\n  redis-url: redis://127.0.0.1:6379\n"
        )), value -> value == null ? "" : value);

        assertFalse(config.redis().uri().isBlank());
        assertEquals("redis://127.0.0.1:6379", config.redis().uri());
        assertEquals("LOCAL_FILESYSTEM", config.storage().primary().backend());
        assertTrue(config.storage().primary().accessKey().isBlank());
    }

    @Test
    void basicAutoModeRecognizesIslandsFolderAndSuppliesLocalProxyDefaults(@TempDir Path tempDir) {
        Path dataFolder = tempDir.resolve("islands-1/plugins/CloudIslands");
        PaperRuntimeConfig config = PaperRuntimeConfigLoader.loadV2(List.of(new ConfigSource(
            "paper/config-v2/config.yml",
            10,
            "configuration-mode: BASIC\nbasic:\n  topology: AUTO\n  node-id: island-1\n  velocity-server-name: island-1\n"
        )), value -> value == null ? "" : value, dataFolder);

        assertEquals(AgentRole.ISLAND_NODE, config.node().role());
        assertEquals("islands-1", config.node().id());
        assertEquals("islands-1", config.node().velocityServerName());
        assertFalse(config.routing().directLocalTeleport());
        assertTrue(config.security().requireVelocityForwarding());
        assertEquals(List.of("127.0.0.1", "::1"), config.security().proxySourceAllowlist());
        assertFalse(config.health().enabled());
    }

    private static PaperRuntimeConfig load(String topology) {
        return PaperRuntimeConfigLoader.loadV2(List.of(new ConfigSource(
            "paper/config-v2/config.yml",
            10,
            "configuration-mode: BASIC\nbasic:\n  topology: " + topology + "\n  core-url: http://127.0.0.1:8443\n"
        )), value -> value == null ? "" : value);
    }
}
