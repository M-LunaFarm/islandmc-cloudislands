package kr.lunaf.cloudislands.common.cache;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisKeysTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Test
    void keepsServerPlayerAndIslandCacheKeysStable() {
        assertEquals("ci:server:island-2:heartbeat", RedisKeys.serverHeartbeat("island-2"));
        assertEquals("ci:server:island-2:state", RedisKeys.serverState("island-2"));
        assertEquals("ci:server:island-2:metrics", RedisKeys.serverMetrics("island-2"));

        assertEquals("ci:player:" + PLAYER + ":island", RedisKeys.playerIsland(PLAYER));
        assertEquals("ci:player:" + PLAYER + ":route-ticket", RedisKeys.playerRouteTicket(PLAYER));
        assertEquals("ci:player:" + PLAYER + ":session", RedisKeys.playerSession(PLAYER));

        assertEquals("ci:island:" + ISLAND + ":summary", RedisKeys.islandSummary(ISLAND));
        assertEquals("ci:island:" + ISLAND + ":runtime", RedisKeys.islandRuntime(ISLAND));
        assertEquals("ci:island:" + ISLAND + ":members", RedisKeys.islandMembers(ISLAND));
        assertEquals("ci:island:" + ISLAND + ":flags", RedisKeys.islandFlags(ISLAND));
        assertEquals("ci:island:" + ISLAND + ":permissions", RedisKeys.islandPermissions(ISLAND));
        assertEquals("ci:island:" + ISLAND + ":warps", RedisKeys.islandWarps(ISLAND));
    }

    @Test
    void keepsLockStreamAndRouteTicketKeysStable() {
        assertEquals("ci:lock:player-create:" + PLAYER, RedisKeys.playerCreateLock(PLAYER));
        assertEquals("ci:lock:island:" + ISLAND, RedisKeys.islandLock(ISLAND));
        assertEquals("ci:lock:activation:" + ISLAND, RedisKeys.activationLock(ISLAND));

        assertEquals("ci:route-ticket:" + TICKET, RedisKeys.routeTicket(TICKET));
        assertEquals("ci:island:" + ISLAND + ":route-tickets", RedisKeys.islandRouteTickets(ISLAND));

        assertEquals("ci:stream:jobs", RedisKeys.jobsStream());
        assertEquals("ci:stream:events", RedisKeys.eventsStream());
        assertEquals("ci:stream:audit", RedisKeys.auditStream());
    }
}
