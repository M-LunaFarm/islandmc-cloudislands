package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class AdminDiagnosticCommands {
    private final MessageService messages;
    private final DatabaseService database;
    private final Supplier<Map<String, String>> integrationMetadata;
    private final Supplier<Map<String, String>> addonState;

    AdminDiagnosticCommands(
            MessageService messages,
            DatabaseService database,
            Supplier<Map<String, String>> integrationMetadata,
            Supplier<Map<String, String>> addonState
    ) {
        this.messages = messages;
        this.database = database;
        this.integrationMetadata = integrationMetadata;
        this.addonState = addonState;
    }

    void showDoctor(CommandSender sender) {
        messages.sendRaw(sender, "admin-doctor-title");
        Map<String, String> state = diagnosticState();
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("runtime", runtimeStatus(state));
        summary.put("database", databaseStatus(state));
        summary.put("addon-state", addonStateStatus(state));
        summary.put("dirty-save", dirtySaveStatus(state));
        summary.put("routes", routeStatus(state));
        summary.put("lifecycle", firstNonBlank(state.get("last-lifecycle-status"), "unknown"));
        summary.put("config-validation", configValidationStatus(state));
        summary.put("blocked-components", firstNonBlank(state.get("runtime-blocked-components"), "none"));
        summary.put("operator-action", doctorAction(state));
        printSection(sender, summary);
    }

    void showDatabase(CommandSender sender) {
        messages.sendRaw(sender, "admin-database-title");
        Map<String, String> state = diagnosticState();
        printSelected(sender, state, List.of(
                "local.database-active-backend",
                "database-active-backend",
                "database-effective-backend-status",
                "local.database-attempted-backends",
                "database-attempted-backends",
                "local.database-fallback-reason",
                "database-fallback-reason",
                "database-fallback-status",
                "database-fallback-active",
                "database-fallback-risk",
                "database-fallback-production-safe",
                "database-fallback-ready-chain",
                "database-fallback-ready-chain-risk",
                "database-fallback-ready-chain-production-safe",
                "database-fallback-operator-remediation",
                "database-cache-backend",
                "local.database-cache-backend",
                "database-cache-description",
                "local.database-cache-description",
                "database-core-api-authority-ready",
                "database-core-api-local-cache-writes-enabled",
                "database-core-api-fallback-active",
                "database-node-local-cache-active",
                "local.database-description"
        ));
    }

    void showRuntime(CommandSender sender) {
        messages.sendRaw(sender, "admin-runtime-title");
        Map<String, String> state = diagnosticState();
        printSelected(sender, state, List.of(
                "runtime-addon-status",
                "runtime-feature-pack-runtime-enabled",
                "runtime-feature-pack-block-reason",
                "runtime-cloudislands-api-available",
                "runtime-standalone-island-runtime",
                "runtime-local-node-id",
                "runtime-owner-fence-ready",
                "runtime-owner-fence-tracked-islands",
                "runtime-owner-fence-local-active-islands",
                "runtime-tick-authority-policy",
                "runtime-write-authority-policy",
                "runtime-data-writes-enabled",
                "data-write-runtime-owner-fence-ready",
                "runtime-machine-ticker-running",
                "runtime-maintenance-ticker-running",
                "runtime-dirty-save-running",
                "runtime-dirty-save-pending-writes",
                "runtime-dirty-save-last-flush-status",
                "runtime-core-api-state-readiness",
                "runtime-core-api-state-pending-retries",
                "runtime-core-api-state-last-failure"
        ));
    }

    void showRoutes(CommandSender sender) {
        messages.sendRaw(sender, "admin-routes-title");
        Map<String, String> state = diagnosticState();
        printSelected(sender, state, List.of(
                "runtime-route-events-gate",
                "runtime-route-events-status",
                "runtime-route-events-handled",
                "runtime-route-events-blocked",
                "runtime-route-events-publish-failures",
                "runtime-route-events-last-block-reason",
                "route-event-source",
                "route-event-policy",
                "route-event-feature-gate",
                "route-event-state-scope",
                "route-event-player-visible-policy",
                "last-route-player-visible-topology",
                "last-route-ticket-player-visible"
        ));
    }

    void showSupport(CommandSender sender) {
        messages.sendRaw(sender, "admin-support-title");
        Map<String, String> state = diagnosticState();
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("support-command", "/factory admin state");
        summary.put("doctor-status", runtimeStatus(state));
        summary.put("database-status", databaseStatus(state));
        summary.put("runtime-status", firstNonBlank(state.get("runtime-addon-status"), "unknown"));
        summary.put("config-validation", configValidationStatus(state));
        summary.put("feature-blocks", firstNonBlank(state.get("runtime-feature-dependency-blocks"), "none"));
        summary.put("disabled-features", firstNonBlank(state.get("runtime-disabled-features"), "none"));
        summary.put("dirty-save-pending", firstNonBlank(state.get("runtime-dirty-save-pending-writes"), "0"));
        summary.put("route-failures", firstNonBlank(state.get("runtime-route-events-publish-failures"), "0"));
        summary.put("last-core-failure", firstNonBlank(state.get("runtime-core-api-state-last-failure"), "none"));
        summary.put("last-lifecycle", firstNonBlank(state.get("last-lifecycle-operation"), "none"));
        summary.put("operator-action", doctorAction(state));
        printSection(sender, summary);
    }

    private Map<String, String> diagnosticState() {
        Map<String, String> visible = new LinkedHashMap<>();
        if (integrationMetadata != null) {
            try {
                Map<String, String> metadata = integrationMetadata.get();
                if (metadata != null) {
                    visible.putAll(metadata);
                }
            } catch (RuntimeException exception) {
                visible.put("integration-metadata-error", exception.getMessage() == null ? "unknown" : exception.getMessage());
            }
        }
        if (addonState != null) {
            try {
                Map<String, String> state = addonState.get();
                if (state != null) {
                    visible.putAll(state);
                }
            } catch (RuntimeException exception) {
                visible.put("addon-state-error", exception.getMessage() == null ? "unknown" : exception.getMessage());
            }
        }
        if (database != null) {
            visible.put("local.database-active-backend", database.activeBackend().name());
            visible.put("local.database-cache-backend", database.cacheBackend());
            visible.put("local.database-attempted-backends", database.attemptedBackends().stream()
                    .map(DatabaseService.StorageBackend::name)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("none"));
            visible.put("local.database-fallback-reason", database.fallbackReason());
            visible.put("local.database-cache-description", database.cacheDescription());
            visible.put("local.database-description", database.databaseDescription());
        }
        return visible;
    }

    private String runtimeStatus(Map<String, String> state) {
        if (!"0".equals(firstNonBlank(state.get("config-validation-error-count"), "0"))) {
            return "BLOCKED:config-validation-error";
        }
        if (!"true".equalsIgnoreCase(firstNonBlank(state.get("runtime-cloudislands-api-available"), "true"))) {
            return "BLOCKED:cloudislands-api-unavailable";
        }
        String addonStatus = firstNonBlank(state.get("runtime-addon-status"), "");
        if (addonStatus.contains("disabled")) {
            return "BLOCKED:" + addonStatus;
        }
        if ("false".equalsIgnoreCase(firstNonBlank(state.get("runtime-owner-fence-ready"), "true"))) {
            return "BLOCKED:runtime-owner-fence-not-ready";
        }
        if ("false".equalsIgnoreCase(firstNonBlank(state.get("runtime-data-writes-enabled"), "true"))) {
            return "READ_ONLY:data-writes-disabled";
        }
        return "OK";
    }

    private String databaseStatus(Map<String, String> state) {
        String risk = firstNonBlank(state.get("database-fallback-risk"), "");
        if (!risk.isBlank()
                && !"none".equalsIgnoreCase(risk)
                && !"safe".equalsIgnoreCase(risk)
                && !"fallback-disabled".equalsIgnoreCase(risk)) {
            return "WARN:" + risk;
        }
        if ("true".equalsIgnoreCase(firstNonBlank(state.get("database-fallback-active"), "false"))) {
            return "WARN:fallback-active";
        }
        return "OK:" + firstNonBlank(state.get("local.database-active-backend"), firstNonBlank(state.get("database-active-backend"), "unknown"));
    }

    private String addonStateStatus(Map<String, String> state) {
        String status = firstNonBlank(state.get("runtime-addon-state-status"), firstNonBlank(state.get("addon-state-sync-available"), "unknown"));
        if (status.equalsIgnoreCase("available") || status.equalsIgnoreCase("true")) {
            return "OK";
        }
        return "WARN:" + status;
    }

    private String dirtySaveStatus(Map<String, String> state) {
        String running = firstNonBlank(state.get("runtime-dirty-save-running"), "false");
        String pending = firstNonBlank(state.get("runtime-dirty-save-pending-writes"), "0");
        String last = firstNonBlank(state.get("runtime-dirty-save-last-flush-status"), "unknown");
        return "running=" + running + ",pending=" + pending + ",last=" + last;
    }

    private String routeStatus(Map<String, String> state) {
        String status = firstNonBlank(state.get("runtime-route-events-status"), "unknown");
        String failures = firstNonBlank(state.get("runtime-route-events-publish-failures"), "0");
        if (!"0".equals(failures)) {
            return "WARN:" + status + ",failures=" + failures;
        }
        return status;
    }

    private String configValidationStatus(Map<String, String> state) {
        String status = firstNonBlank(state.get("config-validation-status"), "unknown");
        String errors = firstNonBlank(state.get("config-validation-error-count"), "0");
        String warnings = firstNonBlank(state.get("config-validation-warning-count"), "0");
        if (!"0".equals(errors)) {
            return "ERROR:" + firstNonBlank(state.get("config-validation-errors"), errors);
        }
        if (!"0".equals(warnings)) {
            return "WARN:" + firstNonBlank(state.get("config-validation-warnings"), warnings);
        }
        return status;
    }

    private String doctorAction(Map<String, String> state) {
        if (!"0".equals(firstNonBlank(state.get("config-validation-error-count"), "0"))) {
            return "fix-satis-config-validation-errors-before-starting-runtime";
        }
        if (!"0".equals(firstNonBlank(state.get("config-validation-warning-count"), "0"))) {
            return "review-satis-config-validation-warnings";
        }
        if (!"true".equalsIgnoreCase(firstNonBlank(state.get("runtime-cloudislands-api-available"), "true"))) {
            return "restore-cloudislands-api-before-starting-satis-runtime";
        }
        if ("true".equalsIgnoreCase(firstNonBlank(state.get("database-fallback-active"), "false"))) {
            return firstNonBlank(state.get("database-fallback-operator-remediation"), "restore-primary-database-or-configure-shared-fallback");
        }
        if ("false".equalsIgnoreCase(firstNonBlank(state.get("runtime-owner-fence-ready"), "true"))) {
            return "verify-local-node-id-and-cloudislands-runtime-owner-events";
        }
        if (!"0".equals(firstNonBlank(state.get("runtime-core-api-state-pending-retries"), "0"))) {
            return "check-core-api-addon-state-bulk-writer-and-drain-retry-queue";
        }
        return "none";
    }

    private void printSelected(CommandSender sender, Map<String, String> state, List<String> keys) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String key : keys) {
            String value = state.get(key);
            if (value != null && !value.isBlank()) {
                selected.put(key, value);
            }
        }
        if (selected.isEmpty()) {
            selected.put("status", "no-diagnostic-state-available");
        }
        printSection(sender, selected);
    }

    private void printSection(CommandSender sender, Map<String, String> values) {
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> messages.sendRaw(sender, "admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                )));
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? (fallback == null ? "" : fallback) : first;
    }
}
