package kr.seungmin.satisskyfactory.runtime;

import kr.seungmin.satisskyfactory.database.DatabaseService;

public final class SatisDatabaseRuntime {
    public String settingsFingerprint(DatabaseService.Settings settings) {
        if (settings == null) {
            return "";
        }
        String fallbackOrder = settings.fallbackOrder() == null
                ? ""
                : settings.fallbackOrder().stream()
                .map(backend -> backend == null ? "" : backend.name())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return String.join("|",
                settings.backend() == null ? "" : settings.backend().name(),
                safe(settings.sqliteFileName()),
                safe(settings.jdbcUrl()),
                safe(settings.postgresqlJdbcUrl()),
                safe(settings.mysqlJdbcUrl()),
                safe(settings.mariadbJdbcUrl()),
                safe(settings.username()),
                Integer.toHexString(safe(settings.password()).hashCode()),
                Integer.toString(settings.maxPoolSize()),
                Long.toString(settings.connectionTimeoutMillis()),
                backendSettingsFingerprint(settings.postgresqlSettings()),
                backendSettingsFingerprint(settings.mysqlSettings()),
                backendSettingsFingerprint(settings.mariadbSettings()),
                Boolean.toString(settings.fallbackEnabled()),
                fallbackOrder
        );
    }

    public String appendFallbackReason(String currentReason, String nextReason) {
        if (nextReason == null || nextReason.isBlank() || "none".equalsIgnoreCase(nextReason)) {
            return normalizeReason(currentReason);
        }
        String current = normalizeReason(currentReason);
        if ("none".equalsIgnoreCase(current)) {
            return nextReason;
        }
        if (current.contains(nextReason)) {
            return current;
        }
        return current + ";" + nextReason;
    }

    private String backendSettingsFingerprint(DatabaseService.BackendSettings settings) {
        if (settings == null) {
            return "";
        }
        return String.join(":",
                safe(settings.username()),
                Integer.toHexString(safe(settings.password()).hashCode()),
                Integer.toString(settings.maxPoolSize()),
                Long.toString(settings.connectionTimeoutMillis())
        );
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "none" : reason;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
