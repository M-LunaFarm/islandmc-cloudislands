package kr.lunaf.cloudislands.common.cache;

import java.util.UUID;

public final class RedisKeys {
    private RedisKeys() {}

    public static String serverHeartbeat(String nodeId) { return "ci:server:" + nodeId + ":heartbeat"; }
    public static String serverState(String nodeId) { return "ci:server:" + nodeId + ":state"; }
    public static String playerIsland(UUID uuid) { return "ci:player:" + uuid + ":island"; }
    public static String playerRouteTicket(UUID uuid) { return "ci:player:" + uuid + ":route-ticket"; }
    public static String playerRouteSession(UUID uuid) { return "ci:player:" + uuid + ":route-session"; }
    public static String islandSummary(UUID islandId) { return "ci:island:" + islandId + ":summary"; }
    public static String islandRuntime(UUID islandId) { return "ci:island:" + islandId + ":runtime"; }
    public static String islandMembers(UUID islandId) { return "ci:island:" + islandId + ":members"; }
    public static String islandFlags(UUID islandId) { return "ci:island:" + islandId + ":flags"; }
    public static String activationLock(UUID islandId) { return "ci:lock:activation:" + islandId; }
    public static String jobsStream() { return "ci:stream:jobs"; }
    public static String eventsStream() { return "ci:stream:events"; }
    public static String auditStream() { return "ci:stream:audit"; }
}
