package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisPluginMetadataTest {
    @Test
    void pluginMetadataUsesCentralProjectVersionPaperBaselineAndLibraryVersions() throws IOException {
        String descriptor = mainResource("plugin.yml");

        assertTrue(descriptor.contains("version: 1.0.1"));
        assertTrue(descriptor.contains("api-version: \"1.21.11\""));
        assertTrue(descriptor.contains("org.postgresql:postgresql:42.7.7"));
        assertTrue(descriptor.contains("com.mysql:mysql-connector-j:9.1.0"));
        assertTrue(descriptor.contains("org.mariadb.jdbc:mariadb-java-client:3.5.1"));
        assertFalse(descriptor.contains("${"));
    }

    private static String mainResource(String name) throws IOException {
        try (var stream = SatisPluginMetadataTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream, name + " resource should be available");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
