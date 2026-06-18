package kr.lunaf.cloudislands.common.failure;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SetupBackendFallbackPolicy {
    public static final String CONTRACT = "setup-selects-postgresql-mysql-mariadb-or-core-api-with-shared-safe-fallback-before-local";
    public static final String CONFIG_PATH = "setup.database";
    public static final String FALLBACK_CONFIG_PATH = "setup.database.fallback";
    public static final String SELECTED_BACKEND_FIELD = "setup.database.type";
    public static final String FALLBACK_ORDER_FIELD = "setup.database.fallback.order";
    public static final String PRODUCTION_SAFE_ORDER = "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC";
    public static final String FALLBACK_POLICY = "shared-db-or-core-api-before-unsupported-local-fallback";
    public static final String UNSAFE_LOCAL_POLICY = "unsupported-jdbc-is-last-resort-and-not-valid-for-multi-island-node-production";

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
        "CORE_API",
        "UNSUPPORTED_JDBC"
    );

    public static final Set<String> SHARED_STATE_BACKENDS = Set.of(
        "POSTGRESQL",
        "MYSQL",
        "MARIADB",
        "CORE_API"
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
        return normalized;
    }

    public static boolean supportedBackend(String backend) {
        return SUPPORTED_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean sharedStateBackend(String backend) {
        return SHARED_STATE_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean unsafeLocalFallback(String backend) {
        return "UNSUPPORTED_JDBC".equals(normalizeBackend(backend));
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
            return "unsupported-jdbc-last-resort-not-shared-safe";
        }
        return "setup-backend-unknown-use-core-api";
    }
}
