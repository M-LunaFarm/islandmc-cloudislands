package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamChatModeRegistryTest {
    @Test
    void togglesSetsAndClearsPlayerModes() {
        TeamChatModeRegistry registry = new TeamChatModeRegistry();
        UUID player = UUID.randomUUID();

        assertFalse(registry.enabled(player));
        assertEquals(TeamChatModeRegistry.Mode.GLOBAL, registry.mode(player));
        assertTrue(registry.toggle(player));
        assertTrue(registry.enabled(player));
        assertEquals(TeamChatModeRegistry.Mode.TEAM, registry.mode(player));
        assertTrue(registry.toggleIsland(player));
        assertTrue(registry.islandEnabled(player));
        assertFalse(registry.enabled(player));
        assertEquals(TeamChatModeRegistry.Mode.ISLAND, registry.mode(player));
        assertFalse(registry.toggleIsland(player));
        assertEquals(TeamChatModeRegistry.Mode.GLOBAL, registry.mode(player));
        assertTrue(registry.setIsland(player, true));
        assertTrue(registry.islandEnabled(player));
        assertFalse(registry.set(player, false));
        assertTrue(registry.islandEnabled(player));
        assertTrue(registry.toggle(player));
        assertTrue(registry.enabled(player));
        assertFalse(registry.islandEnabled(player));
        assertFalse(registry.toggle(player));
        assertFalse(registry.enabled(player));
        assertTrue(registry.set(player, true));
        registry.clear(player);
        assertFalse(registry.enabled(player));
    }
}
