package kr.seungmin.satisskyfactory.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SatisDatabaseConfigPolicy {
    public static final String ENV_TYPE = "CLOUDISLANDS_SATIS_DATABASE_TYPE";
    public static final String ENV_JDBC_URL = "CLOUDISLANDS_SATIS_JDBC_URL";
    public static final String ENV_USERNAME = "CLOUDISLANDS_SATIS_DB_USERNAME";
    public static final String ENV_PASSWORD = "CLOUDISLANDS_SATIS_DB_PASSWORD";
    public static final String SETUP_ROOT = "setup.database";
    public static final String ADDON_ROOT = "addons.cloudislands-satis.database";
    public static final String LEGACY_ROOT = "database";
    public static final String FALLBACK_PRECEDENCE = "env,setup.database,addons.cloudislands-satis.database,database";
    public static final String FALLBACK_RISK_NO_ORDER = "no-fallback-order";
    public static final String FALLBACK_RISK_NO_READY_BACKEND = "no-ready-backend";
    public static final String FALLBACK_RISK_LOCAL_ONLY = "local-only";
    public static final String FALLBACK_RISK_LOCAL_BEFORE_SHARED = "local-before-shared";
    public static final String FALLBACK_RISK_SHARED_BEFORE_LOCAL = "shared-before-local";
    public static final String FALLBACK_RISK_SHARED_ONLY = "shared-only";

    private static final List<String> TYPE_PRIORITY = List.of(
            ENV_TYPE,
            "setup.database.type",
            "addons.cloudislands-satis.database.type",
            "setup.database.core-api.enabled",
            "jdbc-url-inference",
            "setup.database.<backend>",
            "database.type"
    );

    private static final List<String> PATH_PRIORITY = List.of(
            "CLOUDISLANDS_SATIS_DB",
            "setup.database.path",
            "addons.cloudislands-satis.database.path",
            "database.path",
            "setup.database.shared-directory",
            "addons.cloudislands-satis.database.shared-directory",
            "database.shared-directory",
            "setup.database.sqlite-file",
            "addons.cloudislands-satis.database.sqlite-file",
            "database.sqlite-file"
    );

    private static final List<String> COMMON_JDBC_ALIASES = List.of(
            ENV_JDBC_URL,
            "setup.database.jdbc.url",
            "addons.cloudislands-satis.database.jdbc.url",
            "database.jdbc.url"
    );

    private static final List<String> CREDENTIAL_ALIASES = List.of(
            ENV_USERNAME,
            ENV_PASSWORD,
            "setup.database.jdbc.username",
            "setup.database.jdbc.password",
            "addons.cloudislands-satis.database.jdbc.username",
            "addons.cloudislands-satis.database.jdbc.password",
            "database.jdbc.username",
            "database.jdbc.password"
    );

    private static final List<String> SHARED_BACKENDS = List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API");
    private static final List<String> LOCAL_BACKENDS = List.of("SQLITE");

    private SatisDatabaseConfigPolicy() {
    }

    public static List<String> typePriority() {
        return TYPE_PRIORITY;
    }

    public static List<String> pathPriority() {
        return PATH_PRIORITY;
    }

    public static List<String> commonJdbcAliases() {
        return COMMON_JDBC_ALIASES;
    }

    public static List<String> credentialAliases() {
        return CREDENTIAL_ALIASES;
    }

    public static String commonJdbcAliasMetadata() {
        return "setup.database.jdbc.url,setup.database.<backend>.jdbc-url,setup.database.<backend>.url,addons.cloudislands-satis.database.jdbc.url,database.jdbc.url,database.<backend>.url";
    }

    public static List<String> sharedBackends() {
        return SHARED_BACKENDS;
    }

    public static List<String> localBackends() {
        return LOCAL_BACKENDS;
    }

    public static String normalizeBackend(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSTGRES", "PG" -> "POSTGRESQL";
            case "MARIA" -> "MARIADB";
            case "CORE", "COREAPI", "CLOUDISLANDS", "CLOUDISLANDS_API" -> "CORE_API";
            case "IN_MEMORY", "MEMORY", "LOCAL", "LOCAL_SQLITE" -> "SQLITE";
            default -> normalized;
        };
    }

    public static boolean sharedBackend(String value) {
        return SHARED_BACKENDS.contains(normalizeBackend(value));
    }

    public static boolean localBackend(String value) {
        return LOCAL_BACKENDS.contains(normalizeBackend(value));
    }

    public static String firstSharedFallback(List<String> order) {
        if (order == null) {
            return "";
        }
        for (String backend : order) {
            String normalized = normalizeBackend(backend);
            if (SHARED_BACKENDS.contains(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    public static int localFallbackPosition(List<String> order) {
        if (order == null) {
            return -1;
        }
        for (int index = 0; index < order.size(); index++) {
            if (localBackend(order.get(index))) {
                return index;
            }
        }
        return -1;
    }

    public static String fallbackRisk(List<String> order) {
        if (order == null || order.isEmpty()) {
            return FALLBACK_RISK_NO_ORDER;
        }
        int firstShared = -1;
        int firstLocal = -1;
        for (int index = 0; index < order.size(); index++) {
            String normalized = normalizeBackend(order.get(index));
            if (firstShared < 0 && SHARED_BACKENDS.contains(normalized)) {
                firstShared = index;
            }
            if (firstLocal < 0 && LOCAL_BACKENDS.contains(normalized)) {
                firstLocal = index;
            }
        }
        if (firstShared < 0 && firstLocal < 0) {
            return FALLBACK_RISK_NO_READY_BACKEND;
        }
        if (firstShared < 0) {
            return FALLBACK_RISK_LOCAL_ONLY;
        }
        if (firstLocal < 0) {
            return FALLBACK_RISK_SHARED_ONLY;
        }
        return firstShared < firstLocal ? FALLBACK_RISK_SHARED_BEFORE_LOCAL : FALLBACK_RISK_LOCAL_BEFORE_SHARED;
    }

    public static String fallbackReadyChainRisk(String configuredOrder, String readyBackends) {
        String readyChain = fallbackReadyChain(configuredOrder, readyBackends);
        return readyChain.isBlank() ? fallbackReadyChainEmptyRisk(configuredOrder, readyBackends) : fallbackRisk(parseBackendList(readyChain));
    }

    public static String fallbackReadyChain(String configuredOrder, String readyBackends) {
        List<String> order = parseBackendList(configuredOrder);
        if (order.isEmpty()) {
            return "";
        }
        List<String> ready = parseBackendList(readyBackends);
        if (ready.isEmpty()) {
            return "";
        }
        List<String> readyOrder = new ArrayList<>();
        for (String backend : order) {
            String normalized = normalizeBackend(backend);
            if (ready.contains(normalized)) {
                readyOrder.add(normalized);
            }
        }
        return String.join(",", readyOrder);
    }

    public static String fallbackNotReadyBackends(String configuredOrder, String readyBackends) {
        List<String> order = parseBackendList(configuredOrder);
        List<String> ready = parseBackendList(readyBackends);
        List<String> missing = new ArrayList<>();
        for (String backend : order) {
            String normalized = normalizeBackend(backend);
            if (!normalized.isBlank() && !ready.contains(normalized)) {
                missing.add(normalized);
            }
        }
        return String.join(",", missing);
    }

    public static String fallbackReadinessSummary(String configuredOrder, String readyBackends) {
        String readyChain = fallbackReadyChain(configuredOrder, readyBackends);
        String missing = fallbackNotReadyBackends(configuredOrder, readyBackends);
        return "ready=" + (readyChain.isBlank() ? "none" : readyChain) + ";not-ready=" + (missing.isBlank() ? "none" : missing);
    }

    private static String fallbackReadyChainEmptyRisk(String configuredOrder, String readyBackends) {
        if (parseBackendList(configuredOrder).isEmpty()) {
            return FALLBACK_RISK_NO_ORDER;
        }
        if (parseBackendList(readyBackends).isEmpty()) {
            return FALLBACK_RISK_NO_READY_BACKEND;
        }
        return FALLBACK_RISK_NO_READY_BACKEND;
    }

    public static boolean fallbackReadyChainProductionSafe(String configuredOrder, String readyBackends) {
        String risk = fallbackReadyChainRisk(configuredOrder, readyBackends);
        return FALLBACK_RISK_SHARED_BEFORE_LOCAL.equals(risk) || FALLBACK_RISK_SHARED_ONLY.equals(risk);
    }

    private static List<String> parseBackendList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = normalizeBackend(token);
            if (!normalized.isBlank() && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }
}
