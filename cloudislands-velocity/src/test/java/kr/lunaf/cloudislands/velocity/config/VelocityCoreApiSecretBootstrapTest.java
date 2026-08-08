package kr.lunaf.cloudislands.velocity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class VelocityCoreApiSecretBootstrapTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void basicModeCreatesAndReusesCrossPlatformCoreApiSecrets() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("plugins").resolve("cloudislands");
        Files.createDirectories(dataDirectory);

        VelocityConfig first = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));
        VelocityConfig second = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));

        assertEquals(64, first.coreToken().length());
        assertEquals(64, first.adminToken().length());
        assertEquals(first.coreToken(), second.coreToken());
        assertEquals(first.adminToken(), second.adminToken());
        assertEquals(first.coreToken(), Files.readString(dataDirectory.resolve("secrets/core-token")).trim());
        assertEquals(first.adminToken(), Files.readString(dataDirectory.resolve("secrets/admin-token")).trim());

        String security = Files.readString(dataDirectory.resolve("config-v2/security.yml"));
        assertTrue(security.contains("${file:../secrets/core-token}"));
        assertTrue(security.contains("${file:../secrets/admin-token}"));
        assertFalse(security.contains("/run/secrets"));
    }

    @Test
    void existingDockerDefaultsMigrateOnlyWhenDockerSecretsAreUnavailable() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("legacy").resolve("plugins").resolve("cloudislands");
        Path configRoot = dataDirectory.resolve("config-v2");
        Files.createDirectories(configRoot);
        Files.writeString(configRoot.resolve("security.yml"), """
            core-api:
              auth-token: "${file:/run/secrets/cloudislands_core_token}"
              admin-token: "${file:/run/secrets/cloudislands_admin_token}"
              auto-generate-tokens: true
            """);

        VelocityConfig config = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));
        String security = Files.readString(configRoot.resolve("security.yml"));

        assertFalse(config.coreToken().isBlank());
        assertFalse(config.adminToken().isBlank());
        assertFalse(security.contains("/run/secrets"));
        assertTrue(security.contains("${file:../secrets/core-token}"));
        assertTrue(security.contains("${file:../secrets/admin-token}"));
    }

    @Test
    void missingCustomSecretFileIsNotSilentlyReplaced() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("custom").resolve("plugins").resolve("cloudislands");
        Path configRoot = dataDirectory.resolve("config-v2");
        Files.createDirectories(configRoot);
        Files.writeString(configRoot.resolve("security.yml"), """
            core-api:
              auth-token: "${file:../../operator/core-token}"
              admin-token: "${env:CUSTOM_ADMIN_TOKEN}"
              auto-generate-tokens: true
            """);

        VelocityConfig config = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));

        assertTrue(config.coreToken().isBlank());
        assertTrue(config.adminToken().isBlank());
        assertFalse(Files.exists(dataDirectory.resolve("secrets/core-token")));
        assertFalse(Files.exists(dataDirectory.resolve("secrets/admin-token")));
    }
}
