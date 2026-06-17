package kr.lunaf.cloudislands.common.feature;

import java.util.List;
import java.util.Map;

public final class SatisIntegrationPolicy {
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

    private SatisIntegrationPolicy() {}

    public static String operationScenarioSummary() {
        return summary(OPERATION_SCENARIOS);
    }

    public static String recommendedModeReasonSummary() {
        return String.join(",", RECOMMENDED_MODE_REASONS);
    }

    public static String componentBoundarySummary() {
        return summary(COMPONENT_BOUNDARIES);
    }

    public static String featureOffRuntimeBlockSummary() {
        return summary(FEATURE_OFF_RUNTIME_BLOCKS);
    }

    public static String nodeMoveRemapStepSummary() {
        return String.join(">", NODE_MOVE_REMAP_STEPS);
    }

    public static String failureRecoveryStepSummary() {
        return String.join(">", FAILURE_RECOVERY_STEPS);
    }

    public static String addonReconnectStepSummary() {
        return String.join(">", ADDON_RECONNECT_STEPS);
    }

    public static String completionCriteriaSummary() {
        return String.join(",", COMPLETION_CRITERIA);
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
