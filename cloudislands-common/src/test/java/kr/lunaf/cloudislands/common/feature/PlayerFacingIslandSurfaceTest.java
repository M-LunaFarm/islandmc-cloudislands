package kr.lunaf.cloudislands.common.feature;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFacingIslandSurfaceTest {
    @Test
    void stripsTopologyKeysFromPlayerFacingPayloads() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("surface", "my-island");
        payload.put("island-name", "Luna");
        payload.put("active-node", "island-2");
        payload.put("server_name", "Island-2");
        payload.put("world.name", "ci_island_shard_03");
        payload.put("cell-x", "12");
        payload.put("routeTicket", "secret-ticket");
        payload.put("route-ticket-id", "secret-ticket-id");

        Map<String, String> sanitized = PlayerFacingIslandSurface.sanitize(payload);

        assertEquals(Map.of(
                "surface", "my-island",
                "island-name", "Luna"
        ), sanitized);
    }

    @Test
    void exposesOnlyLogicalPlayerSurfaces() {
        assertEquals(
                List.of("my-island", "other-island", "island-ranking", "island-visit", "island-settings", "island-warps"),
                PlayerFacingIslandSurface.logicalSurfaces()
        );
        assertTrue(PlayerFacingIslandSurface.isLogicalSurface("my-island"));
        assertTrue(PlayerFacingIslandSurface.isLogicalSurface("island-warps"));
        assertFalse(PlayerFacingIslandSurface.isLogicalSurface("island-1"));
        assertFalse(PlayerFacingIslandSurface.isLogicalSurface("active-node"));
    }

    @Test
    void matchesCommonTopologyAliases() {
        assertTrue(PlayerFacingIslandSurface.isHiddenTopologyKey("runtime-active-node"));
        assertTrue(PlayerFacingIslandSurface.isHiddenTopologyKey("serverName"));
        assertTrue(PlayerFacingIslandSurface.isHiddenTopologyKey("active_world"));
        assertTrue(PlayerFacingIslandSurface.isHiddenTopologyKey("route-ticket"));
        assertFalse(PlayerFacingIslandSurface.isHiddenTopologyKey("island-name"));
        assertFalse(PlayerFacingIslandSurface.isHiddenTopologyKey("member-count"));
    }
}
