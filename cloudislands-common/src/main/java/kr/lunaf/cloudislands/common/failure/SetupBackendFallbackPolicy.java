package kr.lunaf.cloudislands.common.failure;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

public final class SetupBackendFallbackPolicy {
    public static final String CONTRACT = "setup-selects-postgresql-mysql-mariadb-or-core-api-with-shared-safe-fallback-before-local";
    public static final String CONFIG_PATH = "setup.database";
    public static final String FALLBACK_CONFIG_PATH = "setup.database.fallback";
    public static final String SELECTED_BACKEND_FIELD = "setup.database.type";
    public static final String FALLBACK_ORDER_FIELD = "setup.database.fallback.order";
    public static final String SETUP_SOURCE_PRECEDENCE = "env-type>setup.database.type>setup.database.core-api.enabled>setup.database.jdbc.url>single-configured-shared-backend>legacy-database.type";
    public static final String CORE_API_ENABLED_FIELD = "setup.database.core-api.enabled";
    public static final String CORE_API_LOCAL_CACHE_WRITES_FIELD = "setup.database.core-api.local-cache-writes.enabled";
    public static final String CORE_API_FLATTENED_FALLBACK_FIELD = "setup.database.core-api.flattened-fallback.enabled";
    public static final String JDBC_URL_FIELD = "setup.database.jdbc.url";
    public static final String PRODUCTION_SAFE_ORDER = "POSTGRESQL,MYSQL,MARIADB,CORE_API";
    public static final String LAST_RESORT_ORDER = "SQLITE,UNSUPPORTED_JDBC";
    public static final String FALLBACK_POLICY = "shared-db-or-core-api-before-unsupported-local-fallback";
    public static final String UNSAFE_LOCAL_POLICY = "unsupported-jdbc-is-last-resort-and-not-valid-for-multi-island-node-production";
    public static final String CORE_API_READY_POLICY = "core-api-ready-requires-cloudislands-api-addon-state-and-bulk-or-flattened-state-writer";
    public static final String JDBC_READY_POLICY = "jdbc-backend-ready-requires-jdbc-url-or-host-database-credentials";
    public static final String LOCAL_CACHE_WRITE_POLICY = "core-api-local-cache-writes-disabled-by-default-and-single-node-rescue-only";
    public static final String FALLBACK_RISK_NO_ORDER = "no-fallback-order";
    public static final String FALLBACK_RISK_NO_READY_BACKEND = "no-ready-backend";
    public static final String FALLBACK_RISK_LOCAL_ONLY = "local-only";
    public static final String FALLBACK_RISK_LOCAL_BEFORE_SHARED = "local-before-shared";
    public static final String FALLBACK_RISK_SHARED_BEFORE_LOCAL = "shared-before-local";
    public static final String FALLBACK_RISK_SHARED_ONLY = "shared-only";

    public static final List<String> SUPPORTED_BACKENDS = List.of(
        "POSTGRESQL",
        "MYSQL",
        "MARIADB",
        "CORE_API"
    );

    public static final List<String> PRODUCTION_FALLBACK_ORDER = List.of(
        "POSTGRESQL",
        "MYSQL",
        "MARIADB",
        "CORE_API"
    );

    public static final List<String> LAST_RESORT_FALLBACK_ORDER = List.of(
        "SQLITE",
        "UNSUPPORTED_JDBC"
    );

    public static final Set<String> SHARED_STATE_BACKENDS = Set.of(
        "POSTGRESQL",
        "MYSQL",
        "MARIADB",
        "CORE_API"
    );

    public static final Set<String> LOCAL_STATE_BACKENDS = Set.of(
        "SQLITE",
        "UNSUPPORTED_JDBC"
    );

    public static final Map<String, List<String>> BACKEND_READINESS_FIELDS = Map.of(
        "CORE_API", List.of(
            "setup.database.core-api.enabled",
            "cloudislands-api",
            "addon-state",
            "table-key-value-bulk-save-or-flattened-fallback"
        ),
        "POSTGRESQL", List.of(
            "setup.database.postgresql.jdbc-url",
            "setup.database.postgresql.host",
            "setup.database.postgresql.database",
            "setup.database.postgresql.username",
            "setup.database.postgresql.password"
        ),
        "MYSQL", List.of(
            "setup.database.mysql.jdbc-url",
            "setup.database.mysql.host",
            "setup.database.mysql.database",
            "setup.database.mysql.username",
            "setup.database.mysql.password"
        ),
        "MARIADB", List.of(
            "setup.database.mariadb.jdbc-url",
            "setup.database.mariadb.host",
            "setup.database.mariadb.database",
            "setup.database.mariadb.username",
            "setup.database.mariadb.password"
        ),
        "SQLITE", List.of(
            "setup.database.sqlite-file",
            "single-node-or-shared-directory-only"
        )
    );

    private SetupBackendFallbackPolicy() {
    }

