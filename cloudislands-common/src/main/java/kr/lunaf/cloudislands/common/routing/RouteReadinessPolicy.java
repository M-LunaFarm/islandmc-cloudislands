package kr.lunaf.cloudislands.common.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteReadinessPolicy {
    public static final String READY_GATE_POLICY = "route-ticket-ready-requires-runtime-protection-member-warp-cache-and-spawn-chunk-preload";
    public static final String PLAYER_WAIT_MESSAGE = "섬을 준비하는 중입니다...";

    private static final List<String> REQUIRED_GATES = List.of(
            "runtime-active-or-restored",
            "protection-cache-ready",
            "member-cache-ready",
            "warp-cache-ready",
            "spawn-chunk-preloaded",
            "route-session-publishable"
    );

    private RouteReadinessPolicy() {
    }

    public static List<String> requiredGates() {
        return REQUIRED_GATES;
    }

    public static String requiredGateSummary() {
        return String.join(">", REQUIRED_GATES);
    }

    public static boolean ready(Map<String, Boolean> gates) {
        return firstMissingGate(gates).isBlank();
    }

    public static String firstMissingGate(Map<String, Boolean> gates) {
        Map<String, Boolean> safeGates = gates == null ? Map.of() : gates;
        for (String gate : REQUIRED_GATES) {
            if (!Boolean.TRUE.equals(safeGates.get(gate))) {
                return gate;
            }
        }
        return "";
    }

    public static String ticketState(Map<String, Boolean> gates) {
        return ready(gates) ? "READY" : "PREPARING";
    }

    public static Map<String, Boolean> gateMap(
            boolean runtimeActiveOrRestored,
            boolean protectionCacheReady,
            boolean memberCacheReady,
            boolean warpCacheReady,
            boolean spawnChunkPreloaded,
            boolean routeSessionPublishable
    ) {
        Map<String, Boolean> gates = new LinkedHashMap<>();
        gates.put("runtime-active-or-restored", runtimeActiveOrRestored);
        gates.put("protection-cache-ready", protectionCacheReady);
        gates.put("member-cache-ready", memberCacheReady);
        gates.put("warp-cache-ready", warpCacheReady);
        gates.put("spawn-chunk-preloaded", spawnChunkPreloaded);
        gates.put("route-session-publishable", routeSessionPublishable);
        return Map.copyOf(gates);
    }
}
