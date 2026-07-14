package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerFlightPreferenceRegistryTest {
    @Test
    void keepsPersistentPreferenceSeparateFromLocalFlightOwnership() {
        PlayerFlightPreferenceRegistry registry = new PlayerFlightPreferenceRegistry();
        UUID playerUuid = UUID.randomUUID();

        registry.remember(playerUuid, true);
        assertTrue(registry.known(playerUuid));
        assertTrue(registry.enabled(playerUuid));
        assertFalse(registry.managed(playerUuid));
        assertTrue(registry.beginUpdate(playerUuid));
        assertFalse(registry.beginUpdate(playerUuid));
        registry.finishUpdate(playerUuid);
        assertTrue(registry.beginUpdate(playerUuid));

        registry.markManaged(playerUuid);
        assertTrue(registry.managed(playerUuid));
        registry.clearManaged(playerUuid);
        assertTrue(registry.enabled(playerUuid));
        assertFalse(registry.managed(playerUuid));

        registry.forget(playerUuid);
        assertFalse(registry.enabled(playerUuid));
        assertFalse(registry.known(playerUuid));
        assertTrue(registry.beginUpdate(playerUuid));
    }
}
