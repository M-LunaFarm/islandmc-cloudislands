package kr.lunaf.cloudislands.velocity;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
}
