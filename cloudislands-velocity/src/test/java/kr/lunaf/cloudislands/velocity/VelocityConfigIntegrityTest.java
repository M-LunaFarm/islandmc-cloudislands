package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VelocityConfigIntegrityTest {
    @Test
    void defaultConfigV2KeepsPlayerNodeNamesHidden() throws Exception {
        Path root = projectRoot();
        String routing = Files.readString(root.resolve("src/main/resources/config-v2/routing.yml"));

        assertTrue(routing.contains("hide-backend-node-names: true"));
    }

    @Test
    void defaultConfigV2KeepsGoalVelocitySurface() throws Exception {
        Path root = projectRoot();
        String resources = readTree(root.resolve("src/main/resources/config-v2"));

        assertFalse(Files.exists(root.resolve("src/main/resources/config.yaml")), "Velocity must not bundle the legacy config.yaml alongside authoritative config-v2 files");
        assertTrue(resources.contains("language: ko_kr"));
        assertTrue(resources.contains("base-url: https://core-api.internal:8443"));
        assertTrue(resources.contains("auth-token:"));
        assertTrue(resources.contains("default-lobby: Lobby"));
        assertTrue(resources.contains("island-pool: island"));
        assertTrue(resources.contains("ttl: 30s"));
        assertTrue(resources.contains("wait-timeout: 20s"));
        assertTrue(resources.contains("fallback-server: Lobby"));
        assertTrue(resources.contains("hide-backend-node-names: true"));
        assertTrue(resources.contains("- is"));
        assertTrue(resources.contains("- island"));
        assertTrue(resources.contains("- \uC12C"));
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        return builder.toString();
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("settings.gradle.kts"))) {
            return cwd.resolve("cloudislands-velocity");
        }
        return cwd;
    }
}
