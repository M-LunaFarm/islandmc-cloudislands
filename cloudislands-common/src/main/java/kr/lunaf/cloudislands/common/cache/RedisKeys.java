package kr.lunaf.cloudislands.common.cache;

import java.util.UUID;

public final class RedisKeys {
    private RedisKeys() {}

    public static String serverHeartbeat(String nodeId) { return "ci:server:" + nodeId + ":heartbeat"; }
    public static String serverState(String nodeId) { return "ci:server:" + nodeId + ":state"; }
    public static String serverMetrics(String nodeId) { return "ci:server:" + nodeId + ":metrics"; }
    public static String nodeIslandRuntimes(String nodeId, int limit) { return "ci:node:" + nodeId + ":island-runtimes:" + limit; }
    public static String playerIsland(UUID uuid) { return "ci:player:" + uuid + ":island"; }
    public static String playerProfile(UUID uuid) { return "ci:player:" + uuid + ":profile"; }
    public static String playerNameProfile(String name) { return "ci:player-name:" + name.toLowerCase() + ":profile"; }
    public static String playerRouteTicket(UUID uuid) { return "ci:player:" + uuid + ":route-ticket"; }
    public static String routeTicket(UUID ticketId) { return "ci:route-ticket:" + ticketId; }
    public static String routeTicketCounts() { return "ci:route-ticket-counts"; }
    public static String playerRouteSession(UUID uuid) { return "ci:player:" + uuid + ":route-session"; }
    public static String islandSummary(UUID islandId) { return "ci:island:" + islandId + ":summary"; }
    public static String islandRuntime(UUID islandId) { return "ci:island:" + islandId + ":runtime"; }
    public static String islandRuntimeCounts() { return "ci:island-runtime-counts"; }
    public static String islandMembers(UUID islandId) { return "ci:island:" + islandId + ":members"; }
    public static String islandBans(UUID islandId) { return "ci:island:" + islandId + ":bans"; }
    public static String islandRoles(UUID islandId) { return "ci:island:" + islandId + ":roles"; }
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
    public static String islandBlockCounts(UUID islandId) { return "ci:island:" + islandId + ":block-counts"; }
    public static String blockValues() { return "ci:block-values"; }
    public static String templates() { return "ci:templates"; }
    public static String rankingVersion() { return "ci:rankings:version"; }
    public static String rankingTop(String metric, int limit, long version) { return "ci:rankings:" + metric + ":" + limit + ":v" + version; }
    public static String islandLock(UUID islandId) { return "ci:lock:island:" + islandId; }
    public static String activationLock(UUID islandId) { return "ci:lock:activation:" + islandId; }
    public static String playerCreateLock(UUID playerUuid) { return "ci:lock:player-create:" + playerUuid; }
    public static String jobsStream() { return "ci:stream:jobs"; }
    public static String eventsStream() { return "ci:stream:events"; }
    public static String auditStream() { return "ci:stream:audit"; }
}
