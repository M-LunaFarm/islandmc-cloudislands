package kr.lunaf.cloudislands.common.design;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Final design policy for CloudIslands island ownership and node portability.
 */
public final class PortableIslandDesignPolicy {
    public static final String ONE_LINE_DEFINITION = "CloudIslands manages islands as global resources on a Velocity network and dynamically activates them in an Island node pool";
    public static final String GLOBAL_RESOURCE_POLICY = "islands-are-global-resources-not-fixed-to-servers";
    public static final String PORTABLE_BUNDLE_POLICY = "islands-are-saved-as-portable-bundles";
    public static final String NODE_ROLE_POLICY = "island-nodes-are-execution-hosts-not-island-owners";
    public static final String PLACEMENT_POLICY = "node-agnostic-shard-cell-remap";
    public static final String RESTORE_POLICY = "verify-checksum-then-restore-to-current-active-node";

    private static final List<String> CORE_DECISIONS = List.of(
            GLOBAL_RESOURCE_POLICY,
            PORTABLE_BUNDLE_POLICY,
            NODE_ROLE_POLICY,
            PLACEMENT_POLICY,
            RESTORE_POLICY
    );

    private static final List<String> REQUIRED_OUTCOMES = List.of(
            "lobby-can-query-island-info",
            "island-1-can-query-island-info",
            "island-2-can-query-island-info",
            "full-island-1-does-not-block-create-on-island-2",
            "inactive-island-can-open-on-island-2",
            "players-do-not-need-channel-or-node-knowledge",
            "admins-can-drain-or-migrate-by-node",
            "island-3-and-island-4-can-be-added-later"
    );

    private static final Map<String, List<String>> EXPECTED_SCENARIOS = buildExpectedScenarios();

    private PortableIslandDesignPolicy() {
    }

    public static List<String> coreDecisions() {
        return CORE_DECISIONS;
    }

    public static List<String> requiredOutcomes() {
        return REQUIRED_OUTCOMES;
    }

    public static boolean requiredOutcome(String outcome) {
        return REQUIRED_OUTCOMES.contains(outcome);
    }

    public static Map<String, List<String>> expectedScenarios() {
        return EXPECTED_SCENARIOS;
    }

    public static List<String> expectedScenario(String key) {
        return key == null ? List.of() : EXPECTED_SCENARIOS.getOrDefault(key, List.of());
    }

    public static boolean knownScenario(String key) {
        return key != null && EXPECTED_SCENARIOS.containsKey(key);
    }

    public static String scenarioSummary(String key) {
        List<String> scenario = expectedScenario(key);
        return scenario.isEmpty() ? "" : String.join(">", scenario);
    }

    private static Map<String, List<String>> buildExpectedScenarios() {
        LinkedHashMap<String, List<String>> scenarios = new LinkedHashMap<>();
        scenarios.put("a-server-to-b-server-move", List.of(
                "island-active-on-a-server",
                "a-server-drain-or-overload-detected",
                "save-portable-bundle-with-manifest-and-checksum",
                "b-server-claims-runtime",
                "restore-bundle-on-b-server",
                "route-ticket-points-player-to-b-server",
                "player-sees-same-island-without-node-knowledge"
        ));
        scenarios.put("island-1-soft-full-create-on-island-2", List.of(
                "island-1-reaches-soft-player-or-active-island-cap",
                "new-island-create-skips-island-1",
                "allocator-selects-island-2",
                "db-keeps-global-island-record",
                "velocity-connects-player-to-island-2"
        ));
        scenarios.put("add-island-5-and-6", List.of(
                "new-nodes-register-heartbeat",
                "allocator-includes-ready-nodes",
                "existing-islands-remain-global-resources",
                "new-or-inactive-islands-can-open-on-new-nodes",
                "players-do-not-change-commands"
        ));
        scenarios.put("addon-disabled-or-removed", List.of(
                "core-island-create-home-visit-still-work",
                "addon-commands-gui-listeners-tasks-and-writes-stop",
                "addon-state-remains-in-shared-storage",
                "reenable-restores-addon-state-by-island-uuid"
        ));
        return Collections.unmodifiableMap(scenarios);
    }
}
