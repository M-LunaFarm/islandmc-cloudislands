package kr.seungmin.satisskyfactory.integration;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy;

public final class SatisAddonIntegrationPolicy {
    public static final String ADDON_ID = "cloudislands-satis";
    public static final String RECOMMENDED_MODE = "EXTERNAL_ADDON";
    public static final String COMPATIBLE_BUILT_IN_MODE = "BUILT_IN_COMPATIBLE";
    public static final String DISABLED_MODE = "DISABLED";
    public static final String SUPPORTED_PACKAGING_MODES = "external-plugin,built-in-feature-pack,built-in-compatible";
    public static final String BUILT_IN_COMPATIBLE_BOUNDARY_POLICY = "built-in-compatible-uses-cloudislands-api-and-addon-gates-no-standalone-runtime";
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
    public static final String TOPOLOGY_PRIVACY_POLICY = "player-facing-satis-output-hides-physical-node-server-world-cell-and-route-identifiers";
    public static final String PLAYER_VISIBLE_TOPOLOGY_POLICY = "show-logical-island-only-never-island-node-or-shard-placement";
    public static final String INTERNAL_TOPOLOGY_FIELDS = "active-node,source-node,target-node,server-name,world-name,cell,route-ticket,backend-storage-key";
    public static final String OFFICIAL_FEATURE_PACK_POLICY = "optional-content-layer-not-cloudislands-core-lifecycle-owner";
    public static final String ADDON_SPI_POLICY = "same-cloudislands-addon-spi-for-external-plugin-and-built-in-feature-pack";
    public static final String CONTENT_LAYER_POLICY = "cloudislands-satis-owns-optional-machines-resource-nodes-contracts-research-market-and-placeholders";
    public static final String CORE_BOUNDARY_POLICY = "cloudislands-core-owns-island-lifecycle-routing-storage-protection-and-public-api";

    private static final List<String> SUPPORTED_MODES = List.of(
            RECOMMENDED_MODE,
            COMPATIBLE_BUILT_IN_MODE,
            DISABLED_MODE
    );

    private static final List<String> FEATURE_GATES = List.of(
            "commands",
            "machines",
            "factories",
            "generators",
            "upgrades",
            "missions",
            "resource-nodes",
            "gui",
            "menus",
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
            "island-pre-create",
            "island-created",
            "island-pre-activate",
            "island-activation-requested",
            "island-activated",
            "island-deactivation-requested",
            "island-deactivated",
            "island-migration-requested",
            "island-migrated",
            "island-delete-requested",
            "island-delete-backup-failed",
            "island-restore-requested",
            "island-restored",
            "island-reset",
            "island-recovery-required",
            "island-repaired",
            "island-runtime-changed",
            "island-pre-visit",
            "island-visited",
            "island-invite-changed",
            "island-renamed",
            "island-member-joined",
            "island-member-left",
            "island-member-changed",
            "island-role-changed",
            "island-role-catalog-changed",
            "island-ownership-changed",
            "island-access-changed",
            "island-visitor-ban-changed",
            "island-visitor-kicked",
            "island-flag-changed",
            "island-permission-checked",
            "island-permission-changed",
            "island-chat-sent",
            "island-blocks-changed",
            "island-block-value-changed",
            "island-mission-progress",
            "island-mission-completed",
            "island-level-recalculate",
            "island-worth-changed",
            "island-upgrade-changed",
            "island-limit-changed",
            "island-biome-changed",
            "island-home-changed",
            "island-warp-created",
            "island-warp-deleted",
            "island-warp-changed",
            "island-bank-changed",
            "island-snapshot-requested",
            "island-snapshot-created",
            "island-template-changed",
            "node-state-changed",
            "route-ticket-created",
            "route-session-published",
            "route-ticket-consumed",
            "route-ticket-failed",
            "route-ticket-cleared",
            "addon-state-changed",
            "core-cache-cleared",
            "core-reloaded"
    );

    private static final Map<String, String> REQUIRED_SCENARIOS = Map.ofEntries(
            Map.entry("a-b-node-move", "factory-upgrade-menu-progress-state-restores-on-target-node-from-shared-state"),
            Map.entry("a-b-node-move-addon-disabled", "cloudislands-moves-island-from-node-a-to-node-b-without-satis-runtime-and-reconnects-shared-addon-state-when-reenabled"),
            Map.entry("satis-disabled", "base-cloudislands-create-visit-protect-save-restore-continues-without-satis-runtime-components"),
            Map.entry("partial-features", "disabled-feature-registers-no-command-gui-listener-task-or-write-path"),
            Map.entry("feature-off-data-retention", "existing-feature-state-is-preserved-and-not-deleted-while-feature-is-off"),
            Map.entry("addon-reenable", "previous-addon-state-is-reloaded-from-shared-storage-by-island-uuid"),
            Map.entry("island-id-storage", "satis-state-uses-cloudislands-island-uuid-as-persistent-owner-key"),
            Map.entry("volatile-placement", "server-world-and-center-are-remapped-runtime-placement-not-persistent-identity"),
            Map.entry("api-surface", "island-member-permission-location-upgrade-data-come-from-cloudislands-api-or-addon-spi"),
            Map.entry("no-direct-internals", "satis-does-not-read-cloudislands-storage-runtime-internals-or-node-ownership-directly"),
            Map.entry("external-addon", "cloudislands-boots-without-satis-jar-and-discovers-satis-through-addon-api-when-installed"),
            Map.entry("missing-cloudislands-api", "satis-runtime-does-not-start-and-registers-no-commands-listeners-tickers-or-writers"),
            Map.entry("node-crash-recovery", "expired-heartbeat-or-fencing-mismatch-blocks-duplicate-tick-and-replays-last-confirmed-state-on-target-node"),
            Map.entry("addon-jar-removed", "cloudislands-base-island-lifecycle-boots-without-addon-metadata-or-runtime-jar-and-reconnects-existing-addon-state-when-reinstalled"),
            Map.entry("no-superiorskyblock2", "legacy-skyblock-calls-are-replaced-by-cloudislands-api-or-addon-spi")
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

    public static String forbiddenDirectAccessTargetsCsv() {
        return String.join(",", FORBIDDEN_DIRECT_ACCESS_TARGETS);
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

    public static Map<String, String> officialFeaturePackBoundaries() {
        return SatisIntegrationPolicy.officialFeaturePackBoundaries();
    }

    public static String officialFeaturePackBoundarySummary() {
        return SatisIntegrationPolicy.officialFeaturePackBoundarySummary();
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

    public static String modeRuntimeBoundary(String mode) {
        String normalized = mode == null ? "" : mode.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        if (DISABLED_MODE.equals(normalized)) {
            return "disabled-no-runtime-components";
        }
        if (COMPATIBLE_BUILT_IN_MODE.equals(normalized)) {
            return BUILT_IN_COMPATIBLE_BOUNDARY_POLICY;
        }
        return CLOUDISLANDS_REQUIRED_POLICY;
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
        new java.util.TreeMap<>(values).forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }
}
