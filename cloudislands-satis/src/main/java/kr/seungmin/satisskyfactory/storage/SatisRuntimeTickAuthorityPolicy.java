package kr.seungmin.satisskyfactory.storage;

import kr.seungmin.satisskyfactory.database.DatabaseService;

public final class SatisRuntimeTickAuthorityPolicy {
    public static final String CORE_API_TICK_POLICY = "core-api-requires-cloudislands-api-addon-state-and-hydrated-island";
    public static final String SHARED_SQL_TICK_POLICY = "shared-sql-backend-uses-cloudislands-runtime-owner-fence";
    public static final String LOCAL_FALLBACK_TICK_POLICY = "local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-ticks";
    public static final String CORE_API_WRITE_POLICY = "core-api-writes-require-addon-state-write-authority";
    public static final String SHARED_SQL_WRITE_POLICY = "shared-sql-backend-can-write-under-cloudislands-runtime-owner-fence";
    public static final String LOCAL_FALLBACK_WRITE_POLICY = "local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-writes";

    private SatisRuntimeTickAuthorityPolicy() {
    }

    public static boolean tickReady(DatabaseService.StorageBackend backend, boolean cloudIslandsApiAvailable,
                                    boolean addonStateEnabled, boolean coreHydrated) {
        if (backend == null) {
            return false;
        }
        return switch (backend) {
            case CORE_API -> cloudIslandsApiAvailable && addonStateEnabled && coreHydrated;
            case POSTGRESQL, MYSQL, MARIADB -> true;
            case SQLITE -> false;
        };
    }

    public static String tickPolicy(DatabaseService.StorageBackend backend) {
        if (backend == null) {
            return LOCAL_FALLBACK_TICK_POLICY;
        }
        return switch (backend) {
            case CORE_API -> CORE_API_TICK_POLICY;
            case POSTGRESQL, MYSQL, MARIADB -> SHARED_SQL_TICK_POLICY;
            case SQLITE -> LOCAL_FALLBACK_TICK_POLICY;
        };
    }

    public static boolean writeReady(DatabaseService.StorageBackend backend, boolean storageWriteAuthorityReady) {
        if (backend == null || !storageWriteAuthorityReady) {
            return false;
        }
        return switch (backend) {
            case CORE_API, POSTGRESQL, MYSQL, MARIADB -> true;
            case SQLITE -> false;
        };
    }

    public static String writePolicy(DatabaseService.StorageBackend backend) {
        if (backend == null) {
            return LOCAL_FALLBACK_WRITE_POLICY;
        }
        return switch (backend) {
            case CORE_API -> CORE_API_WRITE_POLICY;
            case POSTGRESQL, MYSQL, MARIADB -> SHARED_SQL_WRITE_POLICY;
            case SQLITE -> LOCAL_FALLBACK_WRITE_POLICY;
        };
    }
}
