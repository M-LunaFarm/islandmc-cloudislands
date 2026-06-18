package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.config.ConfigSurfacePolicy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperConfigSurfaceTest {
    @Test
    void defaultConfigKeepsGoalPaperAgentSurface() throws Exception {
        try (InputStream input = PaperConfigSurfaceTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (String key : ConfigSurfacePolicy.paperRequiredKeys()) {
                assertTrue(containsPath(config, key), key);
            }
            assertTrue(config.contains("id: \"island-1\""));
            assertTrue(config.contains("role: \"ISLAND_NODE\""));
            assertTrue(config.contains("pool: \"island\""));
            assertTrue(config.contains("shard-world-prefix: \"ci_shard_\""));
            assertTrue(config.contains("cell-size: 1024"));
            assertTrue(config.contains("default-island-size: 300"));
        }
    }

    private boolean containsPath(String config, String path) {
        String[] parts = path.split("\\.");
        return config.contains(parts[parts.length - 1] + ":");
    }
}
