package kr.lunaf.cloudislands.velocity;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import kr.lunaf.cloudislands.common.config.ConfigSurfacePolicy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConfigIntegrityTest {
    @Test
    void defaultConfigKeepsPlayerNodeNamesHidden() throws Exception {
        try (InputStream input = VelocityConfigIntegrityTest.class.getClassLoader().getResourceAsStream("config.yaml")) {
            assertNotNull(input);
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(config.contains("hide-node-names: true"));
            assertTrue(config.contains("players see logical islands"));
            assertTrue(config.contains("route summaries"));
        }
    }

    @Test
    void defaultConfigKeepsGoalVelocitySurface() throws Exception {
        try (InputStream input = VelocityConfigIntegrityTest.class.getClassLoader().getResourceAsStream("config.yaml")) {
            assertNotNull(input);
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (String key : ConfigSurfacePolicy.velocityRequiredKeys()) {
                assertTrue(containsPath(config, key), key);
            }
            assertTrue(config.contains("- \"is\""));
            assertTrue(config.contains("- \"island\""));
            assertTrue(config.contains("- \"\uC12C\""));
        }
    }

    private boolean containsPath(String config, String path) {
        String[] parts = path.split("\\.");
        return config.contains(parts[parts.length - 1] + ":");
    }
}
