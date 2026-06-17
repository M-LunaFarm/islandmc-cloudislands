package kr.seungmin.satisskyfactory.integration;

import java.util.List;
import java.util.Map;

public final class SatisAddonIntegrationPolicy {
    public static final String ADDON_ID = "cloudislands-satis";
    public static final String RECOMMENDED_MODE = "EXTERNAL_ADDON";
    public static final String COMPATIBLE_BUILT_IN_MODE = "BUILT_IN_COMPATIBLE";
    public static final String DISABLED_MODE = "DISABLED";
    public static final String API_POLICY = "cloudislands-api-only-no-superiorskyblock2-runtime";
    public static final String ROOT_GATE_POLICY = "addons.cloudislands-satis.enabled&&satis.enabled";
    public static final String FEATURE_GATE_POLICY = "root-disabled-forces-every-feature-off-child-disabled-skips-commands-gui-listeners-tickers-writes";
    public static final String DATA_AUTHORITY = "core-api-table-key-value-or-shared-database";
    public static final String NODE_MOVE_POLICY = "save-on-source-node-restore-on-target-node-by-island-uuid";
    public static final String REMOVAL_POLICY = "addon-removed-or-disabled-never-blocks-cloudislands-base-island-lifecycle";
    public static final String STATE_KEY_POLICY = "island-uuid-stable-node-world-cell-volatile";

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
            "route-events"
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

    private static final Map<String, String> REQUIRED_SCENARIOS = Map.of(
            "a-b-node-move", "factory-upgrade-menu-progress-state-restores-on-target-node-from-shared-state",
            "satis-disabled", "base-cloudislands-create-visit-protect-save-restore-continues-without-satis-runtime-components",
            "partial-features", "disabled-feature-registers-no-command-gui-listener-task-or-write-path",
            "external-addon", "cloudislands-boots-without-satis-jar-and-discovers-satis-through-addon-api-when-installed",
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

    public static List<String> lifecycleEvents() {
        return LIFECYCLE_EVENTS;
    }

    public static Map<String, String> requiredScenarios() {
        return REQUIRED_SCENARIOS;
    }

    public static boolean modeSupported(String mode) {
        return SUPPORTED_MODES.contains(mode);
    }

    public static boolean featureGateRequired(String feature) {
        return FEATURE_GATES.contains(feature);
    }

    public static boolean lifecycleEventRequired(String event) {
        return LIFECYCLE_EVENTS.contains(event);
    }
}
