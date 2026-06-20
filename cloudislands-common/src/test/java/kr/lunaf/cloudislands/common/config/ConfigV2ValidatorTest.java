package kr.lunaf.cloudislands.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigV2ValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsDuplicateKeysAtTheSameYamlLevel() {
        ConfigValidationResult result = ConfigV2Validator.validateYaml("test.yml", """
            core-api:
              timeout: 2s
              timeout: 3s
            redis:
              timeout: 1s
            """);

        assertFalse(result.valid());
        assertTrue(result.hasIssue("DUPLICATE_KEY"));
        assertEquals("core-api.timeout", result.issues().get(0).path());
    }

    @Test
    void allowsSameLeafKeyUnderDifferentParents() {
        ConfigValidationResult result = ConfigV2Validator.validateYaml("test.yml", """
            core-api:
              timeout: 2s
            redis:
              timeout: 1s
            """);

        assertTrue(result.valid(), result.summary());
    }

    @Test
    void rejectsPlaintextSecretsButAllowsEnvAndFileReferences() {
        assertTrue(ConfigV2Validator.validateYaml("safe.yml", """
            api:
              auth-token: "${env:CORE_TOKEN}"
            storage:
              secret-key: "${file:/run/secrets/s3_secret_key}"
            """).valid());

        ConfigValidationResult result = ConfigV2Validator.validateYaml("unsafe.yml", """
            api:
              auth-token: literal-token
            """);

        assertFalse(result.valid());
        assertTrue(result.hasIssue("PLAINTEXT_SECRET"));
    }

    @Test
    void resolvesEnvironmentAndSecretFileReferences() throws Exception {
        Path secretFile = tempDir.resolve("token.txt");
        Files.writeString(secretFile, "file-secret\n");

        assertEquals("env-secret", ConfigSecretResolver.resolve("${env:CORE_TOKEN}", key -> Map.of("CORE_TOKEN", "env-secret").get(key), tempDir).value());
        assertEquals("file-secret", ConfigSecretResolver.resolve("${file:token.txt}", key -> null, tempDir).value());
        assertTrue(ConfigSecretResolver.resolve("${env:MISSING}", key -> null, tempDir).issue().hasCode("MISSING_ENV"));
    }

    @Test
    void redactsSecretsFromEffectiveConfigOutput() {
        String redacted = ConfigV2Validator.redactYaml("""
            api:
              auth-token: resolved-token
              admin-token: resolved-admin
            storage:
              bucket: cloudislands
            """);

        assertTrue(redacted.contains("auth-token: <redacted>"));
        assertTrue(redacted.contains("admin-token: <redacted>"));
        assertTrue(redacted.contains("bucket: cloudislands"));
        assertFalse(redacted.contains("resolved-token"));
        assertFalse(redacted.contains("resolved-admin"));
    }

    @Test
    void reloadRollbackKeepsPreviousEffectiveConfigWhenCandidateInvalid() {
        ConfigReloadPlan.ReloadResult result = ConfigReloadPlan.reload("profile: production", """
            api:
              auth-token: literal-token
            """);

        assertFalse(result.applied());
        assertEquals("profile: production", result.effectiveYaml());
        assertTrue(result.validation().hasIssue("PLAINTEXT_SECRET"));
    }

    @Test
    void bundledConfigV2YamlPassesDuplicateAndSecretValidation() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = Stream.of(
                root.resolve("cloudislands-paper/src/main/resources/config-v2"),
                root.resolve("cloudislands-core-service/src/main/resources/config-v2"),
                root.resolve("cloudislands-velocity/src/main/resources/config-v2")
            ).flatMap(ConfigV2ValidatorTest::yamlFiles)) {
            String violations = files
                .map(path -> Map.entry(root.relativize(path).toString(), ConfigV2Validator.validateYaml(root.relativize(path).toString(), read(path))))
                .filter(entry -> !entry.getValue().valid())
                .map(entry -> entry.getKey() + ": " + entry.getValue().summary())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    private static Stream<Path> yamlFiles(Path root) {
        try {
            if (Files.notExists(root)) {
                return Stream.empty();
            }
            return Files.walk(root).filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("cloudislands-api"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("cloudislands-api"))) {
            return parent;
        }
        return current;
    }
}
