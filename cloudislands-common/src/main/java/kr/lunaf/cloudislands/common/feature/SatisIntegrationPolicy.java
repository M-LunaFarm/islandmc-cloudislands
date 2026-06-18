package kr.lunaf.cloudislands.common.feature;

import java.util.List;
import java.util.Map;

public final class SatisIntegrationPolicy {
    private static final Map<String, String> OPERATION_SCENARIOS = Map.ofEntries(
        Map.entry("built-in-mode", "cloudislands-installs-one-plugin-satis-features-start-only-when-root-and-child-feature-gates-enable-them"),
        Map.entry("addon-plugin-mode", "cloudislands-plus-cloudislands-satis-jar-registers-through-addon-registry-and-uses-cloudislands-api-only"),
        Map.entry("partial-feature-mode", "enabled-features-register-runtime-components-disabled-features-register-no-commands-gui-listeners-placeholders-tasks-or-writes"),
        Map.entry("legacy-migration-mode", "superiorskyblock2-backed-satis-data-is-rebound-to-cloudislands-api-island-member-permission-location-upgrade-state"),
        Map.entry("a-b-island-node-move", "satis-state-follows-cloudislands-island-uuid-through-shared-state-when-island-moves-from-node-a-to-node-b"),
        Map.entry("a-b-node-move-addon-disabled-mode", "cloudislands-moves-island-from-node-a-to-node-b-even-when-satis-runtime-is-disabled-or-addon-jar-is-absent-and-reconnects-state-after-reenable"),
        Map.entry("addon-removed-mode", "cloudislands-core-boots-and-base-island-lifecycle-continues-when-satis-addon-jar-is-absent"),
        Map.entry("setup-database-mode", "operator-selects-core-api-postgresql-mysql-mariadb-or-safe-fallback-through-setup-database-config"),
        Map.entry("bulk-table-state-mode", "satis-uses-table-key-value-bulk-save-and-load-before-flattened-addon-state-fallback"),
        Map.entry("command-list-mode", "factory-and-admin-command-list-render-one-command-per-line-with-page-navigation"),
        Map.entry("velocity-forwarding-mode", "paper-island-nodes-trust-player-identity-only-through-velocity-modern-forwarding-and-shared-secret"),
        Map.entry("topology-private-mode", "players-use-logical-island-commands-and-never-see-island-node-server-world-cell-or-route-ticket-identifiers"),
        Map.entry("player-island-surface-mode", "my-island-other-island-ranking-visit-settings-and-warps-stay-logical-and-route-through-core-api"),
        Map.entry("infrastructure-authority-mode", "postgresql-is-authoritative-redis-is-cache-lock-stream-queue-helper-object-storage-holds-portable-bundles"),
        Map.entry("soft-full-create-mode", "island-1-soft-full-new-create-skips-to-ready-island-2-without-player-command-change")
    );

    private static final List<String> RECOMMENDED_MODE_REASONS = List.of(
        "core-focuses-on-distributed-island-platform",
        "servers-that-do-not-want-satis-do-not-install-the-addon",
        "satis-failures-are-isolated-from-create-route-protect-save",
        "other-addons-can-use-the-same-extension-shape",
        "satis-can-have-its-own-release-cycle"
    );

    private static final Map<String, String> COMPONENT_BOUNDARIES = Map.of(
        "velocity-plugin", "no-direct-satis-handler-global-command-routing-only-when-needed",
        "paper-agent-plugin", "cloudislands-api-addon-lifecycle-current-island-world-center-member-permission-query",
        "cloudislands-satis-addon", "machines-resource-nodes-storage-gui-placeholders-cloudislands-api-only",
        "core-api-service", "stores-addon-metadata-and-state-without-knowing-satis-business-logic"
    );

