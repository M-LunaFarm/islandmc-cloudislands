package kr.lunaf.cloudislands.paper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPluginMetadataTest {
    @Test
    void pluginMetadataUsesCentralProjectVersionAndPaperBaseline() throws IOException {
        String descriptor = mainResource("plugin.yml");

        assertTrue(descriptor.contains("version: '1.0.1'") || descriptor.contains("version: 1.0.1"));
        assertTrue(descriptor.contains("api-version: '1.21.11'"));
        assertFalse(descriptor.contains("0.1.0"));
        assertFalse(descriptor.contains("${"));
    }

    private static String mainResource(String name) throws IOException {
        try (var stream = PaperPluginMetadataTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream, name + " resource should be available");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
