package kr.lunaf.cloudislands.protocol.route;

import java.util.List;

public final class PlayerRouteMessagePolicy {
    public static final String CONTRACT = "player-route-messages-hide-physical-node-world-cell-and-raw-targets";
    public static final String FALLBACK_MESSAGE = "Island routing request could not be completed.";
    public static final String HIDDEN_LABEL = "hidden";
    public static final String PUBLIC_TARGET_LABEL = RouteTicketPolicy.PUBLIC_TARGET_LABEL;
    private static final List<String> TOPOLOGY_ASSIGNMENT_KEYS = List.of(
        "targetNode",
        "requestedNode",
        "activeNode",
        "node",
        "targetServerName",
        "server",
        "targetWorld",
        "activeWorld",
        "world",
        "shardWorld",
        "cell",
        "cellX",
        "cellZ"
    );

    private PlayerRouteMessagePolicy() {
    }

    public static String sanitize(String message) {
        String value = message == null || message.isBlank() ? FALLBACK_MESSAGE : message;
        for (String key : TOPOLOGY_ASSIGNMENT_KEYS) {
            value = value.replaceAll("(?i)\\b" + key + "\\s*[=:]\\s*[^\\s,|}]+", key + "=" + HIDDEN_LABEL);
        }
        return value
            .replaceAll("(?i)\\bcell\\s+-?\\d+\\s*,\\s*-?\\d+\\b", "cell " + HIDDEN_LABEL)
            .replaceAll("(?i)\\b[A-Za-z0-9_.-]*ci[-_ ]?shard[-_ ]?\\d+[A-Za-z0-9_.-]*\\b", PUBLIC_TARGET_LABEL)
            .replaceAll("(?i)\\b[A-Za-z0-9_.-]*island[-_ ]?\\d+[A-Za-z0-9_.-]*\\b", PUBLIC_TARGET_LABEL)
            .replaceAll("(?i)\\b[A-Za-z0-9_.-]*node[-_ ]?\\d+[A-Za-z0-9_.-]*\\b", PUBLIC_TARGET_LABEL);
    }

    public static boolean containsPhysicalTopology(String message) {
        String sanitized = sanitize(message);
        return !sanitized.equals(message == null || message.isBlank() ? FALLBACK_MESSAGE : message);
    }
}
