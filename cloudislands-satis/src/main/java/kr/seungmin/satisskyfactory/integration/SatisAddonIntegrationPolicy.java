package kr.seungmin.satisskyfactory.integration;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy;

public final class SatisAddonIntegrationPolicy {
    public static final String ADDON_ID = "cloudislands-satis";
    public static final String RECOMMENDED_MODE = "EXTERNAL_ADDON";
    public static final String COMPATIBLE_BUILT_IN_MODE = "BUILT_IN_COMPATIBLE";
    public static final String DISABLED_MODE = "DISABLED";
    public static final String API_POLICY = "cloudislands-api-only-no-superiorskyblock2-runtime";
    public static final String API_SURFACE_POLICY = "island-member-permission-location-upgrade-values-through-cloudislands-api-or-addon-spi";
    public static final String FORBIDDEN_DIRECT_ACCESS_POLICY = "no-direct-cloudislands-storage-runtime-or-world-owner-access";
    public static final String CLOUDISLANDS_REQUIRED_POLICY = "cloudislands-api-required-no-standalone-island-runtime";
    public static final String API_RESOLUTION_POLICY = "bootstrap-or-services-manager";
    public static final String MISSING_API_BEHAVIOR = "disable-plugin-clear-features-register-no-components";
    public static final String RUNTIME_HARD_DEPEND_PLUGIN = "CloudIslands";
    public static final String STANDALONE_ISLAND_MANAGEMENT = "false";
    public static final String ROOT_GATE_POLICY = "addons.cloudislands-satis.enabled&&satis.enabled";
    public static final String FEATURE_GATE_POLICY = "root-disabled-forces-every-feature-off-child-disabled-skips-commands-gui-listeners-tickers-writes";
    public static final String FEATURE_DEPENDENCY_POLICY = "child-feature-enabled-in-config-still-blocks-when-required-parent-feature-is-disabled";
    public static final String DATA_AUTHORITY = "core-api-table-key-value-or-shared-database";
    public static final String NODE_MOVE_POLICY = "save-on-source-node-restore-on-target-node-by-island-uuid";
    public static final String REMOVAL_POLICY = "addon-removed-or-disabled-never-blocks-cloudislands-base-island-lifecycle";
    public static final String DATA_RETENTION_POLICY = "disabled-or-removed-preserves-addon-state-by-island-uuid";
    public static final String FEATURE_DISABLE_DATA_POLICY = "disabled-feature-preserves-existing-state-and-skips-new-runtime-writes";
    public static final String REENABLE_POLICY = "reenable-restores-state-from-shared-backend-by-island-uuid";
    public static final String NO_AUTOMATIC_DELETE_POLICY = "no-automatic-delete-on-disable-remove-or-feature-off";
    public static final String STATE_KEY_POLICY = "island-uuid-stable-node-world-cell-volatile";
    public static final String PERSISTENT_ID_AUTHORITY = "cloudislands-island-uuid";
    public static final String FORBIDDEN_PERSISTENT_OWNER_KEYS = "server-name,world-name,player-uuid";
    public static final String VOLATILE_PLACEMENT_POLICY = "active-node-world-center-are-remap-targets-not-state-owners";

    private static final List<String> SUPPORTED_MODES = List.of(
            RECOMMENDED_MODE,
            COMPATIBLE_BUILT_IN_MODE,
            DISABLED_MODE
    );

    private static final List<String> FEATURE_GATES = List.of(
            "commands",
            "machines",
            "resource-nodes",
            "gui",
            "storage",
            "market",
            "contracts",
            "research",
            "maintenance",
            "placeholders",
            "migration",
            "addon-state",
            "route-events",
            "members",
            "permissions",
            "level-values",
            "warps",
            "biomes",
            "chat",
            "templates"
    );

    private static final List<String> REQUIRED_API_DOMAINS = List.of(
            "island-query",
            "member-query",
            "permission-query",
            "active-location-query",
            "upgrade-value-query",
            "runtime-route-query",
            "lifecycle-events",
            "addon-state-storage"
    );

    private static final List<String> FORBIDDEN_DIRECT_ACCESS_TARGETS = List.of(
            "SuperiorSkyblock2-runtime-api",
            "CloudIslands-core-service-internals",
            "CloudIslands-storage-implementation",
            "Paper-world-name-as-state-owner",
            "Island-node-name-as-state-owner"
    );

    private static final List<String> LIFECYCLE_EVENTS = List.of(
            "island-created",
            "island-pre-activate",
            "island-activated",
            "island-deactivation-request",
            "island-deactivated",
            "island-migration-request",
            "island-migrated",
            "island-member-changed",
            "island-permission-changed",
            "island-level-recalculate",
            "island-worth-changed"
    );

