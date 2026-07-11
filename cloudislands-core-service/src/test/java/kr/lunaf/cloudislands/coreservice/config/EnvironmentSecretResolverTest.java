package kr.lunaf.cloudislands.coreservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentSecretResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void directValueTakesPriorityOverFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("token"), "file-token\n");

        assertEquals("direct-token", EnvironmentSecretResolver.value(
            Map.of("CI_CORE_TOKEN", "direct-token", "CI_CORE_TOKEN_FILE", file.toString()),
            "CI_CORE_TOKEN",
            "fallback"
        ));
    }

    @Test
    void readsAndTrimsDockerSecretFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("token"), "  file-token\n");

        assertEquals("file-token", EnvironmentSecretResolver.value(
            Map.of("CI_CORE_TOKEN_FILE", file.toString()),
            "CI_CORE_TOKEN",
            "fallback"
        ));
    }

    @Test
    void usesFallbackOnlyWhenNeitherSourceIsConfigured() {
        assertEquals("fallback", EnvironmentSecretResolver.value(Map.of(), "CI_CORE_TOKEN", "fallback"));
    }

    @Test
    void rejectsMissingOrEmptyConfiguredSecretFiles() throws Exception {
        Path empty = Files.createFile(tempDir.resolve("empty"));

        IllegalStateException missing = assertThrows(IllegalStateException.class, () ->
            EnvironmentSecretResolver.value(Map.of("CI_CORE_TOKEN_FILE", tempDir.resolve("missing").toString()), "CI_CORE_TOKEN", "fallback")
        );
        IllegalStateException blank = assertThrows(IllegalStateException.class, () ->
            EnvironmentSecretResolver.value(Map.of("CI_CORE_TOKEN_FILE", empty.toString()), "CI_CORE_TOKEN", "fallback")
        );

        assertTrue(missing.getMessage().contains("Could not read secret file for CI_CORE_TOKEN"));
        assertTrue(blank.getMessage().contains("Secret file for CI_CORE_TOKEN is empty"));
    }
}
