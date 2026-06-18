package kr.lunaf.cloudislands.common.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinalRequestFlowPolicy {
    public static final String PLAYER_TOPOLOGY_POLICY = "player-facing-route-flow-uses-logical-island-names-and-hides-physical-node-names";
    public static final String NODE_SCALE_OUT_POLICY = "island-node-pool-can-grow-without-player-command-or-ui-change";
    private static final Map<String, List<String>> FLOWS = buildFlows();

    private FinalRequestFlowPolicy() {
    }

    public static Map<String, List<String>> flows() {
        return FLOWS;
    }

    public static List<String> flow(String key) {
        return key == null ? List.of() : FLOWS.getOrDefault(key, List.of());
    }

    public static boolean knownFlow(String key) {
        return key != null && FLOWS.containsKey(key);
    }

    public static String flowKeys() {
        return String.join(",", FLOWS.keySet());
    }

    public static String flowSummary(String key) {
        List<String> flow = flow(key);
        return flow.isEmpty() ? "" : String.join(">", flow);
    }

    private static Map<String, List<String>> buildFlows() {
        LinkedHashMap<String, List<String>> flows = new LinkedHashMap<>();
        flows.put("island-create", List.of(
            "player",
            "velocity-command-create",
            "logical-island-command-no-node-name",
            "core-api-create-island",
            "db-transaction-and-lock",
            "node-allocator",
            "create-island-job",
            "island-agent-claim",
            "template-restore",
            "cell-allocate",
            "runtime-active",
            "route-ticket-ready",
            "velocity-connect-target-node",
            "player-message-hides-physical-node",
            "paper-consume-ticket",
            "teleport-island-spawn"
        ));
        flows.put("island-home", List.of(
            "player",
            "velocity-command-home",
            "logical-island-home-no-node-name",
            "core-api-create-home-route",
            "island-runtime-check",
            "active-runtime-uses-active-node",
            "inactive-runtime-activates-on-best-node",
            "route-ticket-ready",
            "velocity-connect",
            "player-message-hides-physical-node",
            "paper-teleport"
        ));
        flows.put("island-visit", List.of(
            "player",
            "velocity-command-visit-player",
            "logical-target-island-no-node-name",
            "target-island-lookup",
            "public-ban-permission-check",
            "active-or-activate",
            "visitor-ticket-create",
            "connect",
            "player-message-hides-physical-node",
            "visitor-spawn-teleport"
        ));
        flows.put("soft-full-routing", List.of(
            "soft-full-node-avoided-for-new-islands",
            "allocator-block-reason-state-soft-full",
            "ready-node-selected-for-new-islands",
            "ready-node-selected-for-inactive-existing-islands",
            "active-island-owner-member-use-reserved-slots-on-current-node",
            "active-island-visitor-queued-or-limited",
            "empty-active-island-may-migrate-after-save"
        ));
        flows.put("island-1-soft-full-create-on-island-2", List.of(
            "player-runs-island-create",
            "island-1-heartbeat-reports-soft-full",
            "allocator-marks-island-1-state-soft-full-for-new-activation",
            "allocator-keeps-searching-ready-candidates",
            "island-2-ready-candidate-selected",
            "create-island-job-targets-island-2",
            "route-ticket-targets-island-2",
            "player-sees-logical-island-not-island-2",
            "player-command-does-not-change"
        ));
        flows.put("scale-to-five-or-six-island-nodes", List.of(
            "island-5-and-island-6-register-heartbeats",
            "node-pool-adds-ready-candidates",
            "allocator-considers-all-ready-nodes",
            "existing-islands-remain-global-resources",
            "new-and-inactive-islands-can-activate-on-new-nodes",
            "players-keep-using-create-home-visit-commands",
            "player-facing-output-hides-island-5-and-island-6"
        ));
        return Collections.unmodifiableMap(flows);
    }
}