    private static final Map<String, String> FEATURE_OFF_RUNTIME_BLOCKS = Map.ofEntries(
        Map.entry("machines", "machine-place-machine-tick-machine-gui-machine-placeholder"),
        Map.entry("factories", "factory-command-factory-gui-factory-tick-factory-storage-writes"),
        Map.entry("generators", "generator-command-generator-upgrade-listeners-generator-drop-modifiers"),
        Map.entry("upgrades", "upgrade-command-upgrade-gui-upgrade-cost-writes-upgrade-effects"),
        Map.entry("resource-nodes", "resource-node-spawn-resource-node-tick-resource-node-gui"),
        Map.entry("contracts", "contract-command-contract-gui-contract-event-writes"),
        Map.entry("market", "market-command-market-gui-market-storage-writes"),
        Map.entry("missions", "mission-command-mission-gui-mission-event-handlers"),
        Map.entry("research", "research-command-research-gui-research-progress-writes"),
        Map.entry("maintenance", "maintenance-task-registration-repair-gui-and-operator-write-paths"),
        Map.entry("migration", "migration-scan-dryrun-import-rollback-and-legacy-provider-check-commands"),
        Map.entry("menus", "satis-menu-registration-and-open-handlers"),
        Map.entry("commands", "satis-command-registration-and-tab-completion"),
        Map.entry("placeholders", "placeholder-registration-placeholder-refresh"),
        Map.entry("gui", "satis-menu-registration-and-open-handlers"),
        Map.entry("lifecycle", "satis-lifecycle-event-consumers"),
        Map.entry("storage", "satis-state-write-paths-dirty-save-and-storage-backed-gui-actions"),
        Map.entry("addon-state", "core-api-state-writer-table-key-value-bulk-save-and-load"),
        Map.entry("route-events", "route-session-and-node-move-event-consumers"),
        Map.entry("members", "member-query-dependent-commands-gui-actions-and-placeholders"),
        Map.entry("permissions", "permission-query-dependent-actions-and-gui-controls"),
        Map.entry("level-values", "level-worth-placeholders-gui-values-and-recalculation-hooks"),
        Map.entry("warps", "warp-command-gui-and-location-write-paths"),
        Map.entry("biomes", "biome-command-gui-and-biome-change-writes"),
        Map.entry("chat", "island-chat-routing-formatting-and-event-handlers"),
        Map.entry("templates", "template-command-gui-and-template-selection-writes")
    );

    private static final Map<String, String> STATE_STORAGE_CONFIG = Map.of(
        "core-api-table-key-value", "recommended-portable-addon-state-authority-for-multi-node-island-moves",
        "shared-directory", "allowed-only-when-mounted-identically-on-every-island-node",
        "sqlite-file", "single-node-or-shared-directory-mode-only-not-node-local-for-a-b-moves",
        "external-database", "allowed-when-addon-owns-schema-and-keeps-island-uuid-as-primary-scope"
    );

    private static final Map<String, String> PLAYER_EXPERIENCE_BOUNDARIES = Map.of(
        "player-visible-mode", "addon-features-feel-built-in-when-enabled",
        "operator-visible-mode", "operator-manages-cloudislands-satis-through-addon-config-and-feature-gates",
        "command-surface", "commands-appear-only-when-root-addon-and-command-feature-are-enabled",
        "gui-surface", "menus-appear-only-when-root-addon-and-gui-or-menu-feature-are-enabled",
        "disabled-surface", "disabled-or-removed-addon-leaves-no-player-facing-satis-entrypoints"
    );

    private static final Map<String, String> OFFICIAL_FEATURE_PACK_BOUNDARIES = Map.of(
        "platform-layer", "cloudislands-core-owns-island-lifecycle-routing-storage-protection-and-public-api",
        "content-layer", "cloudislands-satis-owns-optional-machines-resource-nodes-contracts-research-market-and-placeholders",
        "coupling-rule", "satis-uses-cloudislands-api-and-addon-spi-without-core-internal-repository-access",
        "release-boundary", "satis-can-ship-as-official-addon-or-built-in-module-with-the-same-feature-gates",
        "failure-boundary", "satis-failure-or-removal-must-not-stop-core-island-create-route-save-restore"
    );

    private static final List<String> NODE_MOVE_REMAP_STEPS = List.of(
        "source-node-save-addon-state-by-island-uuid",
        "cloudislands-deactivate-and-store-portable-island-bundle",
        "core-selects-target-island-node",
        "target-node-activates-island",
        "addon-reads-active-world-center-from-cloudislands-api",
        "addon-remaps-volatile-world-and-cell-references",
        "addon-resumes-from-shared-state-without-player-visible-node-change"
    );

    private static final List<String> FAILURE_RECOVERY_STEPS = List.of(
        "heartbeat-expired",
        "fencing-token-checked",
        "island-marked-recovery-required-or-quarantined",
        "last-confirmed-addon-state-selected",
        "target-node-reactivates-after-storage-verification",
        "satis-ticker-starts-once",
        "duplicate-writes-rejected-by-current-island-uuid-and-fencing-context"
    );

