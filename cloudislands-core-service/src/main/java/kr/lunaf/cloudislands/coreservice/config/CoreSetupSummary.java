package kr.lunaf.cloudislands.coreservice.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoreSetupSummary {
    private CoreSetupSummary() {
    }

    public static String jdbcBackend(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "NONE";
        }
        String value = jdbcUrl.toLowerCase(Locale.ROOT);
        if (value.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        if (value.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (value.startsWith("jdbc:mariadb:")) {
            return "MARIADB";
        }
        return "UNKNOWN";
    }

    public static boolean coreJdbcSupported(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String value = jdbcUrl.toLowerCase(Locale.ROOT);
        return value.startsWith("jdbc:postgresql:")
            || value.startsWith("jdbc:mysql:")
            || value.startsWith("jdbc:mariadb:");
    }

    public static String coreJdbcFallbackReason(CoreServiceConfig config) {
        String configuredType = config.configuredDatabaseType() == null ? "" : config.configuredDatabaseType();
        String effectiveBackend = jdbcBackend(config.jdbcUrl());
        if (!configuredType.isBlank() && !configuredType.equals(effectiveBackend) && !configuredType.equals("UNKNOWN")) {
            if (coreJdbcSupported(config.jdbcUrl())) {
                return "CORE_JDBC_" + effectiveBackend + "_FALLBACK_FOR_" + configuredType;
            }
            return "CORE_JDBC_DISABLED_FOR_" + configuredType;
        }
        if (coreJdbcSupported(config.jdbcUrl())) {
            return "";
        }
        if ("JDBC".equalsIgnoreCase(config.repositoryMode()) || "JDBC".equalsIgnoreCase(config.jobQueueMode())) {
            return "CORE_JDBC_SUPPORTED_BACKEND_REQUIRED";
        }
        return "CORE_JDBC_DISABLED_FOR_" + effectiveBackend;
    }

    public static boolean coreJdbcFallbackActive(CoreServiceConfig config) {
        String reason = coreJdbcFallbackReason(config);
        return reason != null && !reason.isBlank();
    }

    public static boolean coreSetupFallbackSafetyForced(CoreServiceConfig config) {
        return config.setupDatabaseFallbackSafetyForced();
    }

    public static String coreSetupFallbackPolicy(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "native-" + jdbcBackend(config.jdbcUrl()).toLowerCase(Locale.ROOT) + "-jdbc";
        }
        if ("blocked-non-durable-fallback".equals(config.setupDatabaseFallbackReadiness())) {
            return "blocked-non-durable-fallback";
        }
        String reason = coreJdbcFallbackReason(config);
        if (reason.startsWith("CORE_JDBC_") && reason.contains("_FALLBACK_FOR_")) {
            String backend = reason.substring("CORE_JDBC_".length(), reason.indexOf("_FALLBACK_FOR_"));
            return "configured-" + backend.toLowerCase(Locale.ROOT) + "-jdbc-fallback";
        }
        return "configured-safe-fallback";
    }

    public static String coreSetupFallbackMode(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "NONE";
        }
        if ("blocked-non-durable-fallback".equals(config.setupDatabaseFallbackReadiness())) {
            return "BLOCKED_NON_DURABLE_CORE_FALLBACK";
        }
        String reason = coreJdbcFallbackReason(config);
        if (reason.startsWith("CORE_JDBC_") && reason.contains("_FALLBACK_FOR_")) {
            return reason.substring("CORE_JDBC_".length(), reason.indexOf("_FALLBACK_FOR_")) + "_JDBC_FALLBACK";
        }
        return "IN_MEMORY_REPOSITORIES_AND_JOBS";
    }

    public static String coreJdbcFallbackStatus(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "native-" + jdbcBackend(config.jdbcUrl()).toLowerCase(Locale.ROOT) + "-jdbc";
        }
        return coreSetupFallbackPolicy(config)
            + ":repo=" + (config.jdbcRepositories() ? "JDBC" : "IN_MEMORY")
            + ",jobs=" + (config.jdbcJobs() ? "JDBC" : config.redisJobs() ? "REDIS" : "IN_MEMORY")
            + ",order=" + config.setupDatabaseFallbackOrder();
    }

    public static String coreSetupFallbackCandidateChain(CoreServiceConfig config) {
        return String.join(">", coreSetupFallbackCandidates(config));
    }

    public static String coreSetupFallbackReadyBackends(CoreServiceConfig config) {
        List<String> ready = new ArrayList<>();
        for (String candidate : coreSetupFallbackCandidates(config)) {
            if (coreSetupFallbackCandidateReady(config, candidate)) {
                ready.add(candidate);
            }
        }
        return ready.isEmpty() ? "NONE" : String.join(",", ready);
    }

    public static String coreSetupFallbackMissingBackends(CoreServiceConfig config) {
        List<String> missing = new ArrayList<>();
        for (String candidate : coreSetupFallbackCandidates(config)) {
            if (!coreSetupFallbackCandidateReady(config, candidate)) {
                missing.add(candidate + ":" + coreSetupFallbackCandidateMissingReason(config, candidate));
            }
        }
        return missing.isEmpty() ? "NONE" : String.join(",", missing);
    }

    public static String coreSetupFallbackDecision(CoreServiceConfig config) {
        return "requested=" + config.setupDatabaseRequestedBackend()
            + ",target=" + config.setupDatabaseFallbackTarget()
            + ",effective=" + config.setupDatabaseEffectiveBackend()
            + ",ready=" + coreSetupFallbackReadyBackends(config)
            + ",missing=" + coreSetupFallbackMissingBackends(config)
            + ",reason=" + config.setupDatabaseFallbackReason();
    }

    private static List<String> coreSetupFallbackCandidates(CoreServiceConfig config) {
        String order = config.setupDatabaseFallbackOrder();
        if (order == null || order.isBlank()) {
            order = "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC";
        }
        List<String> candidates = new ArrayList<>();
        for (String raw : order.split("[,>\\s]+")) {
            String candidate = normalizeSetupBackend(raw);
            if (!candidate.isBlank() && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("POSTGRESQL");
            candidates.add("MYSQL");
            candidates.add("MARIADB");
            candidates.add("CORE_API");
            candidates.add("UNSUPPORTED_JDBC");
        }
        return candidates;
    }

    private static boolean coreSetupFallbackCandidateReady(CoreServiceConfig config, String candidate) {
        String effective = jdbcBackend(config.jdbcUrl());
        if ("POSTGRESQL".equals(candidate) || "MYSQL".equals(candidate) || "MARIADB".equals(candidate)) {
            return candidate.equals(effective) && coreJdbcSupported(config.jdbcUrl());
        }
        if ("CORE_API".equals(candidate)) {
            return config.setupDatabaseCoreApiClientReady();
        }
        if ("IN_MEMORY".equals(candidate) || "UNSUPPORTED_JDBC".equals(candidate)) {
            return false;
        }
        return false;
    }

    private static String coreSetupFallbackCandidateMissingReason(CoreServiceConfig config, String candidate) {
        String effective = jdbcBackend(config.jdbcUrl());
        if ("POSTGRESQL".equals(candidate) || "MYSQL".equals(candidate) || "MARIADB".equals(candidate)) {
            return candidate.equals(effective) ? "jdbc-not-supported" : "not-selected-or-not-configured";
        }
        if ("CORE_API".equals(candidate)) {
            return config.setupDatabaseCoreApiClientMode() ? "core-api-url-or-token-missing" : "core-api-not-selected";
        }
        if ("IN_MEMORY".equals(candidate) || "UNSUPPORTED_JDBC".equals(candidate)) {
            if ("UNAVAILABLE_NON_DURABLE".equals(config.setupDatabaseEffectiveBackend())) {
                return "blocked-non-durable-fallback";
            }
            if ("IN_MEMORY_FALLBACK".equals(config.setupDatabaseEffectiveBackend())) {
                return "non-durable-readiness-failed";
            }
            return "safety-fallback-not-active";
        }
        return "unknown-backend";
    }

    private static String normalizeSetupBackend(String backend) {
        String value = backend == null ? "" : backend.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "", "NONE" -> "";
            case "POSTGRES", "PG" -> "POSTGRESQL";
            case "MARIA" -> "MARIADB";
            case "CORE", "COREAPI", "CLOUDISLANDS", "CLOUDISLANDS_API" -> "CORE_API";
            case "MEMORY", "LOCAL", "LOCAL_SQLITE" -> "IN_MEMORY";
            case "UNSUPPORTED" -> "UNSUPPORTED_JDBC";
            default -> value;
        };
    }
}
