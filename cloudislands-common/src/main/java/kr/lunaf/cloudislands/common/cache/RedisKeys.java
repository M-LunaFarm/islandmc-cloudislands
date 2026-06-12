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
    public static String islandBans(UUID islandId) { return "ci:island:" + islandId + ":bans"; }
    public static String islandPermissions(UUID islandId) { return "ci:island:" + islandId + ":permissions"; }
    public static String islandFlags(UUID islandId) { return "ci:island:" + islandId + ":flags"; }
    public static String islandHomes(UUID islandId) { return "ci:island:" + islandId + ":homes"; }
    public static String islandWarps(UUID islandId) { return "ci:island:" + islandId + ":warps"; }
    public static String islandBank(UUID islandId) { return "ci:island:" + islandId + ":bank"; }
    public static String islandLimits(UUID islandId) { return "ci:island:" + islandId + ":limits"; }
    public static String islandMissions(UUID islandId, String kind) { return "ci:island:" + islandId + ":missions:" + kind; }
    public static String islandUpgrades(UUID islandId) { return "ci:island:" + islandId + ":upgrades"; }
    public static String islandSnapshots(UUID islandId) { return "ci:island:" + islandId + ":snapshots"; }
    public static String islandLogs(UUID islandId) { return "ci:island:" + islandId + ":logs"; }
    public static String templates() { return "ci:templates"; }
    public static String rankingVersion() { return "ci:rankings:version"; }
    public static String rankingTop(String metric, int limit, long version) { return "ci:rankings:" + metric + ":" + limit + ":v" + version; }
    public static String activationLock(UUID islandId) { return "ci:lock:activation:" + islandId; }
    public static String jobsStream() { return "ci:stream:jobs"; }
    public static String eventsStream() { return "ci:stream:events"; }
    public static String auditStream() { return "ci:stream:audit"; }
}
