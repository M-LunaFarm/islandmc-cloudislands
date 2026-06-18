package kr.lunaf.cloudislands.coreservice.config;

import kr.lunaf.cloudislands.common.config.ConfigSurfacePolicy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConfigSurfaceTest {
    @Test
    void defaultConfigKeepsGoalCoreApiSurface() throws Exception {
        try (InputStream input = CoreConfigSurfaceTest.class.getClassLoader().getResourceAsStream("application.yaml")) {
            assertNotNull(input);
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (String key : ConfigSurfacePolicy.coreRequiredKeys()) {
                assertTrue(containsPath(config, key), key);
            }
            assertTrue(config.contains("bind: \"0.0.0.0\""));
            assertTrue(config.contains("port: 8443"));
            assertTrue(config.contains("jdbc:postgresql://postgres.internal:5432/cloudislands"));
            assertTrue(config.contains("soft-full-policy: \"AVOID_NEW_ACTIVATIONS\""));
            assertTrue(config.contains("hard-full-policy: \"DENY_OR_QUEUE\""));
            assertTrue(config.contains("migration-policy: \"INACTIVE_ONLY_AUTOMATIC\""));
            assertTrue(config.contains("require-mtls: true"));
            assertTrue(config.contains("admin-api-enabled: true"));
        }
    }

    private boolean containsPath(String config, String path) {
        String[] parts = path.split("\\.");
        return config.contains(parts[parts.length - 1] + ":");
    }
}
