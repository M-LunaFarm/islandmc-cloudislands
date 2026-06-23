package kr.lunaf.cloudislands.coreservice.config;

import kr.lunaf.cloudislands.common.config.ConfigSurfacePolicy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            assertTrue(config.contains("auth-mode: \"${CI_AUTH_MODE}\""));
            assertTrue(config.contains("node-credentials: \"${CI_NODE_CREDENTIALS}\""));
            assertTrue(config.contains("require-mtls: true"));
            assertTrue(config.contains("admin-api-enabled: true"));
            assertFalse(config.contains("database: \"\""), "Core default config must not expose setup.database.database aliases next to setup.database.name");
            assertFalse(config.contains("database-type: \"\""), "Core default config must not expose flat setup.database-type aliases next to setup.database.type");
            assertFalse(config.contains("database-password: \"\""), "Core default config must not expose flat setup.database-password aliases next to setup.database.password");
        }
    }

    @Test
    void goalCoreConfigKeysAreLoadedAndConsumedByRuntimeCode() throws Exception {
        String loader = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/config/CoreServiceConfig.java"), StandardCharsets.UTF_8);
        String runtime = String.join("\n",
            Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/CloudIslandsCoreApplication.java"), StandardCharsets.UTF_8),
            Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/CoreBootstrap.java"), StandardCharsets.UTF_8),
            Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/metrics/CoreMetricsFactory.java"), StandardCharsets.UTF_8),
            Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/CoreConfigRoutes.java"), StandardCharsets.UTF_8)
        );
        Map<String, ConfigPathCoverage> coverage = Map.ofEntries(
            Map.entry("server.bind", new ConfigPathCoverage("\"server.bind\"", List.of("config.bind()"))),
            Map.entry("server.port", new ConfigPathCoverage("\"server.port\"", List.of("config.port()"))),
            Map.entry("database.jdbc-url", new ConfigPathCoverage("\"database.jdbc-url\"", List.of("config.jdbcUrl()"))),
            Map.entry("database.username", new ConfigPathCoverage("\"database.username\"", List.of("config.databaseUsername()"))),
            Map.entry("database.password", new ConfigPathCoverage("\"database.password\"", List.of("config.databasePassword()"))),
            Map.entry("database.pool-size", new ConfigPathCoverage("\"database.pool-size\"", List.of("config.databasePoolSize()"))),
            Map.entry("redis.uri", new ConfigPathCoverage("\"redis.uri\"", List.of("config.redisUri()"))),
            Map.entry("storage.type", new ConfigPathCoverage("\"storage.type\"", List.of("config.storageType()"))),
            Map.entry("storage.bucket", new ConfigPathCoverage("\"storage.bucket\"", List.of("config.storageBucket()"))),
            Map.entry("routing.heartbeat-timeout-seconds", new ConfigPathCoverage("\"routing.heartbeat-timeout-seconds\"", List.of("config.heartbeatTimeout()"))),
            Map.entry("routing.lease-duration-seconds", new ConfigPathCoverage("\"routing.lease-duration-seconds\"", List.of("config.leaseDuration()"))),
            Map.entry("routing.soft-full-policy", new ConfigPathCoverage("\"routing.soft-full-policy\"", List.of("config.softFullPolicy()"))),
            Map.entry("routing.hard-full-policy", new ConfigPathCoverage("\"routing.hard-full-policy\"", List.of("config.hardFullPolicy()"))),
            Map.entry("routing.migration-policy", new ConfigPathCoverage("\"routing.migration-policy\"", List.of("config.migrationPolicy()"))),
            Map.entry("security.require-mtls", new ConfigPathCoverage("\"security.require-mtls\"", List.of("config.requireMtls()"))),
            Map.entry("security.admin-api-enabled", new ConfigPathCoverage("\"security.admin-api-enabled\"", List.of("config.adminApiEnabled()")))
        );

        assertTrue(coverage.keySet().containsAll(ConfigSurfacePolicy.coreRequiredKeys()), "Core config coverage must include every required key");
        assertTrue(ConfigSurfacePolicy.coreRequiredKeys().containsAll(coverage.keySet()), "Core config coverage must not keep stale key mappings");
        for (String key : ConfigSurfacePolicy.coreRequiredKeys()) {
            ConfigPathCoverage keyCoverage = coverage.get(key);
            assertTrue(loader.contains(keyCoverage.loaderSignal()), key + " must be loaded by CoreServiceConfig");
            assertTrue(keyCoverage.consumerSignals().stream().anyMatch(runtime::contains), key + " must be consumed by runtime code");
        }
    }

    private boolean containsPath(String config, String path) {
        String[] parts = path.split("\\.");
        return config.contains(parts[parts.length - 1] + ":");
    }

    private record ConfigPathCoverage(String loaderSignal, List<String> consumerSignals) {
    }
}
