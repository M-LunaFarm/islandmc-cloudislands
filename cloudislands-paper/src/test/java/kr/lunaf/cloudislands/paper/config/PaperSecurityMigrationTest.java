package kr.lunaf.cloudislands.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperSecurityMigrationTest {
    @TempDir
    Path dataFolder;

    @Test
    void movesPlaintextTokensIntoCrossPlatformSecretFilesBeforeValidation() throws Exception {
        Path config = security("""
            core-api:
              auth-token: "plain-core-token"
              admin-token: "plain-admin-token"
            redis:
              password: ""
            storage:
              access-key: ""
              secret-key: ""
              bearer-token: ""
            forwarding:
              secret: ""
            """);

        PaperRuntimeConfigLoader.SecurityMigration migration = PaperRuntimeConfigLoader.migrateSecurityConfig(dataFolder);

        String migrated = Files.readString(config);
        assertTrue(migration.changed());
        assertEquals(2, migration.imported().size());
        assertTrue(migrated.contains("auth-token: \"${file:secrets/core-token}\""));
        assertTrue(migrated.contains("admin-token: \"${file:secrets/admin-token}\""));
        assertFalse(migrated.contains("plain-core-token"));
        assertEquals("plain-core-token", Files.readString(dataFolder.resolve("secrets/core-token")).trim());
        assertEquals("plain-admin-token", Files.readString(dataFolder.resolve("secrets/admin-token")).trim());
    }

    @Test
    void replacesMissingDockerDefaultsWithoutInventingRedisOrS3Credentials() throws Exception {
        Path config = security("""
            core-api:
              auth-token: "${file:/run/secrets/cloudislands_core_token}"
              admin-token: "${file:/run/secrets/cloudislands_admin_token}"
            redis:
              password: "${file:/run/secrets/redis_password}"
            storage:
              access-key: "${file:/run/secrets/s3_access_key}"
              secret-key: "${file:/run/secrets/s3_secret_key}"
              bearer-token: "${file:/run/secrets/s3_bearer_token}"
            forwarding:
              secret: ""
            """);

        PaperRuntimeConfigLoader.migrateSecurityConfig(dataFolder);

        String migrated = Files.readString(config);
        assertTrue(migrated.contains("${file:secrets/core-token}"));
        assertTrue(migrated.contains("${file:secrets/admin-token}"));
        assertFalse(migrated.contains("/run/secrets"));
        assertFalse(Files.exists(dataFolder.resolve("secrets/redis-password")));
        assertFalse(Files.exists(dataFolder.resolve("secrets/s3-access-key")));
    }

    private Path security(String yaml) throws Exception {
        Path config = dataFolder.resolve("config-v2/security.yml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, yaml);
        return config;
    }
}