    public static String normalizeBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return "";
        }
        String normalized = backend.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
        if (normalized.equals("POSTGRES") || normalized.equals("PGSQL")) {
            return "POSTGRESQL";
        }
        if (normalized.equals("MARIADB_JDBC")) {
            return "MARIADB";
        }
        if (normalized.equals("MYSQL_JDBC")) {
            return "MYSQL";
        }
        if (normalized.equals("COREAPI")) {
            return "CORE_API";
        }
        if (normalized.equals("LOCAL") || normalized.equals("LOCAL_SQLITE") || normalized.equals("MEMORY") || normalized.equals("IN_MEMORY")) {
            return "SQLITE";
        }
        return normalized;
    }

    public static boolean supportedBackend(String backend) {
        return SUPPORTED_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean sharedStateBackend(String backend) {
        return SHARED_STATE_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean localStateBackend(String backend) {
        return LOCAL_STATE_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean unsafeLocalFallback(String backend) {
        return localStateBackend(backend);
    }

    public static List<String> setupSourcePrecedence() {
        return List.of(
            "env-type",
            "setup.database.type",
            "setup.database.core-api.enabled",
            "setup.database.jdbc.url",
            "single-configured-shared-backend",
            "legacy-database.type"
        );
    }

    public static List<String> backendReadinessFields(String backend) {
        return BACKEND_READINESS_FIELDS.getOrDefault(normalizeBackend(backend), List.of());
    }

    public static boolean backendHasReadinessContract(String backend) {
        return !backendReadinessFields(backend).isEmpty();
    }

    public static boolean coreApiLocalCacheWritesProductionSafe(boolean enabled, boolean singleNode) {
        return !enabled || singleNode;
    }

    public static String fallbackTarget(String requestedBackend) {
        String normalized = normalizeBackend(requestedBackend);
        if (supportedBackend(normalized)) {
            return normalized;
        }
        return "CORE_API";
    }

    public static boolean fallbackKeepsSharedState(String requestedBackend) {
        return sharedStateBackend(fallbackTarget(requestedBackend));
    }

    public static String fallbackReason(String requestedBackend) {
        String normalized = normalizeBackend(requestedBackend);
        if (normalized.isBlank()) {
            return "setup-backend-empty-use-core-api";
        }
        if (supportedBackend(normalized)) {
            return "setup-backend-supported";
        }
        if (unsafeLocalFallback(normalized)) {
            return "local-fallback-last-resort-not-shared-safe";
        }
        return "setup-backend-unknown-use-core-api";
    }

    public static List<String> fallbackOrder(String configuredOrder) {
        List<String> parsed = parseBackends(configuredOrder);
        if (parsed.isEmpty()) {
            List<String> defaults = new ArrayList<>(PRODUCTION_FALLBACK_ORDER);
            defaults.addAll(LAST_RESORT_FALLBACK_ORDER);
            return List.copyOf(defaults);
        }
        return parsed;
    }

    public static String fallbackReadyChain(String configuredOrder, String readyBackends) {
        List<String> order = fallbackOrder(configuredOrder);
        List<String> ready = parseBackends(readyBackends);
        if (ready.isEmpty()) {
            return "";
        }
        List<String> readyOrder = new ArrayList<>();
        for (String backend : order) {
            if (ready.contains(backend)) {
                readyOrder.add(backend);
            }
        }
        return String.join(",", readyOrder);
    }

    public static String fallbackNotReadyBackends(String configuredOrder, String readyBackends) {
        List<String> order = fallbackOrder(configuredOrder);
        List<String> ready = parseBackends(readyBackends);
        List<String> missing = new ArrayList<>();
        for (String backend : order) {
            if (!ready.contains(backend)) {
                missing.add(backend);
            }
        }
        return String.join(",", missing);
    }

    public static String fallbackRisk(List<String> order) {
        if (order == null || order.isEmpty()) {
            return FALLBACK_RISK_NO_ORDER;
        }
        int firstShared = -1;
        int firstLocal = -1;
        for (int index = 0; index < order.size(); index++) {
            String normalized = normalizeBackend(order.get(index));
            if (firstShared < 0 && sharedStateBackend(normalized)) {
                firstShared = index;
            }
            if (firstLocal < 0 && localStateBackend(normalized)) {
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
        String chain = fallbackReadyChain(configuredOrder, readyBackends);
        if (chain.isBlank()) {
            return parseBackends(readyBackends).isEmpty() ? FALLBACK_RISK_NO_READY_BACKEND : FALLBACK_RISK_NO_READY_BACKEND;
        }
        return fallbackRisk(parseBackends(chain));
    }

    public static boolean fallbackReadyChainProductionSafe(String configuredOrder, String readyBackends) {
        String risk = fallbackReadyChainRisk(configuredOrder, readyBackends);
        return FALLBACK_RISK_SHARED_BEFORE_LOCAL.equals(risk) || FALLBACK_RISK_SHARED_ONLY.equals(risk);
    }

    public static String fallbackReadinessSummary(String configuredOrder, String readyBackends) {
        String ready = fallbackReadyChain(configuredOrder, readyBackends);
        String missing = fallbackNotReadyBackends(configuredOrder, readyBackends);
        return "ready=" + (ready.isBlank() ? "none" : ready) + ";not-ready=" + (missing.isBlank() ? "none" : missing);
    }

    private static List<String> parseBackends(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> backends = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = normalizeBackend(token);
            if (!normalized.isBlank() && !backends.contains(normalized)) {
                backends.add(normalized);
            }
        }
        return List.copyOf(backends);
    }
}
