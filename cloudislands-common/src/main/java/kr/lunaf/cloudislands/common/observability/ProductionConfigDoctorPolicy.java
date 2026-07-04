package kr.lunaf.cloudislands.common.observability;

import java.util.List;

public final class ProductionConfigDoctorPolicy {
    public static final String CONTRACT = "config-doctor-runs-at-startup-and-ciadmin-doctor-for-production-risk-checks";
    public static final List<String> CHECKS = List.of(
        "token-empty",
        "mtls-header-trusted-proxy-missing",
        "redis-public-bind-risk",
        "sql-in-memory-production-risk",
        "object-storage-unavailable",
        "default-node-id",
        "duplicate-node-id",
        "velocity-server-name-mismatch",
        "fallback-server-missing",
        "unsupported-paper-version"
    );

    private ProductionConfigDoctorPolicy() {
    }

    public static String checkSummary() {
        return String.join(",", CHECKS);
    }

    public static boolean requiredCheck(String check) {
        return check != null && CHECKS.contains(check);
    }
}