    private static final List<String> ADDON_RECONNECT_STEPS = List.of(
        "addon-jar-removed-core-still-boots",
        "addon-metadata-missing-does-not-block-island-load",
        "addon-state-preserved-in-shared-storage",
        "addon-jar-reinstalled",
        "addon-registers-through-cloudislands-addon-service",
        "state-reconnected-by-island-uuid"
    );

    private static final List<String> COMPLETION_CRITERIA = List.of(
        "satis-features-run-with-cloudislands-island-lifecycle",
        "root-config-can-disable-all-satis-runtime-components",
        "major-features-have-independent-feature-gates",
        "chosen-built-in-or-addon-structure-is-visible-in-code",
        "addon-jar-and-cloudislands-addon-descriptor-ship-as-separate-addon-bundle-artifacts",
        "no-superiorskyblock2-runtime-dependency",
        "setup-database-supports-core-api-postgresql-mysql-mariadb-and-safe-fallback",
        "table-key-value-bulk-save-api-covers-global-and-island-addon-state",
        "command-list-renders-one-line-per-command-with-paging",
        "velocity-modern-forwarding-and-forwarding-secret-are-required-for-paper-island-node-trust",
        "player-facing-output-hides-physical-island-node-server-world-cell-and-route-identifiers",
        "my-island-other-island-ranking-visit-settings-and-warps-use-logical-core-api-backed-flows",
        "postgresql-redis-and-object-storage-authority-boundaries-are-explicit-and-tested",
        "portable-island-bundles-require-manifest-checksums-safe-restore-and-quarantine-fallback",
        "island-create-home-visit-and-soft-full-island-1-to-island-2-flows-are-pinned",
        "state-survives-a-node-to-b-node-island-move",
        "state-survives-a-node-to-b-node-move-while-satis-is-disabled-or-removed",
        "base-cloudislands-functions-survive-satis-disable-or-addon-removal"
    );

    private SatisIntegrationPolicy() {}

    public static Map<String, String> operationScenarios() {
        return OPERATION_SCENARIOS;
    }

    public static String operationScenarioSummary() {
        return summary(OPERATION_SCENARIOS);
    }

    public static List<String> recommendedModeReasons() {
        return RECOMMENDED_MODE_REASONS;
    }

    public static String recommendedModeReasonSummary() {
        return String.join(",", RECOMMENDED_MODE_REASONS);
    }

    public static Map<String, String> componentBoundaries() {
        return COMPONENT_BOUNDARIES;
    }

    public static String componentBoundarySummary() {
        return summary(COMPONENT_BOUNDARIES);
    }

    public static Map<String, String> featureOffRuntimeBlocks() {
        return FEATURE_OFF_RUNTIME_BLOCKS;
    }

    public static String featureOffRuntimeBlockSummary() {
        return summary(FEATURE_OFF_RUNTIME_BLOCKS);
    }

    public static Map<String, String> stateStorageConfig() {
        return STATE_STORAGE_CONFIG;
    }

    public static String stateStorageConfigSummary() {
        return summary(STATE_STORAGE_CONFIG);
    }

    public static Map<String, String> playerExperienceBoundaries() {
        return PLAYER_EXPERIENCE_BOUNDARIES;
    }

    public static String playerExperienceBoundarySummary() {
        return summary(PLAYER_EXPERIENCE_BOUNDARIES);
    }

    public static Map<String, String> officialFeaturePackBoundaries() {
        return OFFICIAL_FEATURE_PACK_BOUNDARIES;
    }

    public static String officialFeaturePackBoundarySummary() {
        return summary(OFFICIAL_FEATURE_PACK_BOUNDARIES);
    }

    public static List<String> nodeMoveRemapSteps() {
        return NODE_MOVE_REMAP_STEPS;
    }

    public static String nodeMoveRemapStepSummary() {
        return String.join(">", NODE_MOVE_REMAP_STEPS);
    }

    public static List<String> failureRecoverySteps() {
        return FAILURE_RECOVERY_STEPS;
    }

    public static String failureRecoveryStepSummary() {
        return String.join(">", FAILURE_RECOVERY_STEPS);
    }

    public static List<String> addonReconnectSteps() {
        return ADDON_RECONNECT_STEPS;
    }

    public static String addonReconnectStepSummary() {
        return String.join(">", ADDON_RECONNECT_STEPS);
    }

    public static List<String> completionCriteria() {
        return COMPLETION_CRITERIA;
    }

    public static String completionCriteriaSummary() {
        return String.join(",", COMPLETION_CRITERIA);
    }

    private static String summary(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        new java.util.TreeMap<>(values).forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }
}