    private static final Map<String, String> OPERATION_SCENARIOS = Map.of(
            "built-in-mode", "cloudislands-installs-one-plugin-satis-features-start-only-when-root-and-child-feature-gates-enable-them",
            "addon-plugin-mode", "cloudislands-plus-cloudislands-satis-jar-registers-through-addon-registry-and-uses-cloudislands-api-only",
            "partial-feature-mode", "enabled-features-register-runtime-components-disabled-features-register-no-commands-gui-listeners-placeholders-tasks-or-writes",
            "legacy-migration-mode", "superiorskyblock2-backed-satis-data-is-rebound-to-cloudislands-api-island-member-permission-location-upgrade-state",
            "a-b-island-node-move", "satis-state-follows-cloudislands-island-uuid-through-shared-state-when-island-moves-from-node-a-to-node-b",
            "addon-removed-mode", "cloudislands-core-boots-and-base-island-lifecycle-continues-when-satis-addon-jar-is-absent"
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

    private static final Map<String, String> FEATURE_OFF_RUNTIME_BLOCKS = Map.of(
            "machines", "machine-place-machine-tick-machine-gui-machine-placeholder",
            "resource-nodes", "resource-node-spawn-resource-node-tick-resource-node-gui",
            "contracts", "contract-command-contract-gui-contract-event-writes",
            "market", "market-command-market-gui-market-storage-writes",
            "missions", "mission-command-mission-gui-mission-event-handlers",
            "placeholders", "placeholder-registration-placeholder-refresh",
            "gui", "satis-menu-registration-and-open-handlers",
            "lifecycle", "satis-lifecycle-event-consumers",
            "storage", "satis-state-write-paths"
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
            "no-superiorskyblock2-runtime-dependency",
            "state-survives-a-node-to-b-node-island-move",
            "base-cloudislands-functions-survive-satis-disable-or-addon-removal"
    );

    private static final Map<String, String> REQUIRED_SCENARIOS = Map.of(
            "a-b-node-move", "factory-upgrade-menu-progress-state-restores-on-target-node-from-shared-state",
            "satis-disabled", "base-cloudislands-create-visit-protect-save-restore-continues-without-satis-runtime-components",
            "partial-features", "disabled-feature-registers-no-command-gui-listener-task-or-write-path",
            "feature-off-data-retention", "existing-feature-state-is-preserved-and-not-deleted-while-feature-is-off",
            "addon-reenable", "previous-addon-state-is-reloaded-from-shared-storage-by-island-uuid",
            "island-id-storage", "satis-state-uses-cloudislands-island-uuid-as-persistent-owner-key",
            "volatile-placement", "server-world-and-center-are-remapped-runtime-placement-not-persistent-identity",
            "api-surface", "island-member-permission-location-upgrade-data-come-from-cloudislands-api-or-addon-spi",
            "no-direct-internals", "satis-does-not-read-cloudislands-storage-runtime-internals-or-node-ownership-directly",
            "external-addon", "cloudislands-boots-without-satis-jar-and-discovers-satis-through-addon-api-when-installed",
            "missing-cloudislands-api", "satis-runtime-does-not-start-and-registers-no-commands-listeners-tickers-or-writers",
            "no-superiorskyblock2", "legacy-skyblock-calls-are-replaced-by-cloudislands-api-or-addon-spi"
    );

    private SatisAddonIntegrationPolicy() {
    }

    public static List<String> supportedModes() {
        return SUPPORTED_MODES;
    }

    public static List<String> featureGates() {
        return FEATURE_GATES;
    }

    public static List<String> requiredApiDomains() {
        return REQUIRED_API_DOMAINS;
    }

    public static List<String> forbiddenDirectAccessTargets() {
        return FORBIDDEN_DIRECT_ACCESS_TARGETS;
    }

    public static List<String> lifecycleEvents() {
        return LIFECYCLE_EVENTS;
    }

    public static Map<String, String> requiredScenarios() {
        return REQUIRED_SCENARIOS;
    }

    public static Map<String, String> operationScenarios() {
        return SatisIntegrationPolicy.operationScenarios();
    }

    public static List<String> completionCriteria() {
        return SatisIntegrationPolicy.completionCriteria();
    }

    public static String operationScenarioSummary() {
        return SatisIntegrationPolicy.operationScenarioSummary();
    }

    public static List<String> recommendedModeReasons() {
        return SatisIntegrationPolicy.recommendedModeReasons();
    }

    public static String recommendedModeReasonSummary() {
        return SatisIntegrationPolicy.recommendedModeReasonSummary();
    }

    public static Map<String, String> componentBoundaries() {
        return SatisIntegrationPolicy.componentBoundaries();
    }

    public static String componentBoundarySummary() {
        return SatisIntegrationPolicy.componentBoundarySummary();
    }

    public static Map<String, String> featureOffRuntimeBlocks() {
        return SatisIntegrationPolicy.featureOffRuntimeBlocks();
    }

    public static String featureOffRuntimeBlockSummary() {
        return SatisIntegrationPolicy.featureOffRuntimeBlockSummary();
    }

    public static List<String> nodeMoveRemapSteps() {
        return SatisIntegrationPolicy.nodeMoveRemapSteps();
    }

    public static String nodeMoveRemapStepSummary() {
        return SatisIntegrationPolicy.nodeMoveRemapStepSummary();
    }

    public static List<String> failureRecoverySteps() {
        return SatisIntegrationPolicy.failureRecoverySteps();
    }

    public static String failureRecoveryStepSummary() {
        return SatisIntegrationPolicy.failureRecoveryStepSummary();
    }

    public static List<String> addonReconnectSteps() {
        return SatisIntegrationPolicy.addonReconnectSteps();
    }

    public static String addonReconnectStepSummary() {
        return SatisIntegrationPolicy.addonReconnectStepSummary();
    }

    public static String completionCriteriaSummary() {
        return SatisIntegrationPolicy.completionCriteriaSummary();
    }

    public static boolean modeSupported(String mode) {
        return SUPPORTED_MODES.contains(mode);
    }

    public static boolean featureGateRequired(String feature) {
        return FEATURE_GATES.contains(feature);
    }

    public static boolean apiDomainRequired(String domain) {
        return REQUIRED_API_DOMAINS.contains(domain);
    }

    public static boolean directAccessForbidden(String target) {
        return FORBIDDEN_DIRECT_ACCESS_TARGETS.contains(target);
    }

    public static boolean lifecycleEventRequired(String event) {
        return LIFECYCLE_EVENTS.contains(event);
    }

    private static String summary(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        values.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }
}
