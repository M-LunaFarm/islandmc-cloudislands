package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamChatModeRegistryTest {
    @Test
    void togglesSetsAndClearsPlayerModes() {
        TeamChatModeRegistry registry = new TeamChatModeRegistry();
        UUID player = UUID.randomUUID();

        assertFalse(registry.enabled(player));
        assertTrue(registry.toggle(player));
        assertTrue(registry.enabled(player));
        assertFalse(registry.toggle(player));
        assertFalse(registry.enabled(player));
        assertTrue(registry.set(player, true));
        registry.clear(player);
        assertFalse(registry.enabled(player));
    }
}
